package ods.string.search.partition;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

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
			if (data == null)
			{
				System.out.println("wtf");
			}
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

	public ExternalMemoryObjectCache(File directory)
	{
		init(directory, 1000000000, true);
	}

	public ExternalMemoryObjectCache(File directory, long cacheSize, boolean compress)
	{
		init(directory, cacheSize, compress);
	}

	private void init(File directory, long cacheSize, boolean compress)
	{
		storageDirectory = directory;
		storageDirectory.mkdirs();
		this.maxCacheMemorySize = cacheSize;
		this.compress = compress;
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
		new File(storageDirectory, index).delete();
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
				File blockFile = new File(storageDirectory, blockId);
				if (blockFile.exists())
				{
					InputStream is = new FileInputStream(blockFile);
					if (compress)
						is = new SnappyInputStream(is);
					ObjectInputStream objStream = new ObjectInputStream(is);
					block.data = (T) objStream.readObject();
					objStream.close();
					block.updateSizeEstimate();
				}

				cachedBlocks.put(blockId, block);
			}

			if (inMemoryByteEstimate > maxCacheMemorySize)
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
				OutputStream os = new FileOutputStream(new File(storageDirectory, block));
				if (compress)
					os = new SnappyOutputStream(os);
				ObjectOutputStream out = new ObjectOutputStream(os);
				out.writeObject(flushBlock.data);
				out.close();
			} catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		}
		inMemoryByteEstimate -= flushBlock.previousByteSize;
	}

	public void close()
	{
		for (String block : new HashSet<String>(cachedBlocks.keySet()))
		{
			flushBlock(block);
		}
	}
}
