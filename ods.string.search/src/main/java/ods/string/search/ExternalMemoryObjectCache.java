package ods.string.search;

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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ExternalMemoryObjectCache<T extends ExternalizableMemoryObject>
{
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
			previousByteSize = data.getByteSize();
			inMemoryByteEstimate += previousByteSize;
		}
	}

	private long inMemoryByteEstimate = 0;
	private long maxCacheMemorySize;
	private boolean compress;
	private File storageDirectory;
	private LinkedHashMap<String, Block> cachedBlocks = new LinkedHashMap<String, Block>(16, 0.75f,
			true);

	public ExternalMemoryObjectCache(File directory, long maxCacheMemorySize)
	{
		init(directory, maxCacheMemorySize, true);
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
						is = new GZIPInputStream(is);
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
					os = new GZIPOutputStream(os);
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
