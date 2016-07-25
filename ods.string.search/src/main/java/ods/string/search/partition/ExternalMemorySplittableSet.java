package ods.string.search.partition;

import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

import ods.string.search.partition.splitsets.ExternalizableMemoryObject;
import ods.string.search.partition.splitsets.SplittableSet;
import ods.string.search.partition.splitsets.Treap;

public class ExternalMemorySplittableSet<T extends Comparable<T> & Serializable> implements
		EMPrefixSearchableSet<T>
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

	public ExternalMemorySplittableSet(File storageDirectory, int maxSetSize,
			long maxInMemoryBytes, SplittableSet<T> root)
	{
		this.maxSetSize = maxSetSize;
		setCache = new ExternalMemoryObjectCache<SplittableSet<T>>(storageDirectory,
				maxInMemoryBytes, true);
		setCache.register(uniqueId + "", root);
		uniqueId++;
	}

	public ExternalMemorySplittableSet(File storageDirectory,
			ExternalMemorySplittableSet<T> baseConfig)
	{
		this.maxSetSize = baseConfig.maxSetSize;
		setCache = new ExternalMemoryObjectCache<SplittableSet<T>>(storageDirectory,
				baseConfig.setCache);
		SplittableSet<T> root = baseConfig.setCache.get("0").createNewSet();
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
		private Iterator<T> currentSetIter;

		private T prev;

		private T to;

		public EMSetIterator(T from, T to)
		{
			this.prev = from;
			this.to = to;

			SplittableSet<T> currentSet;
			if (from != null)
			{
				Entry<T, String> startSetPartition = partitionRanges.floorEntry(from);
				if (startSetPartition == null)
					currentSet = setCache.get("0");
				else
					currentSet = setCache.get(startSetPartition.getValue());
				currentSetIter = currentSet.iterator(from, to);
			} else
			{
				currentSet = setCache.get("0");
				currentSetIter = currentSet.iterator();
			}
		}

		@Override
		public boolean hasNext()
		{
			if (!currentSetIter.hasNext() && (to == null || prev.compareTo(to) < 0))
			{
				Entry<T, String> nextPartition = partitionRanges.higherEntry(prev);
				if (nextPartition != null)
				{
					SplittableSet<T> currentSet = setCache.get(nextPartition.getValue());
					currentSetIter = currentSet.iterator(prev, to);
				}
			}

			return currentSetIter.hasNext();
		}

		@Override
		public T next()
		{
			T result = currentSetIter.next();
			prev = result;

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

	@Override
	public void close()
	{
		setCache.close();
	}

	@Override
	public EMPrefixSearchableSet<T> createNewStructure(File newStorageDir)
	{
		return new ExternalMemorySplittableSet<T>(newStorageDir, this);
	}

	@Override
	public ExternalMemoryObjectCache<? extends ExternalizableMemoryObject> getObjectCache()
	{
		return setCache;
	}
}
