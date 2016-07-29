package ods.string.search.partition;

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

import ods.string.search.partition.splitsets.ExternalizableMemoryObject;

import org.apache.commons.codec.binary.Hex;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

public class ExternalMemoryObjectCache<T extends ExternalizableMemoryObject>
{
	/**
	 * 64 bytes per LinkedHashMap.Entry + 32 bytes for Block value + 64 bytes for String key
	 * minimum.
	 */
	private static final long BASE_CACHED_BLOCK_BYTE_SIZE = 160;

	private class Block
	{
		public long previousByteSize = 0;
		public T data;

		public Block(T data)
		{
			this.data = data;
		}

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

	private long inMemoryByteEstimate = 0;
	private long maxCacheMemorySize;
	private boolean compress;
	private File storageDirectory;
	private LinkedHashMap<String, Block> cachedBlocks = new LinkedHashMap<String, Block>(16, 0.75f,
			true);
	private long uncompressBytes = 0;
	private long compressedBytes = 0;
	private long serializationTime = 0;
	private long diskWriteTime = 0;
	private long diskReadTime = 0;
	private MessageDigest md5Hash;

	public ExternalMemoryObjectCache(File directory)
	{
		init(directory, 1000000000, true);
	}

	public ExternalMemoryObjectCache(File directory, long cacheSize, boolean compress)
	{
		init(directory, cacheSize, compress);
	}

	public ExternalMemoryObjectCache(File directory, ExternalMemoryObjectCache<T> baseCacheConfig)
	{
		init(directory, baseCacheConfig.maxCacheMemorySize, baseCacheConfig.compress);
	}

	private void init(File directory, long cacheSize, boolean compress)
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

	public void register(String index, T data)
	{
		Block block = getBlock(index);
		block.data = data;
		block.updateSizeEstimate();
	}

	public T get(String index)
	{
		Block block = getBlock(index);
		block.updateSizeEstimate();
		return block.data;
	}

	public void unregister(String index)
	{
		Block block = cachedBlocks.remove(index);
		inMemoryByteEstimate -= block.previousByteSize;
		String blockFileName = convertBlockIdToFilename(index);
		new File(storageDirectory, blockFileName).delete();
	}

	@SuppressWarnings("unchecked")
	private Block getBlock(String blockId)
	{
		Block block = cachedBlocks.get(blockId);
		try
		{
			if (block == null)
			{
				block = new Block(null);
				String blockFileName = convertBlockIdToFilename(blockId);
				File blockFile = new File(storageDirectory, blockFileName);
				if (blockFile.exists())
				{
					long startTime = System.currentTimeMillis();
					InputStream is = new FileInputStream(blockFile);
					if (compress)
						is = new SnappyInputStream(is);
					ObjectInputStream objStream = new ObjectInputStream(is);
					block.data = (T) objStream.readObject();
					objStream.close();
					diskReadTime += System.currentTimeMillis() - startTime;
					block.updateSizeEstimate();
				}

				cachedBlocks.put(blockId, block);
			}

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
		if (flushBlock.data.isDirty())
		{
			try
			{
				long startTime = System.currentTimeMillis();
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				OutputStream os = bytes;
				if (compress)
					os = new SnappyOutputStream(os);
				ObjectOutputStream out = new ObjectOutputStream(os);
				out.writeObject(flushBlock.data);
				out.close();
				serializationTime += System.currentTimeMillis() - startTime;
				startTime = System.currentTimeMillis();
				String blockFileName = convertBlockIdToFilename(block);
				OutputStream fileOutput = new FileOutputStream(new File(storageDirectory,
						blockFileName));
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

	private String convertBlockIdToFilename(String blockId)
	{
		return Hex.encodeHexString(md5Hash.digest(blockId.getBytes()));
	}

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
