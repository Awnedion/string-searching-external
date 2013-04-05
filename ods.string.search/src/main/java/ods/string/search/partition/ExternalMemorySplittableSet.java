package ods.string.search.partition;

import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

import ods.string.search.PrefixSearchableSet;

public class ExternalMemorySplittableSet<T extends Comparable<T> & Serializable> implements
		PrefixSearchableSet<T>
{
	private ExternalMemoryObjectCache<SplittableSet<T>> setCache;
	private TreeMap<T, String> partitionRanges = new TreeMap<T, String>();
	private int maxSetSize = 100000;
	private long size = 0;
	private long uniqueId = 0;

	public ExternalMemorySplittableSet(File storageDirectory)
	{
		setCache = new ExternalMemoryObjectCache<SplittableSet<T>>(storageDirectory, 100000000,
				true);
		Treap<T> root = new Treap<T>();
		setCache.register(uniqueId + "", root);
		uniqueId++;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public ExternalMemorySplittableSet(File storageDirectory, int maxSetSize, long maxInMemoryBytes,
			Class<? extends SplittableSet> nodeType)
	{
		this.maxSetSize = maxSetSize;
		setCache = new ExternalMemoryObjectCache<SplittableSet<T>>(storageDirectory,
				maxInMemoryBytes, true);
		SplittableSet<T> root;
		try
		{
			root = nodeType.newInstance();
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		setCache.register(uniqueId + "", root);
		uniqueId++;
	}

	@Override
	public boolean add(T u)
	{
		SplittableSet<T> curSet = getSetPartition(u);

		boolean result = curSet.add(u);
		if (result && curSet.size() > maxSetSize)
		{
			T midValue = curSet.locateMiddleValue();
			partitionRanges.put(midValue, uniqueId + "");
			SplittableSet<T> newSet = curSet.split(midValue);
			if (maxSetSize > 1000)
				System.out.println("Set Split Performed: " + curSet.size() + " "
						+ newSet.size());
			setCache.register(uniqueId + "", newSet);
			uniqueId++;
		}

		if (result)
			size++;

		return result;
	}

	private SplittableSet<T> getSetPartition(T u)
	{
		Entry<T, String> setId = partitionRanges.floorEntry(u);
		SplittableSet<T> curSet;
		if (setId == null)
			curSet = setCache.get("0");
		else
			curSet = setCache.get(setId.getValue());
		return curSet;
	}

	@Override
	public boolean remove(T x)
	{
		SplittableSet<T> curSet = getSetPartition(x);
		boolean result = curSet.remove(x);
		if (result)
		{
			size--;
			if (partitionRanges.size() > 0 && curSet.size() < (maxSetSize >> 3))
			{
				Entry<T, String> smallEntry = partitionRanges.floorEntry(x);
				Entry<T, String> mergeEntry;
				SplittableSet<T> mergeSet;
				if (smallEntry == null)
				{
					mergeEntry = partitionRanges.higherEntry(x);
					mergeSet = setCache.get(mergeEntry.getValue());
					curSet.merge(mergeSet);
					setCache.unregister(mergeEntry.getValue());
					partitionRanges.remove(mergeEntry.getKey());
				} else
				{
					mergeEntry = partitionRanges.lowerEntry(smallEntry.getKey());
					mergeSet = setCache.get(mergeEntry == null ? "0" : mergeEntry.getValue());
					mergeSet.merge(curSet);
					setCache.unregister(smallEntry.getValue());
					partitionRanges.remove(smallEntry.getKey());
				}

				System.out.println("Set Merge performed: " + curSet.size() + " "
						+ mergeSet.size());
			}
		}
		return result;
	}

	@Override
	public boolean contains(T x)
	{
		SplittableSet<T> curSet = getSetPartition(x);
		return curSet.contains(x);
	}

	@Override
	public long size()
	{
		return size;
	}

	private class EMSetIterator implements Iterator<T>
	{
		private T currentPartitionKey;
		private SplittableSet<T> currentSet;
		private Iterator<T> currentSetIter;
		private String endSetKey;
		private SplittableSet<T> endSet;

		private T prev;

		private T from;
		private T to;

		public EMSetIterator(T from, T to)
		{
			this.from = from;
			this.to = to;

			if (from != null)
			{
				currentPartitionKey = from;
				Entry<T, String> startSetPartition = partitionRanges.floorEntry(from);
				if (startSetPartition == null)
					currentSet = setCache.get("0");
				else
					currentSet = setCache.get(startSetPartition.getValue());
				currentSetIter = currentSet.iterator(from, to);
			} else
			{
				currentSet = setCache.get("0");
				T val = currentSet.iterator().next();
				currentPartitionKey = val;
				this.from = val;
				currentSetIter = currentSet.iterator();
			}

			if (to != null)
			{
				Entry<T, String> endSetPartition = partitionRanges.floorEntry(to);
				if (endSetPartition == null)
				{
					endSet = setCache.get("0");
					endSetKey = "0";
				} else
				{
					endSetKey = endSetPartition.getValue();
					endSet = setCache.get(endSetKey);
				}
			} else
			{
				Entry<T, String> endSetPartition = partitionRanges.lastEntry();
				endSetKey = endSetPartition.getValue();
				endSet = setCache.get(endSetKey);
			}
		}

		@Override
		public boolean hasNext()
		{
			return currentSetIter.hasNext();
		}

		@Override
		public T next()
		{
			T result = currentSetIter.next();
			prev = result;

			if (!currentSetIter.hasNext() && currentSet != endSet)
			{
				Entry<T, String> nextPartition = partitionRanges.higherEntry(currentPartitionKey);
				try
				{
					currentPartitionKey = nextPartition.getKey();
				} catch (NullPointerException e)
				{
					e.printStackTrace();
				}
				currentSet = setCache.get(nextPartition.getValue());
				currentSetIter = currentSet.iterator(from, to);
				setCache.get(endSetKey);
			}

			return result;
		}

		@Override
		public void remove()
		{
			ExternalMemorySplittableSet.this.remove(prev);
		}

	}

	@Override
	public Iterator<T> iterator()
	{
		return new EMSetIterator(null, null);
	}

	@Override
	public Iterator<T> iterator(T from, T to)
	{
		if (to == null || from.compareTo(to) < 0)
			return new EMSetIterator(from, to);
		else
			return new EMSetIterator(to, from);
	}
}
