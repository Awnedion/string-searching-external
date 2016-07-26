package ods.string.search.partition;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Iterator;

import ods.string.search.partition.splitsets.ExternalizableMemoryObject;
import ods.string.search.partition.splitsets.SplittableSet;
import ods.string.search.partition.splitsets.Treap;

public class ExternalMemorySplittableSet<T extends Comparable<T> & Serializable> implements
		EMPrefixSearchableSet<T>
{

	private static class TreeNode<T extends Comparable<T> & Serializable> implements
			ExternalizableMemoryObject
	{
		private static final long serialVersionUID = -309297143139643805L;

		public int nodeHeight;
		public String nextPartitionId;
		public SplittableSet<T> structure;
		private transient boolean isDirty = true;

		public TreeNode(SplittableSet<T> type, int nodeHeight)
		{
			if (type != null)
				this.structure = (SplittableSet<T>) type.createNewSet();
			this.nodeHeight = nodeHeight;
		}

		@Override
		public long getByteSize()
		{
			// 16 for class, 8 for pointer to structure, 8 for int, 64 for string ID
			long result = 32;
			if (nextPartitionId == null)
				result += 8;
			else
				result += 64 + (nextPartitionId.length() << 1);

			return structure.getByteSize() + result;
		}

		@Override
		public boolean isDirty()
		{
			return structure.isDirty() || isDirty;
		}

		private void readObject(ObjectInputStream inputStream) throws IOException,
				ClassNotFoundException
		{
			inputStream.defaultReadObject();
			isDirty = false;
		}

		public void setNextPartitionId(String nextPartitionId)
		{
			this.nextPartitionId = nextPartitionId;
			isDirty = true;
		}
	}

	private ExternalMemoryObjectCache<TreeNode<T>> setCache;
	private int maxSetSize = 100000;
	private long size = 0;
	private int treeHeight = 1;

	public ExternalMemorySplittableSet(File storageDirectory)
	{
		setCache = new ExternalMemoryObjectCache<TreeNode<T>>(storageDirectory, 100000000, true);
		TreeNode<T> root = new TreeNode<T>(new Treap<T>(), treeHeight);
		setCache.register("-1", root);
	}

	public ExternalMemorySplittableSet(File storageDirectory, int maxSetSize,
			long maxInMemoryBytes, SplittableSet<T> root)
	{
		this.maxSetSize = maxSetSize;
		setCache = new ExternalMemoryObjectCache<TreeNode<T>>(storageDirectory, maxInMemoryBytes,
				true);
		TreeNode<T> rootNode = new TreeNode<T>(root, treeHeight);
		setCache.register("-1", rootNode);
	}

	public ExternalMemorySplittableSet(File storageDirectory,
			ExternalMemorySplittableSet<T> baseConfig)
	{
		this.maxSetSize = baseConfig.maxSetSize;
		setCache = new ExternalMemoryObjectCache<TreeNode<T>>(storageDirectory, baseConfig.setCache);
		SplittableSet<T> root = baseConfig.setCache.get("-1").structure.createNewSet();
		TreeNode<T> rootNode = new TreeNode<T>(root, treeHeight);
		setCache.register("-1", rootNode);
	}

	@Override
	public boolean add(T u)
	{
		String[] searchPath = getLeafNodeForElem(u);
		TreeNode<T> leafNode = setCache.get(searchPath[0]);

		boolean result = leafNode.structure.add(u);
		if (result)
			splitNodeIfNecessary(searchPath, leafNode);

		if (result)
			size++;

		return result;
	}

	private boolean splitNodeIfNecessary(String[] searchPath, TreeNode<T> leafNode)
	{
		boolean hasSplit = false;
		if (leafNode.structure.size() > maxSetSize)
		{
			hasSplit = true;

			T midValue = leafNode.structure.locateMiddleValue();
			String newNodeId = midValue + "-" + leafNode.nodeHeight;
			TreeNode<T> newNode = new TreeNode<T>(null, leafNode.nodeHeight);
			newNode.structure = leafNode.structure.split(midValue);
			if (newNode.nodeHeight == 1)
			{
				newNode.setNextPartitionId(leafNode.nextPartitionId);
				leafNode.setNextPartitionId(midValue + "-" + leafNode.nodeHeight);
			}
			setCache.register(newNodeId, newNode);

			TreeNode<T> parentNode = null;
			if (leafNode.nodeHeight == searchPath.length)
			{
				treeHeight++;
				parentNode = new TreeNode<T>(leafNode.structure, treeHeight);
				setCache.register("-" + treeHeight, parentNode);
			} else
				parentNode = setCache.get(searchPath[leafNode.nodeHeight]);
			parentNode.structure.add(midValue);

			if (parentNode.nodeHeight <= searchPath.length)
				splitNodeIfNecessary(searchPath, parentNode);
		}

		return hasSplit;
	}

	private String[] getLeafNodeForElem(T u)
	{
		String[] searchPath = new String[treeHeight];
		String nextNodeId = "-" + treeHeight;
		TreeNode<T> curNode = setCache.get(nextNodeId);
		searchPath[treeHeight - 1] = nextNodeId;
		while (curNode.nodeHeight > 1)
		{
			T branchingValue = curNode.structure.floor(u);
			int nextHeight = curNode.nodeHeight - 1;
			if (branchingValue == null)
				nextNodeId = "-" + nextHeight;
			else
				nextNodeId = branchingValue + "-" + nextHeight;
			curNode = setCache.get(nextNodeId);
			searchPath[curNode.nodeHeight - 1] = nextNodeId;
		}
		return searchPath;
	}

	@Override
	public boolean remove(T x)
	{
		String[] searchPath = getLeafNodeForElem(x);
		TreeNode<T> leafNode = setCache.get(searchPath[0]);

		boolean result = leafNode.structure.remove(x);
		if (result)
		{
			size--;
			mergeNodeIfNecessary(x, searchPath, leafNode);
		}
		return result;
	}

	private void mergeNodeIfNecessary(T x, String[] searchPath, TreeNode<T> curNode)
	{
		if (treeHeight > curNode.nodeHeight && curNode.structure.size() < (maxSetSize >> 3))
		{
			TreeNode<T> parentNode = setCache.get(searchPath[curNode.nodeHeight]);
			T deleteBranch = parentNode.structure.floor(x);
			T leftBranch = null;
			T rightBranch = null;
			if (deleteBranch == null)
			{
				rightBranch = parentNode.structure.iterator().next();
			} else
			{
				leftBranch = parentNode.structure.lower(deleteBranch);
				rightBranch = parentNode.structure.higher(deleteBranch);
			}

			TreeNode<T> smallerNode = null;
			TreeNode<T> biggerNode = null;
			if (leftBranch == null && rightBranch == null)
			{
				smallerNode = setCache.get("-" + curNode.nodeHeight);
				biggerNode = curNode;
			} else if (leftBranch == null)
			{
				smallerNode = curNode;
				biggerNode = setCache.get(rightBranch + "-" + curNode.nodeHeight);
				deleteBranch = rightBranch;
			} else if (rightBranch == null)
			{
				smallerNode = setCache.get(leftBranch + "-" + curNode.nodeHeight);
				biggerNode = curNode;
			} else
			{
				TreeNode<T> leftSet = setCache.get(leftBranch + "-" + curNode.nodeHeight);
				TreeNode<T> rightSet = setCache.get(rightBranch + "-" + curNode.nodeHeight);
				if (leftSet.structure.size() < rightSet.structure.size())
				{
					smallerNode = leftSet;
					biggerNode = curNode;
				} else
				{
					smallerNode = curNode;
					biggerNode = rightSet;
					deleteBranch = rightBranch;
				}
			}

			parentNode.structure.remove(deleteBranch);
			smallerNode.structure.merge(biggerNode.structure);
			smallerNode.setNextPartitionId(biggerNode.nextPartitionId);
			setCache.unregister(deleteBranch + "-" + smallerNode.nodeHeight);

			if (!splitNodeIfNecessary(searchPath, smallerNode))
			{
				if (parentNode.structure.size() == 0)
				{
					if (parentNode.nodeHeight != treeHeight)
						throw new RuntimeException("Trying to delete non-root parent.");

					setCache.unregister("-" + treeHeight);
					treeHeight--;
				} else
					mergeNodeIfNecessary(x, searchPath, parentNode);
			}
		}
	}

	@Override
	public boolean contains(T x)
	{
		String[] searchPath = getLeafNodeForElem(x);
		TreeNode<T> leafNode = setCache.get(searchPath[0]);
		return leafNode.structure.contains(x);
	}

	@Override
	public long size()
	{
		return size;
	}

	private class EMSetIterator implements Iterator<T>
	{
		private TreeNode<T> curNode;
		private Iterator<T> currentSetIter;

		private T prev;

		private T to;

		public EMSetIterator(T from, T to)
		{
			this.prev = from;
			this.to = to;

			if (from != null)
			{
				String[] searchPath = getLeafNodeForElem(from);
				curNode = setCache.get(searchPath[0]);
				currentSetIter = curNode.structure.iterator(from, to);
			} else
			{
				curNode = setCache.get("-1");
				currentSetIter = curNode.structure.iterator();
			}
		}

		@Override
		public boolean hasNext()
		{
			if (!currentSetIter.hasNext() && (to == null || prev.compareTo(to) < 0))
			{
				if (curNode.nextPartitionId != null)
				{
					curNode = setCache.get(curNode.nextPartitionId);
					currentSetIter = curNode.structure.iterator(prev, to);
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
