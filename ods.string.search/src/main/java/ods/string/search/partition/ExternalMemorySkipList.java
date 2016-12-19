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
import ods.string.search.partition.ExternalMemoryObjectCache.CompressType;
import ods.string.search.partition.splitsets.ExternalizableLinkedList;
import ods.string.search.partition.splitsets.ExternalizableListSet;
import ods.string.search.partition.splitsets.ExternalizableMemoryObject;
import ods.string.search.partition.splitsets.SplittableSet;

/**
 * This class represents a B-Skip List. A data element is only stored in it's topmost promoted
 * layer.
 */
public class ExternalMemorySkipList<T extends Comparable<T> & Serializable> implements
		EMPrefixSearchableSet<T>
{

	/**
	 * Represents a partitioned list from a layer of the Skip List. The partition has ID links to
	 * the previous and next partitions in it's list layer.
	 */
	private static class SubList<T extends Comparable<T> & Serializable> implements
			ExternalizableMemoryObject, Iterable<T>
	{
		private static final long serialVersionUID = -309297143139643805L;

		/**
		 * The ID to the next list partition in this skip list layer or null if this is the last
		 * partition.
		 */
		private String nextPartitionId;

		/**
		 * The ID to the previous list partition in this skip list layer or null if this is the
		 * first partition.
		 */
		private String prevPartitionId;

		/**
		 * Stores the data elements contained in this partition.
		 */
		public SplittableSet<T> structure;
		private transient boolean isDirty = false;

		public SubList(SplittableSet<T> type)
		{
			this.structure = type.createNewSet();
		}

		/**
		 * {@inheritDoc}
		 */
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

		/**
		 * {@inheritDoc}
		 */
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

		/**
		 * @return The number of elements stored in this partition.
		 */
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

	/**
	 * The probability of promoting an element and creating a new partition ranging from 0 to 1.
	 */
	private double promotionProbability;

	/**
	 * Stores all partitions.
	 */
	private ExternalMemoryObjectCache<SubList<T>> listCache;

	/**
	 * The current highest layer of the skip list. Starts at 1.
	 */
	private int maxHeight;

	/**
	 * The number of elements stored in the structure.
	 */
	private int size;

	/**
	 * The RNG used to calculate promotions.
	 */
	private Random rand = new Random();

	/**
	 * A template SplittableSet used to generate new partitions.
	 */
	private SplittableSet<T> partitionImplementation;

	/**
	 * A string parameter constructor used to create data elements from partition IDs.
	 */
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
			listCache = new ExternalMemoryObjectCache<SubList<T>>(storageDirectory, cacheSize,
					CompressType.SNAPPY);
		maxHeight = 1;
		SubList<T> root = new SubList<T>(partitionImplementation);
		listCache.register("-1", root);
	}

	@Override
	public Iterator<T> iterator()
	{
		return new EMSkipIterator();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean add(T u)
	{
		SubList<T> root = listCache.get("-" + maxHeight);
		ArrayList<String> insertionPath = new ArrayList<String>(maxHeight);
		if (!find(u, root, maxHeight, "", insertionPath))
		{
			// Try to promote as many times as the current maximum list height.
			boolean promotion = false;
			for (int x = 0; x < insertionPath.size(); x++)
			{
				String listLayerEntry = insertionPath.get(x);
				if (!(promotion = promoteOrInsert(u, listCache.get(listLayerEntry), listLayerEntry,
						x + 1)))
					break;
			}
			size++;

			/*
			 * If the element was promoted to a new record height, a new list layer (root partition)
			 * needs to be created.
			 */
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

	/**
	 * Adds the specified element to the specified partition.
	 */
	private void addToCollection(T u, SubList<T> subList)
	{
		subList.structure.add(u);
	}

	/**
	 * Attempts to promote the specified element from the specified partition based on the promotion
	 * probability. If promoted, the current partition is split based on the new element's value.
	 * Otherwise, the new element is added into the current partition.
	 * 
	 * @param u
	 *            The element to attempt to promote.
	 * @param startPartition
	 *            The current partition to either split into a new partition or add the new element
	 *            to.
	 * @param startPartitionId
	 *            The ID of the current partition.
	 * @param height
	 *            The list height of the current partition where 1 is the bottom list.
	 * @return True if the specified element was promoted creating a new partition. False if the
	 *         element was simply added to the current partition.
	 */
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean remove(T x)
	{
		ArrayList<String> findPath = new ArrayList<String>(maxHeight);
		if (find(x, listCache.get("-" + maxHeight), maxHeight, "", findPath))
		{
			String deepestLayerFind = findPath.get(0);
			SubList<T> subList = listCache.get(deepestLayerFind);
			subList.structure.remove(x);

			/*
			 * If the element was removed from a list layer that wasn't the bottom, all list layers
			 * below need to perform a merge based on the deleted element.
			 */
			int deletionHeight = maxHeight - findPath.size();
			for (int y = deletionHeight; y >= 1; y--)
			{
				/*
				 * Merge with the next partition unless it doesn't exist, in which case merge with
				 * the previous partition.
				 */
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

			// If the top list layer is now empty, lower the maximum list height of the skip list.
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean contains(T x)
	{
		SubList<T> root = listCache.get("-" + maxHeight);
		return find(x, root, maxHeight, "", null);
	}

	/**
	 * A recursive method called for each layer of the skip list.
	 * 
	 * @param u
	 *            The element to find.
	 * @param startPartition
	 *            The current partition to search through.
	 * @param height
	 *            The current partition list height.
	 * @param parentKey
	 *            The ID of the current partition.
	 * @param layerTraversalPath
	 *            Optional parameter. If an empty list is provided, the partition IDs descended into
	 *            at each list layer to find the element will be stored here.
	 * @return True if the element was found, false otherwise.
	 */
	@SuppressWarnings("unchecked")
	private boolean find(T u, SubList<T> startPartition, int height, String parentKey,
			List<String> layerTraversalPath)
	{
		boolean result = false;
		int x = 0;
		T floorVal = null;
		try
		{
			// Initialize the string based element contructor if not already done.
			if (comparableConstructor == null)
				comparableConstructor = (Constructor<? extends Comparable<T>>) u.getClass()
						.getConstructor(String.class);

			/*
			 * Continue iterating while there are more partitions in this list layer or an element
			 * smaller than the search element is found.
			 */
			while (x < startPartition.size() || startPartition.nextPartitionId != null)
			{
				String nextParentKey = null;
				if (startPartition.nextPartitionId != null)
					nextParentKey = startPartition.nextPartitionId.substring(0,
							startPartition.nextPartitionId.lastIndexOf("-"));

				if (startPartition.nextPartitionId == null
						|| comparableConstructor.newInstance(nextParentKey).compareTo(u) > 0)
				{
					// The matching or floor element is in this partition.
					floorVal = startPartition.structure.floor(u);
					x = 0;
					if (u.equals(floorVal))
					{
						if (layerTraversalPath != null)
							layerTraversalPath.add(parentKey + "-" + height);
						return true;
					}
				} else
					// The match is in another partition.
					x = (int) startPartition.size();

				if (x >= startPartition.size() && startPartition.nextPartitionId != null)
				{
					if (comparableConstructor.newInstance(nextParentKey).compareTo(u) < 0)
					{
						// The floor element is somewhere in the next partition.
						startPartition = listCache.get(startPartition.nextPartitionId);
						x = 0;
						parentKey = nextParentKey;
					} else
						break;
				} else
				{
					// A floor element was found.
					break;
				}
			}

			// QQQ Descend to the next layer if not at the bottom since no exact match was found.
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
						// Use the parent ID value since it's the only one lower than the elem.
						nextLayerKey = parentKey + nextLayerKey;
					else
					{
						// Use the floor match value to descend.
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
			layerTraversalPath.add(parentKey + "-" + height);
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

	/**
	 * This class keeps track of an iterator's state while iterating over the bottom layer of the
	 * skip list.
	 */
	private class EMSkipIterator implements Iterator<T>
	{
		/**
		 * The current bottom layer list partition being iterated over.
		 */
		public SubList<T> subList;

		/**
		 * The current partition iterator.
		 */
		public Iterator<T> iter;

		/**
		 * The next element to return in a call to next().
		 */
		private T nextResult;

		/**
		 * The value to end iterating at (exclusive). Null means iterate until the end.
		 */
		private T endValue;

		public EMSkipIterator()
		{
			subList = listCache.get("-1");
			iter = subList.iterator();
		}

		public EMSkipIterator(T startValue, T endValue)
		{
			this.endValue = endValue;

			ArrayList<String> findPath = new ArrayList<String>(maxHeight);
			find(startValue, listCache.get("-" + maxHeight), maxHeight, "", findPath);
			String partitionKey = findPath.get(0);
			if (!partitionKey.endsWith("-1"))
			{
				/*
				 * The starting element was a promoted one, choose its lowest layer partition to
				 * start iterating from.
				 */
				partitionKey = startValue + "-1";
				subList = listCache.get(partitionKey);
				nextResult = startValue;
				iter = subList.structure.iterator(startValue, null);
			} else
			{
				subList = listCache.get(partitionKey);
				iter = subList.structure.iterator(startValue, null);
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean hasNext()
		{
			if (nextResult == null)
			{
				if (!iter.hasNext() && subList.nextPartitionId != null)
				{
					// Switch to the next list partition since one exists.
					int lastDashIndex = subList.nextPartitionId.lastIndexOf("-");
					String partitionIdStrVal = subList.nextPartitionId.substring(0, lastDashIndex);
					T partitionIdVal;
					subList = listCache.get(subList.nextPartitionId);
					iter = subList.structure.iterator();
					try
					{
						if (lastDashIndex > 0)
						{
							// Use the value in the next partition ID as the next return value.
							partitionIdVal = (T) comparableConstructor
									.newInstance(partitionIdStrVal);
							if (endValue == null || partitionIdVal.compareTo(endValue) < 0)
								nextResult = partitionIdVal;
						}
					} catch (Exception e)
					{
						throw new RuntimeException(e);
					}
				}
				if (nextResult == null && iter.hasNext())
					nextResult = iter.next();
			}

			return nextResult != null && (endValue == null || nextResult.compareTo(endValue) < 0);
		}

		@Override
		public T next()
		{
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close()
	{
		listCache.close();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EMPrefixSearchableSet<T> createNewStructure(File newStorageDir)
	{
		return new ExternalMemorySkipList<T>(newStorageDir, this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ExternalMemoryObjectCache<? extends ExternalizableMemoryObject> getObjectCache()
	{
		return listCache;
	}
}
