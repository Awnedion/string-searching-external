package ods.string.search;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ExternalMemoryCache<T extends ExternalMemoryNode>
{
	private class Block
	{
		public boolean dirty = false;
		public ByteBuffer data;

		public Block(ByteBuffer data)
		{
			this.data = data;
		}
	}

	private long cacheSize = 20000000l;
	private int blockSize = 1000000;
	private boolean compress;
	private File storageDirectory;
	private LinkedHashMap<Long, Block> cachedBlocks = new LinkedHashMap<Long, Block>(16, 0.75f,
			true);

	public ExternalMemoryCache(File directory, long cacheSize)
	{
		init(directory, cacheSize, 100000, true);
	}

	public ExternalMemoryCache(File directory, long cacheSize, int blockSize, boolean compress)
	{
		init(directory, cacheSize, blockSize, compress);
	}

	private void init(File directory, long cacheSize, int blockSize, boolean compress)
	{
		storageDirectory = directory;
		storageDirectory.mkdirs();
		this.cacheSize = cacheSize;
		this.blockSize = blockSize;
		this.compress = compress;
	}

	public void set(long index, T data)
	{
		if (index < 0)
			throw new IllegalArgumentException("Array index must be 0 or higher. Index=" + index);

		int indicesPerBlock = blockSize / data.byteSize();
		long block = index / indicesPerBlock;
		Block blockBytes = getBlock(block);
		long blockStartIndex = block * indicesPerBlock;
		blockBytes.dirty = true;
		data.writeBytes(blockBytes.data, (int) ((index - blockStartIndex) * data.byteSize()));
	}

	public void get(long index, T result)
	{
		if (index < 0)
			throw new IllegalArgumentException("Array index must be 0 or higher. Index=" + index);

		int indicesPerBlock = blockSize / result.byteSize();
		long block = index / indicesPerBlock;
		Block blockBytes = getBlock(block);
		long blockStartIndex = block * indicesPerBlock;
		result.setFromBytes(blockBytes.data, (int) ((index - blockStartIndex) * result.byteSize()));
	}

	private Block getBlock(long block)
	{
		Block blockBytes = cachedBlocks.get(block);
		try
		{
			if (blockBytes == null)
			{
				blockBytes = new Block(ByteBuffer.allocate(blockSize));
				File blockFile = new File(storageDirectory, (block >> 15) + "/" + block + "");
				if (blockFile.exists())
				{
					InputStream is = new FileInputStream(blockFile);
					if (compress)
						is = new GZIPInputStream(is);
					int bytesRemaining = blockSize;
					while (bytesRemaining > 0)
					{
						int bytesRead = is.read(blockBytes.data.array(),
								blockSize - bytesRemaining, bytesRemaining);
						bytesRemaining -= bytesRead;
					}
					is.close();
				}
				cachedBlocks.put(block, blockBytes);

				if (cachedBlocks.size() * blockSize > cacheSize)
				{
					Iterator<Entry<Long, Block>> iter = cachedBlocks.entrySet().iterator();
					long flushBlockNum = iter.next().getKey();
					flushBlock(flushBlockNum);
				}
			}
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		return blockBytes;
	}

	private void flushBlock(long block)
	{
		Block flushBlock = cachedBlocks.remove(block);
		if (flushBlock.dirty)
		{
			try
			{
				File blockDir = new File(storageDirectory, (block >> 15) + "");
				blockDir.mkdirs();
				OutputStream os = new FileOutputStream(new File(blockDir, block + ""));
				if (compress)
					os = new GZIPOutputStream(os);
				os.write(flushBlock.data.array());
				os.close();
			} catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	public void close()
	{
		for (long block : new HashSet<Long>(cachedBlocks.keySet()))
		{
			flushBlock(block);
		}
	}
}
