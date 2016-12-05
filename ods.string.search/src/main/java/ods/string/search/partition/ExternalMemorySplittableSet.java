package ods.string.search.partition;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Iterator;

import ods.string.search.partition.ExternalMemoryObjectCache.CompressType;
import ods.string.search.partition.splitsets.ExternalizableMemoryObject;
import ods.string.search.partition.splitsets.SplittableSet;
import ods.string.search.partition.splitsets.Treap;

/**
 * This class represents a B+ Tree that also makes use of caching to disk to increase storage
 * limits. The leaf nodes have pointers to next leaf nodes to speed up range searches.
 */
public class ExternalMemorySplittableSet<T extends Comparable<T> & Serializable> implements
		EMPrefixSearchableSet<T>
{

	/**
	 * A node in the B+ Tree knows it's height to assist in retrieving block IDs for child IDs. Leaf
	 * nodes know the IDs to the next leaf node.
	 */
	private static class TreeNode<T extends Comparable<T> & Serializable> implements
			ExternalizableMemoryObject
	{
		private static final long serialVersionUID = -309297143139643805L;

		/**
		 * The node of this node in the tree, where 1 is a leaf node. QQQ
		 */
		public int nodeHeight;

		/**
		 * The block ID of the next leaf node. This is null if this node isn't a leaf node.
		 */
		public String nextPartitionId;

		/**
		 * The data stored in this node.
		 */
		public SplittableSet<T> structure;

		/**
		 * Used when flushing to disk to determine if the data need to be rewritten.
		 */
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
			// A freshly deserialized object isn't dirty.
			inputStream.defaultReadObject();
			isDirty = false;
		}

		public void setNextPartitionId(String nextPartitionId)
		{
			this.nextPartitionId = nextPartitionId;
			isDirty = true;
		}
	}

	/**
	 * Stores all the nodes of the tree where a node's ID is '<minValueInNode>-<nodeHeight>'.
	 */
	private ExternalMemoryObjectCache<TreeNode<T>> setCache;

	/**
	 * The maximum number of elements to store in a node before splitting it.
	 */
	private int maxSetSize = 100000;

	/**
	 * The number of elements stored in the tree.
	 */
	private long size = 0;

	/**
	 * The maximum node height in this tree.
	 */
	private int treeHeight = 1;

	public ExternalMemorySplittableSet(File storageDirectory)
	{
		setCache = new ExternalMemoryObjectCache<TreeNode<T>>(storageDirectory, 100000000,
				CompressType.SNAPPY);
		TreeNode<T> root = new TreeNode<T>(new Treap<T>(), treeHeight);
		setCache.register("-1", root);
	}

	public ExternalMemorySplittableSet(File storageDirectory, int maxSetSize,
			long maxInMemoryBytes, SplittableSet<T> root)
	{
		this.maxSetSize = maxSetSize;
		setCache = new ExternalMemoryObjectCache<TreeNode<T>>(storageDirectory, maxInMemoryBytes,
				CompressType.SNAPPY);
		TreeNode<T> rootNode = new TreeNode<T>(root, treeHeight);
		setCache.register("-1", rootNode);
	}

	@SuppressWarnings("unchecked")
	public ExternalMemorySplittableSet(ExternalMemoryObjectCache<?> objectCache, int maxSetSize,
			SplittableSet<T> root)
	{
		this.maxSetSize = maxSetSize;
		setCache = (ExternalMemoryObjectCache<TreeNode<T>>) objectCache;
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

	/**
	 * {@inheritDoc}
	 */
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

	/**
	 * Splits the specified node into two if it is larger than the maximum node size.
	 * 
	 * @param searchPath
	 *            The search path used to find the specified node.
	 * @param leafNode
	 *            The node that may need to be split.
	 * @return True if the node was split, false otherwise.
	 */
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

			// The new node is a leaf node, so update sibling ID pointers.
			if (newNode.nodeHeight == 1)
			{
				newNode.setNextPartitionId(leafNode.nextPartitionId);
				leafNode.setNextPartitionId(midValue + "-" + leafNode.nodeHeight);
			}
			setCache.register(newNodeId, newNode);

			TreeNode<T> parentNode = null;
			if (leafNode.nodeHeight == searchPath.length)
			{
				// The root node was split, so create a new root node.
				treeHeight++;
				parentNode = new TreeNode<T>(leafNode.structure, treeHeight);
				setCache.register("-" + treeHeight, parentNode);
			} else
				parentNode = setCache.get(searchPath[leafNode.nodeHeight]);
			parentNode.structure.add(midValue);

			// The parent node may need to be split so check it as well.
			if (parentNode.nodeHeight <= searchPath.length)
				splitNodeIfNecessary(searchPath, parentNode);
		}

		return hasSplit;
	}

	/**
	 * Returns the search path to the leaf node that the specified element exists in or would be
	 * placed in if inserted. The search path is an array of IDs where index 0 is the leaf node.
	 */
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

	/**
	 * {@inheritDoc}
	 */
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

	/**
	 * Merges the specified node into one of it's sibling nodes if it is smaller than the minimum
	 * node size (maxSize / 4). The sibling with the smaller node size will be chosen for the merge.
	 * 
	 * @param x
	 *            The element that has just been deleted.
	 * @param searchPath
	 *            The search path used to find the specified node.
	 * @param curNode
	 *            The node that may need to be merged.
	 */
	private void mergeNodeIfNecessary(T x, String[] searchPath, TreeNode<T> curNode)
	{
		if (treeHeight > curNode.nodeHeight && curNode.structure.size() < (maxSetSize >> 3))
		{
			TreeNode<T> parentNode = setCache.get(searchPath[curNode.nodeHeight]);
			T deleteBranch = parentNode.structure.floor(x);
			T leftBranch = null;
			T rightBranch = null;

			// Find the sibling to the node that is too smaller.
			if (deleteBranch == null)
			{
				rightBranch = parentNode.structure.iterator().next();
			} else
			{
				leftBranch = parentNode.structure.lower(deleteBranch);
				rightBranch = parentNode.structure.higher(deleteBranch);
			}

			// Figure out which sibling to merge into. The smaller sibling will be chosen.
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

			// The merged node may now be too big, see if it should now be split.
			if (!splitNodeIfNecessary(searchPath, smallerNode))
			{
				if (parentNode.structure.size() == 0)
				{
					/*
					 * If the parent node is now empty, the maximum tree height needs to be lowered
					 * to update the root node.
					 */
					if (parentNode.nodeHeight != treeHeight)
						throw new RuntimeException("Trying to delete non-root parent.");

					setCache.unregister("-" + treeHeight);
					treeHeight--;
				} else
					// The parent node may now need to be merged as well.
					mergeNodeIfNecessary(x, searchPath, parentNode);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean contains(T x)
	{
		String[] searchPath = getLeafNodeForElem(x);
		TreeNode<T> leafNode = setCache.get(searchPath[0]);
		return leafNode.structure.contains(x);
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
	 * This class keeps track of a range search iterator over the tree.
	 */
	private class EMSetIterator implements Iterator<T>
	{
		/**
		 * The current leaf node being iterated over.
		 */
		private TreeNode<T> curNode;

		/**
		 * The current node iterator being used to pull elements.
		 */
		private Iterator<T> currentSetIter;

		/**
		 * The next element that a call to next() should return.
		 */
		private T nextElem;

		/**
		 * The end range of elements to return (exclusive). null means iterate over all remaining
		 * elements.
		 */
		private T to;

		public EMSetIterator(T from, T to)
		{
			this.to = to;

			if (from != null)
			{
				String[] searchPath = getLeafNodeForElem(from);
				curNode = setCache.get(searchPath[0]);
				currentSetIter = curNode.structure.iterator(from, null);
			} else
			{
				curNode = setCache.get("-1");
				currentSetIter = curNode.structure.iterator();
			}
		}

		@Override
		public boolean hasNext()
		{
			if (nextElem == null)
			{
				// If the current iterator is empty, the next leaf node needs to be iterated over.
				if (!currentSetIter.hasNext())
				{
					if (curNode.nextPartitionId != null)
					{
						curNode = setCache.get(curNode.nextPartitionId);
						currentSetIter = curNode.structure.iterator();
					}
				}

				// Get the next element ready if one exists.
				if (currentSetIter.hasNext())
					nextElem = currentSetIter.next();
			}

			return nextElem != null && (to == null || nextElem.compareTo(to) < 0);
		}

		@Override
		public T next()
		{
			T result = nextElem;
			nextElem = null;

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
		return new EMSetIterator(null, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Iterator<T> iterator(T from, T to)
	{
		if (to == null || from.compareTo(to) < 0)
			return new EMSetIterator(from, to);
		else
			return new EMSetIterator(to, from);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close()
	{
		setCache.close();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EMPrefixSearchableSet<T> createNewStructure(File newStorageDir)
	{
		return new ExternalMemorySplittableSet<T>(newStorageDir, this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ExternalMemoryObjectCache<? extends ExternalizableMemoryObject> getObjectCache()
	{
		return setCache;
	}
}
