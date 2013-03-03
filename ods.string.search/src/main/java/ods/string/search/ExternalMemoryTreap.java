package ods.string.search;

import java.io.File;
import java.io.Serializable;
import java.util.Map.Entry;
import java.util.TreeMap;

import ods.string.search.Treap.Node;

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
			System.out.println("Treap Split Performed: " + curTreap.size() + " " + newTreap.size());
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
			size--;
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
}
