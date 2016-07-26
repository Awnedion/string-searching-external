package ods.string.search.partition;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

		private String nextPartitionId;
		private String prevPartitionId;
		public SplittableSet<T> structure;
		private transient boolean isDirty = false;

		public SubList(SplittableSet<T> type)
		{
			this.structure = type.createNewSet();
		}

		@Override
		public long getByteSize()
		{
			// 16 for class, 8 for pointer to structure, 64*2 for strings IDs
			long result = 24;
			if (nextPartitionId == null)
				result += 8;
			else
				result += 64 + (nextPartitionId.length() << 1);

			if (prevPartitionId == null)
				result += 8;
			else
				result += 64 + (prevPartitionId.length() << 1);
			return structure.getByteSize() + result;
		}

		@Override
		public boolean isDirty()
		{
			return structure.isDirty() || isDirty;
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

		public void setNextPartitionId(String nextPartitionId)
		{
			this.nextPartitionId = nextPartitionId;
			isDirty = true;
		}

		public void setPrevPartitionId(String prevPartitionId)
		{
			this.prevPartitionId = prevPartitionId;
			isDirty = true;
		}

		private void readObject(ObjectInputStream inputStream) throws IOException,
				ClassNotFoundException
		{
			inputStream.defaultReadObject();
			isDirty = false;
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
		partitionImplementation = new ExternalizableListSet<T>(new ExternalizableLinkedList<T>(),
				true);
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
		if (listCache == null)
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
						listLayerEntry.listPartitionKey, x + 1)))
					break;
			}
			size++;
			if (promotion)
			{
				maxHeight++;
				SubList<T> newRoot = new SubList<T>(partitionImplementation);
				addToCollection(u, newRoot);
				listCache.register("-" + maxHeight, newRoot);
			}
			return true;
		}

		return false;
	}

	private void addToCollection(T u, SubList<T> subList)
	{
		subList.structure.add(u);
	}

	private boolean promoteOrInsert(T u, SubList<T> startPartition, String startPartitionId,
			int height)
	{
		boolean promote = rand.nextDouble() < promotionProbability;
		if (promote)
		{
			SubList<T> newPartition = new SubList<T>(partitionImplementation);
			newPartition.structure = startPartition.structure.split(u);
			String newPartitionId = u.toString() + "-" + height;

			listCache.register(newPartitionId, newPartition);
			newPartition.setNextPartitionId(startPartition.nextPartitionId);
			startPartition.setNextPartitionId(newPartitionId);
			newPartition.setPrevPartitionId(startPartitionId);

			if (newPartition.nextPartitionId != null)
			{
				SubList<T> afterPartition = listCache.get(newPartition.nextPartitionId);
				afterPartition.setPrevPartitionId(newPartitionId);
			}
			return true;
		} else
		{
			addToCollection(u, startPartition);
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
				destinationPartition.setNextPartitionId(toBeMovedPartition.nextPartitionId);
				if (toBeMovedPartition.nextPartitionId != null)
				{
					SubList<T> afterMovedPartition = listCache
							.get(toBeMovedPartition.nextPartitionId);
					afterMovedPartition.setPrevPartitionId(toBeMovedPartition.prevPartitionId);
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
		public String listPartitionKey;

		public ListLayerEntry(String listPartitionKey)
		{
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
							layerTraversalPath.add(new ListLayerEntry(parentKey + "-" + height));
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
			layerTraversalPath.add(new ListLayerEntry(parentKey + "-" + height));
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

	private class EMSkipIterator implements Iterator<T>
	{
		public SubList<T> subList;
		public Iterator<T> iter;
		private T lastResult;

		private T endValue;
		private Constructor<? extends Comparable<T>> constructorByString;

		public EMSkipIterator()
		{
			subList = listCache.get("-1");
			iter = subList.iterator();
		}

		public EMSkipIterator(T startValue, T endValue)
		{
			lastResult = startValue;
			this.endValue = endValue;

			ArrayList<ListLayerEntry> findPath = new ArrayList<ListLayerEntry>();
			find(startValue, listCache.get("-" + maxHeight), maxHeight, "", findPath);
			String partitionKey = findPath.get(0).listPartitionKey;
			if (!partitionKey.endsWith("-1"))
			{
				int lastDashIndex = partitionKey.lastIndexOf("-");
				partitionKey = partitionKey.substring(0, lastDashIndex) + "-1";
			}
			subList = listCache.get(partitionKey);
			iter = subList.structure.iterator(lastResult, endValue);
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean hasNext()
		{
			if (iter != null && !iter.hasNext() && subList.nextPartitionId != null)
			{
				int lastDashIndex = subList.nextPartitionId.lastIndexOf("-");
				String partitionIdStrVal = subList.nextPartitionId.substring(0, lastDashIndex);
				T partitionIdVal;
				try
				{
					if (constructorByString == null)
						constructorByString = (Constructor<? extends Comparable<T>>) lastResult
								.getClass().getDeclaredConstructor(String.class);
					partitionIdVal = (T) constructorByString.newInstance(partitionIdStrVal);
					if (endValue == null || partitionIdVal.compareTo(endValue) < 0)
					{
						lastResult = partitionIdVal;
						iter = null;
					}
				} catch (Exception e)
				{
					throw new RuntimeException(e);
				}
			}

			return iter == null || iter.hasNext();
		}

		@Override
		public T next()
		{
			T result = null;
			if (iter == null)
			{
				result = lastResult;
				subList = listCache.get(subList.nextPartitionId);
				iter = subList.structure.iterator(lastResult, endValue);
			} else
				result = iter.next();
			lastResult = result;
			return lastResult;
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
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
