package ods.string.search;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;

public class CentroidTree
{
	private int maxStringLength = 100;

	private ExternalMemoryCache<CentroidTreeNode> array;
	private CentroidTreeNode emptyNode = new CentroidTreeNode(maxStringLength);
	private long size = 0l;
	private File treeDirectory;
	private BstTreeIndexLayout indexLayout = new BasicIndexLayout();
	private long cacheSize = 200000000;

	public CentroidTree(File treeDirectory)
	{
		this.treeDirectory = treeDirectory;
		array = new ExternalMemoryCache<CentroidTreeNode>(treeDirectory, 200000000, 10000, true);
	}

	public CentroidTree(File treeDirectory, long cacheSize, int maxStringSize,
			BstTreeIndexLayout indexLayout, boolean compress)
	{
		this.indexLayout = indexLayout;
		this.treeDirectory = treeDirectory;
		this.cacheSize = cacheSize;
		array = new ExternalMemoryCache<CentroidTreeNode>(treeDirectory, cacheSize, 10000, compress);
		maxStringLength = maxStringSize;
		emptyNode = new CentroidTreeNode(maxStringLength);
	}

	public boolean insert(String value)
	{
		CentroidTreeNode node = new CentroidTreeNode(maxStringLength);
		node.setString(value);
		node.setValueEnd(true);
		node.setSubtreeSize(1);
		long insertIndex = findIndex(node, 0);
		if (insertIndex == 0)
		{
			insertAt(0, node);
		} else
		{
			int newNodes = 1;
			CentroidTreeNode parentNode = new CentroidTreeNode(maxStringLength);
			long parentIndex = indexLayout.getParentIndex(insertIndex);
			array.get(parentIndex, parentNode);
			if (node.getStringBitLength() == 0)
			{
				if (parentNode.isValueEnd())
					return false;
				parentNode.setValueEnd(true);
				array.set(parentIndex, parentNode);
				newNodes = 0;
			} else if (insertIndex == indexLayout.getLeftChildIndex(parentIndex))
			{
				CentroidTreeNode.splitOnPrefix(parentNode, node);
				if (parentNode.getStringBitLength() == 0)
				{
					insertAt(insertIndex, node);
				} else if (node.getStringBitLength() == 0)
				{
					parentNode.setValueEnd(true);
					parentNode.setSubtreeSize(1);
					insertAt(insertIndex, parentNode);
				} else
				{
					parentNode.setSubtreeSize(2);
					parentNode.setValueEnd(false);
					insertAt(insertIndex, parentNode);
					insertAt(indexLayout.getRightChildIndex(insertIndex), node);
					newNodes = 2;
				}
			} else
			{
				insertAt(insertIndex, node);
			}

			if (newNodes > 0)
			{
				long unbalancedIndex = -1;
				while (parentIndex >= 0)
				{
					array.get(parentIndex, parentNode);
					if (!isNodeBalanced(parentIndex))
						unbalancedIndex = parentIndex;
					parentNode.setSubtreeSize(parentNode.getSubtreeSize() + newNodes);
					array.set(parentIndex, parentNode);
					parentIndex = indexLayout.getParentIndex(parentIndex);
				}
				if (unbalancedIndex != -1)
					rebalanceSubTree(unbalancedIndex);
			}
		}

		size++;
		return true;
	}

	private void insertAt(long insertIndex, CentroidTreeNode node)
	{
		array.set(insertIndex, node);
		array.set(indexLayout.getLeftChildIndex(insertIndex), emptyNode);
		array.set(indexLayout.getRightChildIndex(insertIndex), emptyNode);
	}

	private void insertStartingFrom(CentroidTreeNode node, long startIndex)
	{
		if (node.getStringBitLength() == 0)
			return;

		node.setSubtreeSize(1);
		long insertIndex = findIndex(node, startIndex);
		insertAt(insertIndex, node);
		CentroidTreeNode parentNode = new CentroidTreeNode(maxStringLength);
		long parentIndex = indexLayout.getParentIndex(insertIndex);

		while (parentIndex >= startIndex)
		{
			array.get(parentIndex, parentNode);
			parentNode.setSubtreeSize(parentNode.getSubtreeSize() + 1);
			array.set(parentIndex, parentNode);
			parentIndex = indexLayout.getParentIndex(parentIndex);
		}
	}

	public boolean contains(String value)
	{
		CentroidTreeNode node = new CentroidTreeNode(maxStringLength);
		node.setString(value);
		long index = findIndex(node, 0);
		if (node.getStringBitLength() != 0)
			return false;

		array.get(indexLayout.getParentIndex(index), node);
		return node.isValueEnd();
	}

	private long findIndex(CentroidTreeNode matchNode, long startIndex)
	{
		long index = startIndex;
		CentroidTreeNode curNode = new CentroidTreeNode(maxStringLength);
		while (true)
		{
			array.get(index, curNode);
			int commonBits = CentroidTreeNode.getCommonPrefixBits(curNode, matchNode);
			if (curNode.getStringBitLength() == 0 || matchNode.getStringBitLength() == 0)
			{
				break;
			} else if (commonBits == curNode.getStringBitLength())
			{
				CentroidTreeNode.splitOnPrefix(curNode, matchNode);
				index = indexLayout.getRightChildIndex(index);
			} else
			{
				index = indexLayout.getLeftChildIndex(index);
			}
		}
		return index;
	}

	public boolean remove(String value)
	{
		CentroidTreeNode node = new CentroidTreeNode(maxStringLength);
		node.setString(value);
		long insertIndex = findIndex(node, 0);

		if (node.getStringBitLength() == 0)
		{
			insertIndex = indexLayout.getParentIndex(insertIndex);
			if (insertIndex < 0)
				return false;
			array.get(insertIndex, node);
			if (node.isValueEnd())
			{
				node.setValueEnd(false);
				size--;
				array.set(insertIndex, node);
				return true;
			}
		}
		return false;
	}

	public long size()
	{
		return size;
	}

	private boolean isNodeBalanced(long nodeIndex)
	{
		CentroidTreeNode node = new CentroidTreeNode(maxStringLength);
		array.get(indexLayout.getLeftChildIndex(nodeIndex), node);
		long leftSize = node.getSubtreeSize() + 1;
		array.get(indexLayout.getRightChildIndex(nodeIndex), node);
		long rightSize = node.getSubtreeSize() + 1;
		double ratio = (double) leftSize / rightSize;
		return ratio >= 0.2 && ratio <= 5;
	}

	private void rebalanceSubTree(long startIndex)
	{
		Utils.deleteRecursively(new File(treeDirectory, "tourArray"));
		ExternalMemoryCache<CentroidTreeNode> tourArray = new ExternalMemoryCache<CentroidTreeNode>(
				new File(treeDirectory, "tourArray"), cacheSize);
		CentroidTreeNode node = new CentroidTreeNode(maxStringLength);

		copySubtreeAsTrie(startIndex, tourArray);

		array.get(indexLayout.getLeftChildIndex(startIndex), node);
		long startLeft = node.getSubtreeSize();
		array.get(indexLayout.getRightChildIndex(startIndex), node);
		long startRight = node.getSubtreeSize();
		System.out.println("Before rebalance: left: " + startLeft + " right: " + startRight);

		insertAt(startIndex, emptyNode);
		centroidSplit(tourArray, node, startIndex);

		array.get(indexLayout.getLeftChildIndex(startIndex), node);
		startLeft = node.getSubtreeSize();
		array.get(indexLayout.getRightChildIndex(startIndex), node);
		startRight = node.getSubtreeSize();
		System.out.println("After rebalance: left: " + startLeft + " right: " + startRight);
	}

	private class UnexpandedNode
	{
		public long index;
		public CentroidTreeNode node;

		public UnexpandedNode(long index, CentroidTreeNode node)
		{
			this.index = index;
			this.node = node;
		}
	}

	private void copySubtreeAsTrie(long treeIndex, ExternalMemoryCache<CentroidTreeNode> resultArray)
	{
		PriorityQueue<CentroidTreeNode> trieNodes = new PriorityQueue<CentroidTreeNode>(10,
				new CentroidTreeNode.StringLengthComparator());
		PriorityQueue<UnexpandedNode> unexpandedNodes = new PriorityQueue<UnexpandedNode>(10,
				new Comparator<UnexpandedNode>()
				{
					@Override
					public int compare(UnexpandedNode o1, UnexpandedNode o2)
					{
						if (o1.node.getStringBitLength() < o2.node.getStringBitLength())
							return -1;
						else if (o2.node.getStringBitLength() < o1.node.getStringBitLength())
							return 1;
						return 0;
					}
				});
		unexpandedNodes.add(new UnexpandedNode(treeIndex, new CentroidTreeNode(maxStringLength)));

		int lastInsertBitLengths = 0;
		while (!unexpandedNodes.isEmpty())
		{
			UnexpandedNode unNode = unexpandedNodes.remove();
			if (unNode.node.getStringBitLength() > lastInsertBitLengths)
			{
				lastInsertBitLengths = unNode.node.getStringBitLength();
				prepareNodes(resultArray, trieNodes, lastInsertBitLengths);
			}

			long currentIndex = unNode.index;
			while (true)
			{
				CentroidTreeNode node = new CentroidTreeNode(maxStringLength);
				array.get(currentIndex, node);
				if (node.getSubtreeSize() == 0)
					break;

				CentroidTreeNode newPrefix = new CentroidTreeNode(unNode.node);
				CentroidTreeNode.appendOnNode(newPrefix, node);
				node.copyString(newPrefix);
				trieNodes.add(node);
				unexpandedNodes.add(new UnexpandedNode(
						indexLayout.getRightChildIndex(currentIndex), newPrefix));
				currentIndex = indexLayout.getLeftChildIndex(currentIndex);
			}
		}

		prepareNodes(resultArray, trieNodes, Integer.MAX_VALUE);
	}

	private void prepareNodes(ExternalMemoryCache<CentroidTreeNode> resultArray,
			PriorityQueue<CentroidTreeNode> gatheredVals, int maxBitLength)
	{
		HashSet<CentroidTreeNode> trieNodes = new HashSet<CentroidTreeNode>();
		for (Iterator<CentroidTreeNode> iter = gatheredVals.iterator(); iter.hasNext();)
		{
			CentroidTreeNode n = iter.next();
			if (n.getStringBitLength() > maxBitLength)
				continue;

			if (n.isValueEnd())
				n.setSubtreeSize(1);
			else
				n.setSubtreeSize(0);
			trieNodes.add(n);
		}

		CentroidTreeNode prefixNode = new CentroidTreeNode(maxStringLength);
		CentroidTreeNode postfixNode = new CentroidTreeNode(maxStringLength);
		while (!gatheredVals.isEmpty() && gatheredVals.peek().getStringBitLength() <= maxBitLength)
		{
			CentroidTreeNode node1 = gatheredVals.remove();
			for (Iterator<CentroidTreeNode> iter = gatheredVals.iterator(); iter.hasNext();)
			{
				CentroidTreeNode node2 = iter.next();
				prefixNode.copyString(node1);
				postfixNode.copyString(node2);
				CentroidTreeNode.splitOnPrefix(prefixNode, postfixNode);
				if (prefixNode.getStringBitLength() < node1.getStringBitLength()
						&& prefixNode.getStringBitLength() < node2.getStringBitLength())
				{
					prefixNode.setSubtreeSize(1);
					if (trieNodes.add(prefixNode))
						prefixNode = new CentroidTreeNode(maxStringLength);
				}
			}
		}

		for (Iterator<CentroidTreeNode> iter = trieNodes.iterator(); iter.hasNext();)
		{
			CentroidTreeNode n = iter.next();
			if (n.getStringBitLength() > maxBitLength || n.getSubtreeSize() == 0)
			{
				iter.remove();
			}
		}

		CentroidTreeNode[] trieNodeArray = trieNodes.toArray(new CentroidTreeNode[] {});
		Arrays.sort(trieNodeArray, new CentroidTreeNode.StringLengthComparator());

		for (CentroidTreeNode n : trieNodeArray)
			insertTrieStyle(resultArray, n);
	}

	private void insertTrieStyle(ExternalMemoryCache<CentroidTreeNode> resultArray,
			CentroidTreeNode value)
	{
		CentroidTreeNode node = new CentroidTreeNode(maxStringLength);
		long insertIndex = 0l;
		while (true)
		{
			resultArray.get(insertIndex, node);
			if (node.getSubtreeSize() == 0)
				break;

			int commonBits = CentroidTreeNode.getCommonPrefixBits(value, node);
			if (commonBits != node.getStringBitLength())
				System.out.println("yikes");
			else if (commonBits == value.getStringBitLength())
				return;

			short mask = (short) (128 >> (node.getStringBitLength() % 8));
			if ((value.getBytes().get(node.getStringBitLength() / 8) & mask) == 0)
			{
				insertIndex = indexLayout.getLeftChildIndex(insertIndex);
			} else
			{
				insertIndex = indexLayout.getRightChildIndex(insertIndex);
			}
		}

		value.setSubtreeSize(1);
		resultArray.set(insertIndex, value);
		// System.out.println(insertIndex + " " + value);

		while ((insertIndex = indexLayout.getParentIndex(insertIndex)) >= 0)
		{
			resultArray.get(insertIndex, node);
			node.setSubtreeSize(node.getSubtreeSize() + 1);
			resultArray.set(insertIndex, node);
		}
	}

	private void copySubtree(long treeIndex, long insertionIndex,
			ExternalMemoryCache<CentroidTreeNode> resultArray, CentroidTreeNode currentPrefix)
	{
		CentroidTreeNode node = new CentroidTreeNode(maxStringLength);
		array.get(treeIndex, node);
		if (node.getSubtreeSize() == 0)
			return;

		CentroidTreeNode newPrefix = new CentroidTreeNode(currentPrefix);
		CentroidTreeNode.appendOnNode(newPrefix, node);
		node.copyString(newPrefix);
		resultArray.set(insertionIndex, node);
		if (node.getSubtreeSize() > 1)
		{
			copySubtree(indexLayout.getLeftChildIndex(treeIndex),
					indexLayout.getLeftChildIndex(insertionIndex), resultArray, currentPrefix);
			copySubtree(indexLayout.getRightChildIndex(treeIndex),
					indexLayout.getRightChildIndex(insertionIndex), resultArray, newPrefix);
		}
	}

	private void centroidSplit(ExternalMemoryCache<CentroidTreeNode> data, CentroidTreeNode node,
			long insertionIndex)
	{
		ArrayDeque<Long> startIndices = new ArrayDeque<Long>();
		ArrayDeque<Long> totalSizes = new ArrayDeque<Long>();

		data.get(0, node);
		if (node.getStringBitLength() == 0)
		{
			node.setSubtreeSize(node.getSubtreeSize() - 1);
			data.set(0, node);
		}

		startIndices.add(0l);
		totalSizes.add(node.getSubtreeSize());

		long totalSize;
		while (startIndices.size() > 0)
		{
			long checkIndex = startIndices.remove();
			long parentSize;
			long leftSize;
			long leftIndex;
			long rightSize;
			long rightIndex;
			totalSize = totalSizes.remove();

			while (true)
			{
				data.get(checkIndex, node);
				if (node.getSubtreeSize() == -1)
				{
					checkIndex = indexLayout.getParentIndex(checkIndex);
					continue;
				}
				parentSize = totalSize - node.getSubtreeSize();
				leftIndex = indexLayout.getLeftChildIndex(checkIndex);
				data.get(leftIndex, node);
				leftSize = Math.max(node.getSubtreeSize(), 0);
				rightIndex = indexLayout.getRightChildIndex(checkIndex);
				data.get(rightIndex, node);
				rightSize = Math.max(node.getSubtreeSize(), 0);

				long moveIndex = -1;
				if (parentSize > (rightSize + leftSize + 1) * 2)
				{
					moveIndex = indexLayout.getParentIndex(checkIndex);
				} else if (parentSize < (rightSize + leftSize) / 2.)
				{
					if (rightSize > leftSize)
						moveIndex = rightIndex;
					else
						moveIndex = leftIndex;
				}

				if (moveIndex == -1)
				{
					break;
				}
				checkIndex = moveIndex;
			}

			data.get(checkIndex, node);
			insertStartingFrom(node, insertionIndex);

			node.setSubtreeSize(-1);
			data.set(checkIndex, node);

			if (parentSize > 0)
			{
				long updateIndex = checkIndex;
				while ((updateIndex = indexLayout.getParentIndex(updateIndex)) >= 0)
				{
					data.get(updateIndex, node);
					if (node.getSubtreeSize() == -1)
					{
						break;
					}
					node.setSubtreeSize(node.getSubtreeSize() - 1 - rightSize - leftSize);
					data.set(updateIndex, node);
				}
			}

			if (parentSize > 0)
			{
				startIndices.add(indexLayout.getParentIndex(checkIndex));
				totalSizes.add(parentSize);
			}
			if (leftSize > 0)
			{
				startIndices.add(leftIndex);
				totalSizes.add(leftSize);
			}

			if (rightSize > 0)
			{
				startIndices.add(rightIndex);
				totalSizes.add(rightSize);
			}
		}
	}

	public String toString()
	{
		String result = "";
		ArrayDeque<Long> indices = new ArrayDeque<Long>();
		CentroidTreeNode node = new CentroidTreeNode(maxStringLength);
		ExternalMemoryCache<CentroidTreeNode> copy = new ExternalMemoryCache<CentroidTreeNode>(
				new File(treeDirectory, "toStringArray"), 200000000);
		copySubtree(0, 0, copy, node);
		indices.add(0l);

		while (!indices.isEmpty())
		{
			long index = indices.removeLast();
			copy.get(index, node);
			if (node.getSubtreeSize() == 0)
				continue;
			if (node.isValueEnd())
				result += node.toString() + ", ";
			if (node.getSubtreeSize() > 1)
			{
				indices.add(indexLayout.getLeftChildIndex(index));
				indices.add(indexLayout.getRightChildIndex(index));
			}
		}
		return result;
	}
}
