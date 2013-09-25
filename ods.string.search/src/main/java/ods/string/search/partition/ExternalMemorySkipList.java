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

public class ExternalMemorySkipList<T extends Comparable<T> & Serializable> implements
		PrefixSearchableSet<T>
{
	private double promotionProbability;
	private ExternalMemoryObjectCache<ExternalizableLinkedList<T>> listCache;
	private int maxHeight;
	private int size;
	private Random rand = new Random();

	public ExternalMemorySkipList(File storageDirectory)
	{
		promotionProbability = 1. / 35.;
		init(storageDirectory, 1000000000);
	}

	public ExternalMemorySkipList(File storageDirectory, double promotionProbability, long cacheSize)
	{
		this.promotionProbability = promotionProbability;
		init(storageDirectory, cacheSize);
	}

	private void init(File storageDirectory, long cacheSize)
	{
		listCache = new ExternalMemoryObjectCache<ExternalizableLinkedList<T>>(storageDirectory,
				cacheSize, true);
		maxHeight = 1;
		listCache.register("-1", new ExternalizableLinkedList<T>());
	}

	@Override
	public Iterator<T> iterator()
	{
		return new EMSkipIterator();
	}

	@Override
	public boolean add(T u)
	{
		ExternalizableLinkedList<T> root = listCache.get("-" + maxHeight);
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
				ExternalizableLinkedList<T> newRoot = new ExternalizableLinkedList<T>();
				newRoot.add(0, u);
				listCache.register("-" + maxHeight, newRoot);
			}
			return true;
		}

		return false;
	}

	private boolean promoteOrInsert(T u, ExternalizableLinkedList<T> startPartition,
			String startPartitionId, int x, int height)
	{
		boolean promote = rand.nextDouble() < promotionProbability;
		if (promote)
		{
			ExternalizableLinkedList<T> newPartition = new ExternalizableLinkedList<T>();
			int transfers = startPartition.size() - x;
			for (int y = 0; y < transfers; y++)
			{
				newPartition.add(0, startPartition.remove(startPartition.size() - 1));
			}
			String newPartitionId = u.toString() + "-" + height;

			listCache.register(newPartitionId, newPartition);
			newPartition.nextPartitionId = startPartition.nextPartitionId;
			startPartition.nextPartitionId = newPartitionId;
			newPartition.prevPartitionId = startPartitionId;

			if (newPartition.nextPartitionId != null)
			{
				ExternalizableLinkedList<T> afterPartition = listCache
						.get(newPartition.nextPartitionId);
				afterPartition.prevPartitionId = newPartitionId;
			}
			return true;
		} else
		{
			startPartition.add(x, u);
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
			listCache.get(deepestLayerFind.listPartitionKey).remove(deepestLayerFind.index);

			int deletionHeight = maxHeight - findPath.size();
			for (int y = deletionHeight; y >= 1; y--)
			{
				String deletingPartitionId = x.toString() + "-" + y;
				ExternalizableLinkedList<T> toBeMovedPartition = listCache.get(deletingPartitionId);
				ExternalizableLinkedList<T> destinationPartition = listCache
						.get(toBeMovedPartition.prevPartitionId);
				destinationPartition.addAll(toBeMovedPartition);
				destinationPartition.nextPartitionId = toBeMovedPartition.nextPartitionId;
				if (toBeMovedPartition.nextPartitionId != null)
				{
					ExternalizableLinkedList<T> afterMovedPartition = listCache
							.get(toBeMovedPartition.nextPartitionId);
					afterMovedPartition.prevPartitionId = toBeMovedPartition.prevPartitionId;
				}
				listCache.unregister(deletingPartitionId);
			}

			ExternalizableLinkedList<T> root = listCache.get("-" + maxHeight);
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
		ExternalizableLinkedList<T> root = listCache.get("-" + maxHeight);
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
	private boolean find(T u, ExternalizableLinkedList<T> startPartition, int height,
			String parentKey, List<ListLayerEntry> layerTraversalPath)
	{
		boolean result = false;
		int x = 0;
		while (x < startPartition.size() || startPartition.nextPartitionId != null)
		{
			for (x = 0; x < startPartition.size(); x++)
			{
				int compare = u.compareTo(startPartition.get(x));
				if (compare < 0)
					break;
				else if (compare == 0)
				{
					if (layerTraversalPath != null)
						layerTraversalPath.add(new ListLayerEntry(parentKey + "-" + height, x));
					return true;
				}
			}

			if (x >= startPartition.size() && startPartition.nextPartitionId != null)
			{
				String nextParentKey = startPartition.nextPartitionId.substring(0,
						startPartition.nextPartitionId.lastIndexOf("-"));
				try
				{
					Constructor<? extends Comparable<T>> comparableConstructor = (Constructor<? extends Comparable<T>>) u
							.getClass().getConstructor(String.class);
					if (comparableConstructor.newInstance(nextParentKey).compareTo(u) < 0)
					{
						startPartition = listCache.get(startPartition.nextPartitionId);
						x = 0;
						parentKey = nextParentKey;
					} else
						break;
				} catch (Exception e)
				{
					throw new RuntimeException(e);
				}
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
				newParentKey = startPartition.get(x - 1).toString();
				nextLayerKey = newParentKey + nextLayerKey;
			} else
				nextLayerKey = parentKey + nextLayerKey;
			result = find(u, listCache.get(nextLayerKey), height - 1, newParentKey,
					layerTraversalPath);
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
		public ExternalizableLinkedList<T> subList;
		public Iterator<T> iter; // TODO iters break on remove, load into array in reverse?
		public T elem;

		public LowestSubListEntry(ExternalizableLinkedList<T> subList, Iterator<T> iter, T elem)
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
				ExternalizableLinkedList<T> subList = listCache.get("-" + (x + 1));
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
				ExternalizableLinkedList<T> list = listCache.get(entry.listPartitionKey);
				LowestSubListEntry newSubListEntry = new LowestSubListEntry(list, list.subList(
						entry.index, list.size()).iterator(), null);
				if (getNextEntry(newSubListEntry))
					nextSmallestEntryQueue.add(newSubListEntry);
			}

			int underMatchHeight = maxHeight - findPath.size();
			for (int y = underMatchHeight; y >= 1; y--)
			{
				ExternalizableLinkedList<T> subList = listCache
						.get(startValue.toString() + "-" + y);
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
		PriorityQueue<LowestSubListEntry> nextSmallestEntryQueue = new PriorityQueue<LowestSubListEntry>();

		for (int x = 0; x < maxHeight; x++)
		{
			ExternalizableLinkedList<T> subList = listCache.get("-" + (x + 1));
			Iterator<T> iter = subList.iterator();
			LowestSubListEntry newSubListEntry = new LowestSubListEntry(subList, iter, null);
			if (getNextEntry(newSubListEntry))
				nextSmallestEntryQueue.add(newSubListEntry);
		}

		String result = "[";
		while (nextSmallestEntryQueue.size() > 0)
		{
			LowestSubListEntry nextEntry = nextSmallestEntryQueue.remove();
			result += nextEntry.elem.toString() + ", ";
			if (getNextEntry(nextEntry))
				nextSmallestEntryQueue.add(nextEntry);
		}
		result += "]";
		return result;
	}

	private boolean getNextEntry(LowestSubListEntry subList)
	{
		while (subList.subList.nextPartitionId != null || subList.iter.hasNext())
		{
			if (subList.iter.hasNext())
			{
				subList.elem = subList.iter.next();
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
