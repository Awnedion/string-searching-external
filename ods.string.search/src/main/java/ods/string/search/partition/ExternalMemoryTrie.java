package ods.string.search.partition;

import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Stack;

import ods.string.search.PrefixSearchableSet;
import ods.string.search.partition.BinaryPatriciaTrie.SearchPoint;

import org.apache.commons.codec.binary.Hex;

public class ExternalMemoryTrie<T extends Comparable<T> & Serializable> implements
		PrefixSearchableSet<T>
{
	private ExternalMemoryObjectCache<BinaryPatriciaTrie<T>> trieCache;
	private int maxSetSize = 100000;
	private long size = 0;

	public ExternalMemoryTrie(File storageDirectory)
	{
		trieCache = new ExternalMemoryObjectCache<BinaryPatriciaTrie<T>>(storageDirectory,
				100000000, true);
		BinaryPatriciaTrie<T> root = new BinaryPatriciaTrie<T>();
		trieCache.register("~", root);
	}

	public ExternalMemoryTrie(File storageDirectory, int maxSetSize, long maxInMemoryBytes,
			int minPartitionDepth)
	{
		this.maxSetSize = maxSetSize;
		trieCache = new ExternalMemoryObjectCache<BinaryPatriciaTrie<T>>(storageDirectory,
				maxInMemoryBytes, true);
		trieCache.register("~", new BinaryPatriciaTrie<T>(minPartitionDepth));
	}

	@Override
	public boolean add(T u)
	{
		BinaryPatriciaTrie<T> curTrie = trieCache.get("~");

		byte[] valueAsBytes = curTrie.convertToBytes(u);
		int result;
		while ((result = curTrie.add(valueAsBytes)) > 0)
			curTrie = trieCache.get(getTrieIdFromBytes(valueAsBytes, result));

		if (result == 0 && curTrie.r.subtreeSize > maxSetSize)
		{
			BinaryPatriciaTrie<T> newTrie = (BinaryPatriciaTrie<T>) curTrie.split(u);
			trieCache.register(getTrieIdFromBytes(newTrie.r.label, newTrie.r.bitsUsed), newTrie);
		}

		if (result == 0)
			size++;

		return result == 0;
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

		while ((result = curTrie.remove(valueAsBytes)) > 0)
			curTrie = trieCache.get(getTrieIdFromBytes(valueAsBytes, result));

		if (result == 0)
		{
			size--;
			if (curTrie.childTrieLabel != null && curTrie.r.subtreeSize < (maxSetSize >> 3))
			{
				long beforeSize = curTrie.r.subtreeSize;
				String childTrieId = getTrieIdFromBytes(curTrie.childTrieLabel.label,
						curTrie.childTrieLabel.bitsUsed);
				BinaryPatriciaTrie<T> childToMerge = trieCache.get(childTrieId);
				curTrie.merge(childToMerge);
				trieCache.unregister(childTrieId);

				System.out.println("Set Merge performed: " + beforeSize + " "
						+ curTrie.r.subtreeSize);
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
}
