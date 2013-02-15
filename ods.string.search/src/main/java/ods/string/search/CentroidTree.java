package ods.string.search;

import java.io.File;
import java.util.ArrayDeque;

public class CentroidTree
{
	private ExternalMemoryCache<CentroidTreeNode> array;
	private CentroidTreeNode emptyNode = new CentroidTreeNode(100);
	private long size = 0l;
	private File treeDirectory;
	private BstTreeIndexLayout indexLayout = new VebIndexLayout();

	public CentroidTree(File treeDirectory)
	{
		this.treeDirectory = treeDirectory;
		array = new ExternalMemoryCache<CentroidTreeNode>(treeDirectory, 200000000);
	}

	public boolean insert(String value)
	{
		CentroidTreeNode node = new CentroidTreeNode(100);
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
			CentroidTreeNode parentNode = new CentroidTreeNode(100);
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
		node.setSubtreeSize(1);
		long insertIndex = findIndex(node, startIndex);
		insertAt(insertIndex, node);
		CentroidTreeNode parentNode = new CentroidTreeNode(100);
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
		CentroidTreeNode node = new CentroidTreeNode(100);
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
		CentroidTreeNode curNode = new CentroidTreeNode(100);
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
		CentroidTreeNode node = new CentroidTreeNode(100);
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
		CentroidTreeNode node = new CentroidTreeNode(100);
		array.get(indexLayout.getLeftChildIndex(nodeIndex), node);
		long leftSize = node.getSubtreeSize() + 1;
		array.get(indexLayout.getRightChildIndex(nodeIndex), node);
		long rightSize = node.getSubtreeSize() + 1;
		double ratio = (double) leftSize / rightSize;
		return ratio >= 0.1 && ratio <= 10;
	}

	private void rebalanceSubTree(long startIndex)
	{
		ExternalMemoryCache<CentroidTreeNode> tourArray = new ExternalMemoryCache<CentroidTreeNode>(
				new File(treeDirectory, "tourArray"), 10000000);
		CentroidTreeNode node = new CentroidTreeNode(100);
		copySubtree(startIndex, 0, tourArray, node);

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

	private void copySubtree(long treeIndex, long insertionIndex,
			ExternalMemoryCache<CentroidTreeNode> resultArray, CentroidTreeNode currentPrefix)
	{
		CentroidTreeNode node = new CentroidTreeNode(100);
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
			boolean rightParent;
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
				leftSize = node.getSubtreeSize();
				rightIndex = indexLayout.getRightChildIndex(checkIndex);
				data.get(rightIndex, node);
				rightSize = node.getSubtreeSize();
				rightParent = (indexLayout.getRightChildIndex(indexLayout
						.getParentIndex(checkIndex)) == checkIndex);

				while (leftSize == -1 && parentSize + 1 < totalSize)
				{
					boolean rightParentz = (indexLayout.getRightChildIndex(indexLayout
							.getParentIndex(leftIndex)) == leftIndex);
					if (rightParentz)
						leftIndex = indexLayout.getLeftChildIndex(leftIndex);
					else
						leftIndex = indexLayout.getRightChildIndex(leftIndex);
					data.get(leftIndex, node);
					leftSize = node.getSubtreeSize();
				}
				if (leftSize == -1)
				{
					leftSize = 0;
				}

				while (rightSize == -1 && parentSize + leftSize + 1 < totalSize)
				{
					boolean rightParentz = (indexLayout.getRightChildIndex(indexLayout
							.getParentIndex(rightIndex)) == rightIndex);
					if (rightParentz)
						rightIndex = indexLayout.getLeftChildIndex(rightIndex);
					else
						rightIndex = indexLayout.getRightChildIndex(rightIndex);
					data.get(rightIndex, node);
					rightSize = node.getSubtreeSize();
				}
				if (rightSize == -1)
				{
					rightSize = 0;
				}

				long moveIndex = -1;
				if (parentSize > rightSize + leftSize + 1)
				{
					moveIndex = indexLayout.getParentIndex(checkIndex);
				} else if (leftSize > rightSize + parentSize + 1)
				{
					moveIndex = leftIndex;
				} else if (rightSize > parentSize + leftSize + 1)
				{
					moveIndex = rightIndex;
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

			long firstParent = -1;

			if (parentSize > 0)
			{
				long updateIndex = checkIndex;
				while ((updateIndex = indexLayout.getParentIndex(updateIndex)) >= 0)
				{
					data.get(updateIndex, node);
					if (node.getSubtreeSize() == -1)
					{
						continue;
					}
					if (firstParent == -1)
						firstParent = updateIndex;
					node.setSubtreeSize(node.getSubtreeSize() - 1
							- (rightParent ? rightSize : leftSize));
					data.set(updateIndex, node);
					if (node.getSubtreeSize() == parentSize + (rightParent ? leftSize : rightSize))
					{
						break;
					}
				}
			}

			if (parentSize > 0)
			{
				startIndices.add(firstParent);
				totalSizes.add(parentSize + (rightParent ? leftSize : rightSize));
			}
			if (leftSize > 0 && (!rightParent || parentSize == 0))
			{
				startIndices.add(leftIndex);
				totalSizes.add(leftSize);
			}

			if (rightSize > 0 && (rightParent || parentSize == 0))
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
		CentroidTreeNode node = new CentroidTreeNode(100);
		ExternalMemoryCache<CentroidTreeNode> copy = new ExternalMemoryCache<CentroidTreeNode>(
				new File(treeDirectory, "toStringArray"), 10000000);
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
