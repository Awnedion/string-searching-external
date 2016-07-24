package ods.string.search.partition;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

import ods.string.search.PrefixSearchableSet;
import ods.string.search.partition.splitsets.ExternalizableLinkedList;
import ods.string.search.partition.splitsets.ExternalizableListSet;
import ods.string.search.partition.splitsets.ExternalizableMemoryObject;
import ods.string.search.partition.splitsets.SplittableSet;

public class ExternalMemorySkipList<T extends Comparable<T> & Serializable> implements
		EMPrefixSearchableSet<T>
{
	private static class SubList<T extends Comparable<T> & Serializable> implements
			ExternalizableMemoryObject, Iterable<T>
	{
		private static final long serialVersionUID = -309297143139643805L;

		public String nextPartitionId;
		public String prevPartitionId;
		public SplittableSet<T> structure;

		@SuppressWarnings("unchecked")
		public SubList(SplittableSet<T> type)
		{
			try
			{
				Class<? extends SplittableSet<T>> implClass = (Class<? extends SplittableSet<T>>) type
						.getClass();
				this.structure = implClass.getConstructor(implClass).newInstance(type);
			} catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}

		@Override
		public long getByteSize()
		{
			// 16 for class, 8 for pointer to structure, 64*2 for strings IDs
			return structure.getByteSize() + 152;
		}

		@Override
		public boolean isDirty()
		{
			return structure.isDirty();
		}

		@Override
		public Iterator<T> iterator()
		{
			return ((Iterable<T>) structure).iterator();
		}

		public long size()
		{
			return ((PrefixSearchableSet<?>) structure).size();
		}
	}

	private double promotionProbability;
	private ExternalMemoryObjectCache<SubList<T>> listCache;
	private int maxHeight;
	private int size;
	private Random rand = new Random();
	private SplittableSet<T> partitionImplementation;
	private Constructor<? extends Comparable<T>> comparableConstructor;

	public ExternalMemorySkipList(File storageDirectory)
	{
		promotionProbability = 1. / 35.;
		partitionImplementation = new ExternalizableListSet<T>(
				new ExternalizableLinkedList<T>(), true);
		init(storageDirectory, 1000000000);
	}

	public ExternalMemorySkipList(File storageDirectory, double promotionProbability,
			long cacheSize, SplittableSet<T> subListType)
	{
		this.promotionProbability = promotionProbability;
		partitionImplementation = subListType;
		init(storageDirectory, cacheSize);
	}

	public ExternalMemorySkipList(File storageDirectory, ExternalMemorySkipList<T> baseConfig)
	{
		this.promotionProbability = baseConfig.promotionProbability;
		partitionImplementation = baseConfig.partitionImplementation.createNewSet();
		listCache = new ExternalMemoryObjectCache<>(storageDirectory, baseConfig.listCache);
		init(storageDirectory, 0);
	}

	private void init(File storageDirectory, long cacheSize)
	{
		if (listCache != null)
			listCache = new ExternalMemoryObjectCache<SubList<T>>(storageDirectory, cacheSize, true);
		maxHeight = 1;
		SubList<T> root = new SubList<T>(partitionImplementation);
		listCache.register("-1", root);
	}

	@Override
	public Iterator<T> iterator()
	{
		return new EMSkipIterator();
	}

	@Override
	public boolean add(T u)
	{
		SubList<T> root = listCache.get("-" + maxHeight);
		ArrayList<ListLayerEntry> insertionPath = new ArrayList<ListLayerEntry>();
		if (!find(u, root, maxHeight, "", insertionPath))
		{
			boolean promotion = false;
			for (int x = 0; x < insertionPath.size(); x++)
			{
				ListLayerEntry listLayerEntry = insertionPath.get(x);
				if (!(promotion = promoteOrInsert(u,
						listCache.get(listLayerEntry.listPartitionKey),
						listLayerEntry.listPartitionKey, listLayerEntry.index, x + 1)))
					break;
			}
			size++;
			if (promotion)
			{
				maxHeight++;
				SubList<T> newRoot = new SubList<T>(partitionImplementation);
				addToCollection(u, newRoot, 0);
				listCache.register("-" + maxHeight, newRoot);
			}
			return true;
		}

		return false;
	}

	private void addToCollection(T u, SubList<T> subList, int suggestedIndex)
	{
		subList.structure.add(u);
	}

	private boolean promoteOrInsert(T u, SubList<T> startPartition, String startPartitionId, int x,
			int height)
	{
		boolean promote = rand.nextDouble() < promotionProbability;
		if (promote)
		{
			SubList<T> newPartition = new SubList<T>(partitionImplementation);
			newPartition.structure = startPartition.structure.split(u);
			String newPartitionId = u.toString() + "-" + height;

			listCache.register(newPartitionId, newPartition);
			newPartition.nextPartitionId = startPartition.nextPartitionId;
			startPartition.nextPartitionId = newPartitionId;
			newPartition.prevPartitionId = startPartitionId;

			if (newPartition.nextPartitionId != null)
			{
				SubList<T> afterPartition = listCache.get(newPartition.nextPartitionId);
				afterPartition.prevPartitionId = newPartitionId;
			}
			return true;
		} else
		{
			addToCollection(u, startPartition, x);
		}
		return false;
	}

	@Override
	public boolean remove(T x)
	{
		ArrayList<ListLayerEntry> findPath = new ArrayList<ListLayerEntry>();
		if (find(x, listCache.get("-" + maxHeight), maxHeight, "", findPath))
		{
			ListLayerEntry deepestLayerFind = findPath.get(0);
			SubList<T> subList = listCache.get(deepestLayerFind.listPartitionKey);
			subList.structure.remove(x);

			int deletionHeight = maxHeight - findPath.size();
			for (int y = deletionHeight; y >= 1; y--)
			{
				String deletingPartitionId = x.toString() + "-" + y;
				SubList<T> toBeMovedPartition = listCache.get(deletingPartitionId);
				SubList<T> destinationPartition = listCache.get(toBeMovedPartition.prevPartitionId);
				destinationPartition.structure.merge(toBeMovedPartition.structure);
				destinationPartition.nextPartitionId = toBeMovedPartition.nextPartitionId;
				if (toBeMovedPartition.nextPartitionId != null)
				{
					SubList<T> afterMovedPartition = listCache
							.get(toBeMovedPartition.nextPartitionId);
					afterMovedPartition.prevPartitionId = toBeMovedPartition.prevPartitionId;
				}
				listCache.unregister(deletingPartitionId);
			}

			SubList<T> root = listCache.get("-" + maxHeight);
			if (root.size() == 0 && maxHeight > 1)
			{
				listCache.unregister("-" + maxHeight);
				maxHeight--;
			}

			size--;
			return true;
		}
		return false;
	}

	@Override
	public boolean contains(T x)
	{
		SubList<T> root = listCache.get("-" + maxHeight);
		return find(x, root, maxHeight, "", null);
	}

	private class ListLayerEntry
	{
		public int index;
		public String listPartitionKey;

		public ListLayerEntry(String listPartitionKey, int index)
		{
			this.index = index;
			this.listPartitionKey = listPartitionKey;
		}
	}

	@SuppressWarnings("unchecked")
	private boolean find(T u, SubList<T> startPartition, int height, String parentKey,
			List<ListLayerEntry> layerTraversalPath)
	{
		boolean result = false;
		int x = 0;
		T floorVal = null;
		try
		{
			if (comparableConstructor == null)
				comparableConstructor = (Constructor<? extends Comparable<T>>) u.getClass()
						.getConstructor(String.class);

			while (x < startPartition.size() || startPartition.nextPartitionId != null)
			{
				String nextParentKey = null;
				if (startPartition.nextPartitionId != null)
					nextParentKey = startPartition.nextPartitionId.substring(0,
							startPartition.nextPartitionId.lastIndexOf("-"));

				if (startPartition.nextPartitionId == null
						|| comparableConstructor.newInstance(nextParentKey).compareTo(u) > 0)
				{
					floorVal = startPartition.structure.floor(u);
					x = 0;
					if (u.equals(floorVal))
					{
						if (layerTraversalPath != null)
							layerTraversalPath.add(new ListLayerEntry(parentKey + "-" + height, x));
						return true;
					}
				} else
					x = (int) startPartition.size();

				if (x >= startPartition.size() && startPartition.nextPartitionId != null)
				{
					if (comparableConstructor.newInstance(nextParentKey).compareTo(u) < 0)
					{
						startPartition = listCache.get(startPartition.nextPartitionId);
						x = 0;
						parentKey = nextParentKey;
					} else
						break;
				} else
				{
					break;
				}
			}

			if (height > 1)
			{
				String nextLayerKey = "-" + (height - 1);
				String newParentKey = parentKey;
				if (x > 0)
				{
					newParentKey = ((List<T>) startPartition.structure).get(x - 1).toString();
					nextLayerKey = newParentKey + nextLayerKey;
				} else
				{
					if (floorVal == null)
						nextLayerKey = parentKey + nextLayerKey;
					else
					{
						newParentKey = floorVal.toString();
						nextLayerKey = newParentKey + nextLayerKey;
					}
				}
				result = find(u, listCache.get(nextLayerKey), height - 1, newParentKey,
						layerTraversalPath);
			}
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}

		if (layerTraversalPath != null)
			layerTraversalPath.add(new ListLayerEntry(parentKey + "-" + height, x));
		return result;
	}

	@Override
	public long size()
	{
		return size;
	}

	@Override
	public Iterator<T> iterator(T from, T to)
	{
		if (to.compareTo(from) < 0)
		{
			T temp = from;
			from = to;
			to = temp;
		}
		return new EMSkipIterator(from, to);
	}

	private class LowestSubListEntry implements Comparable<LowestSubListEntry>
	{
		public SubList<T> subList;
		public Iterator<T> iter;
		public T elem;

		public LowestSubListEntry(SubList<T> subList, Iterator<T> iter, T elem)
		{
			this.subList = subList;
			this.iter = iter;
			this.elem = elem;
		}

		@Override
		public int compareTo(LowestSubListEntry arg0)
		{
			return elem.compareTo(arg0.elem);
		}

		public String toString()
		{
			return elem.toString();
		}
	}

	private class EMSkipIterator implements Iterator<T>
	{
		private PriorityQueue<LowestSubListEntry> nextSmallestEntryQueue = new PriorityQueue<LowestSubListEntry>();
		private T lastResult;

		private T endValue;

		public EMSkipIterator()
		{
			for (int x = 0; x < maxHeight; x++)
			{
				SubList<T> subList = listCache.get("-" + (x + 1));
				Iterator<T> iter = subList.iterator();
				LowestSubListEntry newSubListEntry = new LowestSubListEntry(subList, iter, null);
				if (getNextEntry(newSubListEntry))
					nextSmallestEntryQueue.add(newSubListEntry);
			}
		}

		public EMSkipIterator(T startValue, T endValue)
		{
			this.endValue = endValue;

			ArrayList<ListLayerEntry> findPath = new ArrayList<ListLayerEntry>();
			find(startValue, listCache.get("-" + maxHeight), maxHeight, "", findPath);

			for (ListLayerEntry entry : findPath)
			{
				SubList<T> list = listCache.get(entry.listPartitionKey);

				LowestSubListEntry newSubListEntry = new LowestSubListEntry(list,
						list.structure.iterator(startValue, endValue), null);
				if (getNextEntry(newSubListEntry))
					nextSmallestEntryQueue.add(newSubListEntry);
			}

			int underMatchHeight = maxHeight - findPath.size();
			for (int y = underMatchHeight; y >= 1; y--)
			{
				SubList<T> subList = listCache.get(startValue.toString() + "-" + y);
				Iterator<T> iter = subList.iterator();
				LowestSubListEntry newSubListEntry = new LowestSubListEntry(subList, iter, null);
				if (getNextEntry(newSubListEntry))
					nextSmallestEntryQueue.add(newSubListEntry);
			}
		}

		@Override
		public boolean hasNext()
		{
			return nextSmallestEntryQueue.size() > 0;
		}

		@Override
		public T next()
		{
			LowestSubListEntry nextEntry = nextSmallestEntryQueue.remove();
			lastResult = nextEntry.elem;
			if (getNextEntry(nextEntry))
				nextSmallestEntryQueue.add(nextEntry);
			return lastResult;
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
		}

		private boolean getNextEntry(LowestSubListEntry subList)
		{
			while (subList.subList.nextPartitionId != null || subList.iter.hasNext())
			{
				if (subList.iter.hasNext())
				{
					subList.elem = subList.iter.next();
					if (endValue != null && subList.elem.compareTo(endValue) >= 0)
					{
						subList.elem = null;
						return false;
					}
					return true;
				} else if (subList.subList.nextPartitionId != null)
				{
					subList.subList = listCache.get(subList.subList.nextPartitionId);
					subList.iter = subList.subList.iterator();
				} else
				{
					subList.elem = null;
					return false;
				}
			}

			return false;
		}
	}

	public String toString()
	{
		String result = "[";
		for (Iterator<T> iter = iterator(); iter.hasNext();)
		{
			T nextEntry = iter.next();
			result += nextEntry.toString() + ", ";
		}
		result += "]";
		return result;
	}

	@Override
	public void close()
	{
		listCache.close();
	}

	@Override
	public EMPrefixSearchableSet<T> createNewStructure(File newStorageDir)
	{
		return new ExternalMemorySkipList<T>(newStorageDir, this);
	}

	@Override
	public ExternalMemoryObjectCache<? extends ExternalizableMemoryObject> getObjectCache()
	{
		return listCache;
	}
}
