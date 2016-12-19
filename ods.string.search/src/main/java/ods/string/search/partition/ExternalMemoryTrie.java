package ods.string.search.partition;

import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Stack;

import ods.string.search.partition.BinaryPatriciaTrie.ByteArrayConversion;
import ods.string.search.partition.BinaryPatriciaTrie.SearchPoint;
import ods.string.search.partition.ExternalMemoryObjectCache.CompressType;
import ods.string.search.partition.splitsets.ExternalizableMemoryObject;

import org.apache.commons.codec.binary.Hex;

/**
 * This class represents a Partitioned Patricia Trie. Nodes are binary based having up to two
 * branches (0 bit, 1 bit). Edges are compacted able to store multiple bits.
 * 
 * Partitions are created by trying to split the current partition in half by default. The partition
 * split node can be forced to have a minimum tree depth before being eligible.
 */
public class ExternalMemoryTrie<T extends Comparable<T> & Serializable> implements
		EMPrefixSearchableSet<T>
{
	/**
	 * Stores all partitions.
	 */
	private ExternalMemoryObjectCache<BinaryPatriciaTrie<T>> trieCache;

	/**
	 * The maximum size a trie parition can be before being split.
	 */
	private int maxSetSize = 100000;

	/**
	 * The current number of elements stored in the trie.
	 */
	private long size = 0;

	/**
	 * The minimum depth in a trie partition that a node has to be before being eligible to be the
	 * root of a new partition.
	 */
	private int minPartitionDepth;

	public ExternalMemoryTrie(File storageDirectory)
	{
		trieCache = new ExternalMemoryObjectCache<BinaryPatriciaTrie<T>>(storageDirectory,
				100000000, CompressType.SNAPPY);
		BinaryPatriciaTrie<T> root = new BinaryPatriciaTrie<T>();
		trieCache.register("~", root);
		minPartitionDepth = 0;
	}

	public ExternalMemoryTrie(File storageDirectory, int maxSetSize, long maxInMemoryBytes,
			int minPartitionDepth)
	{
		this.maxSetSize = maxSetSize;
		trieCache = new ExternalMemoryObjectCache<BinaryPatriciaTrie<T>>(storageDirectory,
				maxInMemoryBytes, CompressType.SNAPPY);
		trieCache.register("~", new BinaryPatriciaTrie<T>());
		this.minPartitionDepth = minPartitionDepth;
	}

	@SuppressWarnings("unchecked")
	public ExternalMemoryTrie(ExternalMemoryObjectCache<?> cache, int maxSetSize,
			int minPartitionDepth)
	{
		this.maxSetSize = maxSetSize;
		trieCache = (ExternalMemoryObjectCache<BinaryPatriciaTrie<T>>) cache;
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean add(T u)
	{
		BinaryPatriciaTrie<T> curTrie = trieCache.get("~");

		byte[] valueAsBytes = curTrie.convertToBytes(u);
		int result;

		// Traverse between partitions if the search path encounters a pointer node.
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

	/**
	 * Splits the specified partition if it's larger than the maximum allowed partition size.
	 */
	private void splitIfNecessary(BinaryPatriciaTrie<T> curTrie)
	{
		if (curTrie.r.subtreeSize > maxSetSize)
		{
			BinaryPatriciaTrie<T> newTrie = (BinaryPatriciaTrie<T>) curTrie.split(null,
					minPartitionDepth);
			trieCache.register(getTrieIdFromBytes(newTrie.r.bits.label, newTrie.r.bits.bitsUsed),
					newTrie);
		}
	}

	/**
	 * Converts the specified bit string into an ID string used to identify the trie partition whose
	 * root node stores the specified bit string.
	 * 
	 * @param value
	 *            The bit string to convert to an ID.
	 * @param bitsUsed
	 *            The number of bits from the byte array to use in the ID.
	 */
	private String getTrieIdFromBytes(byte[] value, int bitsUsed)
	{
		int fullBytesUsed = bitsUsed / 8;
		int remainingBits = bitsUsed % 8;

		// Convert complete bytes into it's hex encoding.
		String result = Hex.encodeHexString(value).substring(0, fullBytesUsed * 2);

		// Convert the remaining bits into a string represented bit string.
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean remove(T x)
	{
		BinaryPatriciaTrie<T> curTrie = trieCache.get("~");
		byte[] valueAsBytes = curTrie.convertToBytes(x);
		int result;

		BinaryPatriciaTrie<T> parentTrie = null;
		String curTrieId = "~";

		// Traverse between partitions if the search path encounters a pointer node.
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
			int minTrieSize = (minPartitionDepth <= 1 ? maxSetSize / 6
					: (maxSetSize >> (minPartitionDepth + 1)));
			if ((curTrie.childTrieLabel != null || parentTrie != null)
					&& curTrie.r.subtreeSize <= minTrieSize)
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

	/**
	 * {@inheritDoc}
	 */
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long size()
	{
		return size;
	}

	/**
	 * This class keeps track of an iterator's state while iterating over partitions and elements in
	 * a depth first search way.
	 */
	private class EMTrieIterator implements Iterator<T>
	{
		/**
		 * Stores an incomplete iterator per partition. Iterators are traversed in a depth first
		 * search way.
		 */
		private Stack<Iterator<SearchPoint>> iterators = new Stack<Iterator<SearchPoint>>();

		/**
		 * The next iterator result.
		 */
		private T nextResult;

		/**
		 * The prefix that all returned results must match. A 0 length array returns all results.
		 */
		private byte[] prefix;

		/**
		 * Cache the operation to convert bit strings into actual data types.
		 */
		private ByteArrayConversion converter;

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

			Iterator<SearchPoint> curIter = null;
			while (!iterators.isEmpty() && nextResult == null)
			{
				// Find the first iterator that isn't empty.
				while (!iterators.isEmpty() && !((curIter = iterators.pop()).hasNext()))
					;

				// The last iterator is empty.
				if (curIter == null || !curIter.hasNext())
					return false;

				SearchPoint node = curIter.next();
				if (node.lastMatchingNode.subtreeSize == 0)
				{
					// Found a pointer node, create a new iterator and use it.
					iterators.push(curIter);
					curIter = trieCache.get(
							getTrieIdFromBytes(node.leftOver.label, node.leftOver.bitsUsed))
							.iterator(prefix);
				} else
				{
					if (converter == null)
					{
						BinaryPatriciaTrie<T> root = trieCache.get("~");
						converter = root.converter;
					}
					nextResult = (T) converter.readFromBytes(node.leftOver.label);
				}
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
