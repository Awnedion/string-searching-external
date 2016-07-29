package ods.string.search.partition;

import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Stack;

import ods.string.search.partition.BinaryPatriciaTrie.SearchPoint;
import ods.string.search.partition.splitsets.ExternalizableMemoryObject;

import org.apache.commons.codec.binary.Hex;

public class ExternalMemoryTrie<T extends Comparable<T> & Serializable> implements
		EMPrefixSearchableSet<T>
{
	private ExternalMemoryObjectCache<BinaryPatriciaTrie<T>> trieCache;
	private int maxSetSize = 100000;
	private long size = 0;
	private int minPartitionDepth;

	public ExternalMemoryTrie(File storageDirectory)
	{
		trieCache = new ExternalMemoryObjectCache<BinaryPatriciaTrie<T>>(storageDirectory,
				100000000, true);
		BinaryPatriciaTrie<T> root = new BinaryPatriciaTrie<T>();
		trieCache.register("~", root);
		minPartitionDepth = 0;
	}

	public ExternalMemoryTrie(File storageDirectory, int maxSetSize, long maxInMemoryBytes,
			int minPartitionDepth)
	{
		this.maxSetSize = maxSetSize;
		trieCache = new ExternalMemoryObjectCache<BinaryPatriciaTrie<T>>(storageDirectory,
				maxInMemoryBytes, true);
		trieCache.register("~", new BinaryPatriciaTrie<T>());
		this.minPartitionDepth = minPartitionDepth;
	}

	public ExternalMemoryTrie(File storageDirectory, ExternalMemoryTrie<T> baseConfig)
	{
		trieCache = new ExternalMemoryObjectCache<BinaryPatriciaTrie<T>>(storageDirectory,
				baseConfig.trieCache);
		BinaryPatriciaTrie<T> root = (BinaryPatriciaTrie<T>) baseConfig.trieCache.get("~")
				.createNewSet();
		trieCache.register("~", root);
		maxSetSize = baseConfig.maxSetSize;
		minPartitionDepth = baseConfig.minPartitionDepth;
	}

	@Override
	public boolean add(T u)
	{
		BinaryPatriciaTrie<T> curTrie = trieCache.get("~");

		byte[] valueAsBytes = curTrie.convertToBytes(u);
		int result;
		while ((result = curTrie.add(valueAsBytes)) > 0)
			curTrie = trieCache.get(getTrieIdFromBytes(valueAsBytes, result));

		if (result == 0)
		{
			splitIfNecessary(curTrie);
		}

		if (result == 0)
			size++;

		return result == 0;
	}

	private void splitIfNecessary(BinaryPatriciaTrie<T> curTrie)
	{
		if (curTrie.r.subtreeSize > maxSetSize)
		{
			BinaryPatriciaTrie<T> newTrie = (BinaryPatriciaTrie<T>) curTrie.split(null,
					minPartitionDepth);
			trieCache.register(getTrieIdFromBytes(newTrie.r.label, newTrie.r.bitsUsed), newTrie);
		}
	}

	private String getTrieIdFromBytes(byte[] value, int bitsUsed)
	{
		int fullBytesUsed = bitsUsed / 8;
		int remainingBits = bitsUsed % 8;
		String result = Hex.encodeHexString(value).substring(0, fullBytesUsed * 2);
		if (remainingBits > 0)
		{
			result += "~";
			int mask = 0x80;
			for (int x = 0; x < remainingBits; x++)
			{
				if ((value[fullBytesUsed] & mask) == 0)
					result += "0";
				else
					result += "1";
				mask >>= 1;
			}
		}
		return result;
	}

	@Override
	public boolean remove(T x)
	{
		BinaryPatriciaTrie<T> curTrie = trieCache.get("~");
		byte[] valueAsBytes = curTrie.convertToBytes(x);
		int result;

		BinaryPatriciaTrie<T> parentTrie = null;
		String curTrieId = "~";
		while ((result = curTrie.remove(valueAsBytes)) > 0)
		{
			parentTrie = curTrie;
			curTrieId = getTrieIdFromBytes(valueAsBytes, result);
			curTrie = trieCache.get(curTrieId);
		}

		if (result == 0)
		{
			size--;

			/*
			 * Merge this trie with another if it is smaller than maxSetSize/8 or
			 * maxSetSize/(2^minPartitionDepth) if a min depth is being used.
			 */
			if ((curTrie.childTrieLabel != null || parentTrie != null)
					&& curTrie.r.subtreeSize <= (maxSetSize >> Math.max(3, minPartitionDepth + 1)))
			{
				BinaryPatriciaTrie<T> topTrie;
				BinaryPatriciaTrie<T> bottomTrie;
				String bottomId;

				// Merge into either the parent or child trie. Preferring whichever is smaller.
				if (parentTrie != null && curTrie.childTrieLabel != null)
				{
					String childTrieId = getTrieIdFromBytes(curTrie.childTrieLabel.label,
							curTrie.childTrieLabel.bitsUsed);
					BinaryPatriciaTrie<T> childTrie = trieCache.get(childTrieId);
					if (parentTrie.n <= childTrie.n)
					{
						topTrie = parentTrie;
						bottomTrie = curTrie;
						bottomId = curTrieId;
					} else
					{
						topTrie = curTrie;
						bottomTrie = childTrie;
						bottomId = childTrieId;
					}
				} else if (parentTrie != null)
				{
					topTrie = parentTrie;
					bottomTrie = curTrie;
					bottomId = curTrieId;
				} else
				{
					topTrie = curTrie;
					String childTrieId = getTrieIdFromBytes(curTrie.childTrieLabel.label,
							curTrie.childTrieLabel.bitsUsed);
					bottomTrie = trieCache.get(childTrieId);
					bottomId = childTrieId;
				}

				topTrie.merge(bottomTrie);
				trieCache.unregister(bottomId);

				// Ensure the merge trie isn't larger than the maxSetSize.
				splitIfNecessary(topTrie);
			}
		}
		return result == 0;
	}

	@Override
	public boolean contains(T x)
	{
		BinaryPatriciaTrie<T> curTrie = trieCache.get("~");
		byte[] valueAsBytes = curTrie.convertToBytes(x);
		int result;
		while ((result = curTrie.contains(valueAsBytes)) > 0)
			curTrie = trieCache.get(getTrieIdFromBytes(valueAsBytes, result));

		return result == 0;
	}

	@Override
	public long size()
	{
		return size;
	}

	private class EMTrieIterator implements Iterator<T>
	{
		private Stack<Iterator<SearchPoint>> iterators = new Stack<Iterator<SearchPoint>>();
		private T nextResult;
		private byte[] prefix;

		public EMTrieIterator(byte[] prefix)
		{
			this.prefix = prefix;
			iterators.push(trieCache.get("~").iterator(prefix));
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean hasNext()
		{
			if (nextResult != null)
				return true;

			BinaryPatriciaTrie<T> root = trieCache.get("~");
			Iterator<SearchPoint> curIter = null;
			while (!iterators.isEmpty() && nextResult == null)
			{
				while (!iterators.isEmpty() && !((curIter = iterators.pop()).hasNext()))
					;
				if (curIter == null || !curIter.hasNext())
					return false;

				SearchPoint node = curIter.next();
				if (node.getLastMatchingNode().subtreeSize == 0)
				{
					iterators.push(curIter);
					curIter = trieCache.get(
							getTrieIdFromBytes(node.leftOver.label, node.leftOver.bitsUsed))
							.iterator(prefix);
				} else
					nextResult = (T) root.converter.readFromBytes(node.leftOver.label);
				iterators.push(curIter);
			}

			return nextResult != null;
		}

		@Override
		public T next()
		{
			hasNext();
			T result = nextResult;
			nextResult = null;
			return result;
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
		}

	}

	@Override
	public Iterator<T> iterator()
	{
		return new EMTrieIterator(new byte[0]);
	}

	@Override
	public Iterator<T> iterator(T from, T to)
	{
		return new EMTrieIterator(trieCache.get("~").convertToBytes(from));
	}

	public String toString()
	{
		String result = "";
		Iterator<T> vals = iterator();
		while (vals.hasNext())
			result += " " + vals.next();

		return result;
	}

	@Override
	public void close()
	{
		trieCache.close();
	}

	@Override
	public EMPrefixSearchableSet<T> createNewStructure(File newStorageDir)
	{
		return new ExternalMemoryTrie<T>(newStorageDir, this);
	}

	@Override
	public ExternalMemoryObjectCache<? extends ExternalizableMemoryObject> getObjectCache()
	{
		return trieCache;
	}

	public void setMaxSetSize(int maxSetSize)
	{
		this.maxSetSize = maxSetSize;
	}
}
