package ods.string.search.partition;

import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

import ods.string.search.PrefixSearchableSet;
import ods.string.search.partition.Treap.Node;

public class ExternalMemoryTreap<T extends Comparable<T> & Serializable> implements
		PrefixSearchableSet<T>
{
	private ExternalMemoryObjectCache<Treap<T>> treapCache;
	private TreeMap<T, String> partitionRanges = new TreeMap<T, String>();
	private int maxTreapSize = 100000;
	private long size = 0;
	private long uniqueId = 0;

	public ExternalMemoryTreap(File storageDirectory)
	{
		treapCache = new ExternalMemoryObjectCache<Treap<T>>(storageDirectory, 100000000, true);
		Treap<T> root = new Treap<T>();
		treapCache.register(uniqueId + "", root);
		uniqueId++;
	}

	public ExternalMemoryTreap(File storageDirectory, int maxTreapSize, long maxInMemoryBytes)
	{
		this.maxTreapSize = maxTreapSize;
		treapCache = new ExternalMemoryObjectCache<Treap<T>>(storageDirectory, maxInMemoryBytes,
				true);
		Treap<T> root = new Treap<T>();
		treapCache.register(uniqueId + "", root);
		uniqueId++;
	}

	@Override
	public boolean add(T u)
	{
		Treap<T> curTreap = getTreapPartition(u);

		boolean result = curTreap.add(u);
		if (result && curTreap.size() > maxTreapSize)
		{
			T midValue = locateMiddleValue(curTreap);
			partitionRanges.put(midValue, uniqueId + "");
			Treap<T> newTreap = curTreap.split(midValue);
			if (maxTreapSize > 1000)
				System.out.println("Treap Split Performed: " + curTreap.size() + " "
						+ newTreap.size());
			treapCache.register(uniqueId + "", newTreap);
			uniqueId++;
		}

		if (result)
			size++;

		return result;
	}

	private Treap<T> getTreapPartition(T u)
	{
		Entry<T, String> treapId = partitionRanges.floorEntry(u);
		Treap<T> curTreap;
		if (treapId == null)
			curTreap = treapCache.get("0");
		else
			curTreap = treapCache.get(treapId.getValue());
		return curTreap;
	}

	private T locateMiddleValue(Treap<T> treap)
	{
		Node<T> curNode = treap.r;
		int idealSize = treap.r.size / 2;
		int leftSize = 0;
		int rightSize = 0;

		while (true)
		{
			int leftChildSize = 0;
			int rightChildSize = 0;
			if (curNode.left != null)
				leftChildSize = curNode.left.size;
			if (curNode.right != null)
				rightChildSize = curNode.right.size;

			if (leftChildSize == 0 && rightChildSize == 0)
				break;
			else if (leftChildSize == 0)
			{
				curNode = curNode.right;
				leftSize += 1;
			} else if (rightChildSize == 0)
			{
				curNode = curNode.left;
				rightSize += 1;
			} else
			{

				int newLeftSize = leftChildSize + 1 + leftSize;
				int newRightSize = rightChildSize + 1 + rightSize;
				long leftScore = (leftSize > idealSize ? (long) (leftSize - idealSize)
						* (leftSize - idealSize) : idealSize - leftSize)
						+ ((newRightSize > idealSize ? (long) (newRightSize - idealSize)
								* (newRightSize - idealSize) : idealSize - newRightSize));
				long rightScore = (newLeftSize > idealSize ? (long) (newLeftSize - idealSize)
						* (newLeftSize - idealSize) : idealSize - newLeftSize)
						+ ((rightSize > idealSize ? (long) (rightSize - idealSize)
								* (rightSize - idealSize) : idealSize - rightSize));
				if (leftScore < rightScore)
				{
					curNode = curNode.left;
					rightSize = newRightSize;
				} else
				{
					curNode = curNode.right;
					leftSize = newLeftSize;
				}
			}
		}
		return curNode.x;
	}

	@Override
	public boolean remove(T x)
	{
		Treap<T> curTreap = getTreapPartition(x);
		boolean result = curTreap.remove(x);
		if (result)
		{
			size--;
			if (partitionRanges.size() > 0 && curTreap.size() < (maxTreapSize >> 3))
			{
				Entry<T, String> smallEntry = partitionRanges.floorEntry(x);
				Entry<T, String> mergeEntry;
				Treap<T> mergeTreap;
				if (smallEntry == null)
				{
					mergeEntry = partitionRanges.higherEntry(x);
					mergeTreap = treapCache.get(mergeEntry.getValue());
					curTreap.merge(mergeTreap);
					treapCache.unregister(mergeEntry.getValue());
					partitionRanges.remove(mergeEntry.getKey());
				} else
				{
					mergeEntry = partitionRanges.lowerEntry(smallEntry.getKey());
					mergeTreap = treapCache.get(mergeEntry == null ? "0" : mergeEntry.getValue());
					mergeTreap.merge(curTreap);
					treapCache.unregister(smallEntry.getValue());
					partitionRanges.remove(smallEntry.getKey());
				}

				System.out.println("Treap Merge performed: " + curTreap.size() + " "
						+ mergeTreap.size());
			}
		}
		return result;
	}

	@Override
	public boolean contains(T x)
	{
		Treap<T> curTreap = getTreapPartition(x);
		return curTreap.contains(x);
	}

	@Override
	public long size()
	{
		return size;
	}

	private class EMTreapIterator implements Iterator<T>
	{
		private T currentPartitionKey;
		private Treap<T> currentTreap;
		private Treap<T>.BTI currentTreapIter;
		private String endTreapKey;
		private Treap<T> endTreap;

		private T prev;

		private T from;
		private T to;

		public EMTreapIterator(T from, T to)
		{
			this.from = from;
			this.to = to;

			if (from != null)
			{
				currentPartitionKey = from;
				Entry<T, String> startTreapPartition = partitionRanges.floorEntry(from);
				if (startTreapPartition == null)
					currentTreap = treapCache.get("0");
				else
					currentTreap = treapCache.get(startTreapPartition.getValue());
				currentTreapIter = (Treap<T>.BTI) currentTreap.iterator(from, to);
			} else
			{
				currentTreap = treapCache.get("0");
				currentPartitionKey = currentTreap.r.x;
				this.from = currentTreap.r.x;
				currentTreapIter = (Treap<T>.BTI) currentTreap.iterator();
			}

			if (to != null)
			{
				Entry<T, String> endTreapPartition = partitionRanges.floorEntry(to);
				if (endTreapPartition == null)
				{
					endTreap = treapCache.get("0");
					endTreapKey = "0";
				} else
				{
					endTreapKey = endTreapPartition.getValue();
					endTreap = treapCache.get(endTreapKey);
				}
			} else
			{
				Entry<T, String> endTreapPartition = partitionRanges.lastEntry();
				endTreapKey = endTreapPartition.getValue();
				endTreap = treapCache.get(endTreapKey);
			}
		}

		@Override
		public boolean hasNext()
		{
			return currentTreapIter.hasNext();
		}

		@Override
		public T next()
		{
			T result = currentTreapIter.next();
			prev = result;

			if (!currentTreapIter.hasNext() && currentTreap != endTreap)
			{
				Entry<T, String> nextPartition = partitionRanges.higherEntry(currentPartitionKey);
				try
				{
					currentPartitionKey = nextPartition.getKey();
				} catch (NullPointerException e)
				{
					e.printStackTrace();
				}
				currentTreap = treapCache.get(nextPartition.getValue());
				currentTreapIter = (Treap<T>.BTI) currentTreap.iterator(from, to);
				treapCache.get(endTreapKey);
			}

			return result;
		}

		@Override
		public void remove()
		{
			ExternalMemoryTreap.this.remove(prev);
		}

	}

	@Override
	public Iterator<T> iterator()
	{
		return new EMTreapIterator(null, null);
	}

	@Override
	public Iterator<T> iterator(T from, T to)
	{
		if (to == null || from.compareTo(to) < 0)
			return new EMTreapIterator(from, to);
		else
			return new EMTreapIterator(to, from);
	}
}
