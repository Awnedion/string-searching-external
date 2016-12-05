package ods.string.search.partition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import ods.string.search.partition.splitsets.ExternalizableMemoryObject;

import org.apache.commons.codec.binary.Hex;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

/**
 * This class caches ExternalizableMemoryObjects to disk when a specified byte limit in RAM is
 * reached. Disk data is auto-loaded into cache when needed. Data is cached onto disk in a
 * least-recently used fashion. Data that is cached to disk can optionally make use of compression.
 */
public class ExternalMemoryObjectCache<T extends ExternalizableMemoryObject>
{
	/**
	 * 64 bytes per LinkedHashMap.Entry + 32 bytes for Block value + 64 bytes for String key
	 * minimum.
	 */
	private static final long BASE_CACHED_BLOCK_BYTE_SIZE = 160;

	/**
	 * A block is a ExternalizableMemoryObject that keeps track of its in-memory size.
	 */
	private class Block
	{
		/**
		 * The last known size of the ExternalizableMemoryObject stored in this block. The size is
		 * stored to reduce re-computation work and to keep track of how much data the cache is
		 * using in total.
		 */
		public long previousByteSize = 0;
		public T data;

		public Block(T data)
		{
			this.data = data;
		}

		/**
		 * Updates this block's size and the total memory size of the entire cache.
		 */
		public void updateSizeEstimate()
		{
			inMemoryByteEstimate -= previousByteSize;
			previousByteSize = data.getByteSize() + BASE_CACHED_BLOCK_BYTE_SIZE;
			inMemoryByteEstimate += previousByteSize;
		}

		public String toString()
		{
			return data + "";
		}
	}

	public enum CompressType
	{
		NONE, GZIP, SNAPPY
	}

	/**
	 * The currently RAM usage, in bytes, of this cache.
	 */
	private long inMemoryByteEstimate = 0;

	/**
	 * The maximum number of bytes that this cache can store in RAM before flushing data to disk.
	 */
	private long maxCacheMemorySize;

	/**
	 * The type of compress to use when flushing data to disk.
	 */
	private CompressType compress;

	/**
	 * The directory to store data in.
	 */
	private File storageDirectory;

	/**
	 * A map of block ID to block data. The map also has a linked list using the access order of the
	 * blocks for its element ordering.
	 */
	private LinkedHashMap<String, Block> cachedBlocks = new LinkedHashMap<String, Block>(16, 0.75f,
			true);

	/**
	 * The total number of bytes in RAM that was flushed to disk.
	 */
	private long uncompressBytes = 0;

	/**
	 * The total number of bytes that was stored onto disk after compression.
	 */
	private long compressedBytes = 0;

	/**
	 * The total time in ms that has been spent performing serialization.
	 */
	private long serializationTime = 0;

	/**
	 * The total time in ms that has been spent writing data to disk.
	 */
	private long diskWriteTime = 0;

	/**
	 * The total time in ms that has been spent reading data from disk.
	 */
	private long diskReadTime = 0;

	/**
	 * The hashing algorithm to use when computing file name from block ID.
	 */
	private MessageDigest md5Hash;

	public ExternalMemoryObjectCache(File directory)
	{
		init(directory, 1000000000, CompressType.SNAPPY);
	}

	public ExternalMemoryObjectCache(File directory, long cacheSize, CompressType compress)
	{
		init(directory, cacheSize, compress);
	}

	public ExternalMemoryObjectCache(File directory, ExternalMemoryObjectCache<T> baseCacheConfig)
	{
		init(directory, baseCacheConfig.maxCacheMemorySize, baseCacheConfig.compress);
	}

	private void init(File directory, long cacheSize, CompressType compress)
	{
		storageDirectory = directory;
		storageDirectory.mkdirs();
		this.maxCacheMemorySize = cacheSize;
		this.compress = compress;
		try
		{
			md5Hash = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Adds a new ExternalizableMemoryObject to the cache with the specified ID.
	 * 
	 * @param index
	 *            The ID of the ExternalizableMemoryObject.
	 * @param data
	 *            The ExternalizableMemoryObject to add into the cache.
	 */
	public void register(String index, T data)
	{
		Block block = getBlock(index);
		block.data = data;
		block.updateSizeEstimate();
	}

	/**
	 * Returns the ExternalizableMemoryObject stored with the specified ID.
	 */
	public T get(String index)
	{
		Block block = getBlock(index);
		block.updateSizeEstimate();
		return block.data;
	}

	/**
	 * Removes the ExternalizableMemoryObject with the specified ID from the cache.
	 */
	public void unregister(String index)
	{
		Block block = cachedBlocks.remove(index);
		inMemoryByteEstimate -= block.previousByteSize;
		String blockFileName = convertBlockIdToFilename(index);
		new File(storageDirectory, blockFileName).delete();
	}

	/**
	 * Returnss the block stored with the specified ID from RAM if possible, otherwise the block
	 * will be loaded from disk into the cache first.
	 */
	@SuppressWarnings("unchecked")
	private Block getBlock(String blockId)
	{
		Block block = cachedBlocks.get(blockId);
		try
		{
			// The block is not in RAM so load it from disk.
			if (block == null)
			{
				block = new Block(null);
				String blockFileName = convertBlockIdToFilename(blockId);
				File blockFile = new File(storageDirectory, blockFileName);
				if (blockFile.exists())
				{
					long startTime = System.currentTimeMillis();
					InputStream is = new BufferedInputStream(new FileInputStream(blockFile));

					// Decompress if necessary.
					if (compress == CompressType.SNAPPY)
						is = new SnappyInputStream(is);
					else if (compress == CompressType.GZIP)
						is = new GZIPInputStream(is);

					ObjectInputStream objStream = new ObjectInputStream(is);
					block.data = (T) objStream.readObject();
					objStream.close();
					diskReadTime += System.currentTimeMillis() - startTime;
					block.updateSizeEstimate();
				}

				cachedBlocks.put(blockId, block);
			}

			/*
			 * If the new block made the cache too large, flush the least-recently used block to
			 * disk.
			 */
			if (inMemoryByteEstimate > maxCacheMemorySize && cachedBlocks.size() > 1)
			{
				Iterator<Entry<String, Block>> iter = cachedBlocks.entrySet().iterator();
				String flushBlockNum = iter.next().getKey();
				flushBlock(flushBlockNum);
			}
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		return block;
	}

	private void flushBlock(String block)
	{
		Block flushBlock = cachedBlocks.remove(block);

		/*
		 * If the block was modified since being loaded, the new data needs to be saved to disk.
		 */
		if (flushBlock.data.isDirty())
		{
			flushBlock.updateSizeEstimate();
			try
			{
				long startTime = System.currentTimeMillis();
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				OutputStream os = bytes;

				// Compress if necessary.
				if (compress == CompressType.SNAPPY)
					os = new SnappyOutputStream(os);
				else if (compress == CompressType.GZIP)
					os = new GZIPOutputStream(os);

				ObjectOutputStream out = new ObjectOutputStream(os);
				out.writeObject(flushBlock.data);
				out.close();
				serializationTime += System.currentTimeMillis() - startTime;
				startTime = System.currentTimeMillis();
				String blockFileName = convertBlockIdToFilename(block);
				OutputStream fileOutput = new BufferedOutputStream(new FileOutputStream(new File(
						storageDirectory, blockFileName)));
				fileOutput.write(bytes.toByteArray());
				fileOutput.close();
				diskWriteTime += System.currentTimeMillis() - startTime;
				uncompressBytes += flushBlock.previousByteSize;
				compressedBytes += bytes.size();
			} catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		}
		inMemoryByteEstimate -= flushBlock.previousByteSize;
	}

	/**
	 * Returns an MD5 hash of the specified block ID.
	 */
	private String convertBlockIdToFilename(String blockId)
	{
		return Hex.encodeHexString(md5Hash.digest(blockId.getBytes()));
	}

	/**
	 * Flushes all blocks still remaining in RAM.
	 */
	public void close()
	{
		for (String block : new HashSet<String>(cachedBlocks.keySet()))
		{
			flushBlock(block);
		}

		System.out.println("Compression Ratio: " + getCompressionRatio());
		System.out.println("Total Serialization Time: " + serializationTime + "ms");
		System.out.println("Total Disk Write Time: " + diskWriteTime + "ms");
		System.out.println("Total Disk Read Time: " + diskReadTime + "ms");
	}

	public double getCompressionRatio()
	{
		return (double) compressedBytes / uncompressBytes;
	}

	public File getStorageDirectory()
	{
		return storageDirectory;
	}

	public long getSerializationTime()
	{
		return serializationTime;
	}

	public long getDiskWriteTime()
	{
		return diskWriteTime;
	}
}
