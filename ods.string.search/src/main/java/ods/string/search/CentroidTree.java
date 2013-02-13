package ods.string.search;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;

public class CentroidTree
{
	private ExternalMemoryCache<CentroidTreeNode> array;
	private CentroidTreeNode emptyNode = new CentroidTreeNode(100);
	private long size = 0l;
	private File treeDirectory;

	public CentroidTree(File treeDirectory)
	{
		this.treeDirectory = treeDirectory;
		array = new ExternalMemoryCache<CentroidTreeNode>(treeDirectory, 10000000);
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
			long parentIndex = getParentIndex(insertIndex);
			array.get(parentIndex, parentNode);
			if (node.getStringBitLength() == 0)
			{
				if (parentNode.isValueEnd())
					return false;
				parentNode.setValueEnd(true);
				array.set(parentIndex, parentNode);
				newNodes = 0;
			} else if (insertIndex == getLeftChildIndex(parentIndex))
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
					insertAt(getRightChildIndex(insertIndex), node);
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
					parentIndex = getParentIndex(parentIndex);
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
		array.set(getLeftChildIndex(insertIndex), emptyNode);
		array.set(getRightChildIndex(insertIndex), emptyNode);
	}

	private void insertStartingFrom(CentroidTreeNode node, long startIndex)
	{
		node.setSubtreeSize(1);
		long insertIndex = findIndex(node, startIndex);
		insertAt(insertIndex, node);
		CentroidTreeNode parentNode = new CentroidTreeNode(100);
		long parentIndex = getParentIndex(insertIndex);

		while (parentIndex >= startIndex)
		{
			array.get(parentIndex, parentNode);
			parentNode.setSubtreeSize(parentNode.getSubtreeSize() + 1);
			array.set(parentIndex, parentNode);
			parentIndex = getParentIndex(parentIndex);
		}
	}

	public boolean contains(String value)
	{
		CentroidTreeNode node = new CentroidTreeNode(100);
		node.setString(value);
		long index = findIndex(node, 0);
		if (node.getStringBitLength() != 0)
			return false;

		array.get(getParentIndex(index), node);
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
				index = getRightChildIndex(index);
			} else
			{
				index = getLeftChildIndex(index);
			}
		}
		return index;
	}

	public void remove(String value)
	{

	}

	public long size()
	{
		return size;
	}

	private long getLeftChildIndex(long nodeIndex)
	{
		// long result = nodeIndex;
		// result++;
		//
		// int[] indices = new int[7];
		// int cur = 0;
		// for (int x = 32; x >= 0; x /= 2)
		// {
		// long mask = (1l << x);
		// int index = -1;
		// if (result > mask)
		// {
		// result -= mask;
		// index = (int) (result / (mask - 1));
		// result -= index * (mask - 1);
		// }
		// indices[cur++] = index;
		// }

		return (nodeIndex + 1) * 2 - 1;
	}

	private long getRightChildIndex(long nodeIndex)
	{
		return (nodeIndex + 1) * 2;
	}

	private long getParentIndex(long nodeIndex)
	{
		return (nodeIndex + 1) / 2 - 1;
	}

	private boolean isNodeBalanced(long nodeIndex)
	{
		CentroidTreeNode node = new CentroidTreeNode(100);
		array.get(getLeftChildIndex(nodeIndex), node);
		long leftSize = node.getSubtreeSize() + 1;
		array.get(getRightChildIndex(nodeIndex), node);
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

		array.get(getLeftChildIndex(startIndex), node);
		long startLeft = node.getSubtreeSize();
		array.get(getRightChildIndex(startIndex), node);
		long startRight = node.getSubtreeSize();
		System.out.println("Before rebalance: left: " + startLeft + " right: " + startRight);

		// ArrayDeque<Long> startIndices = new ArrayDeque<Long>();
		// startIndices.add(0l);
		// while (!startIndices.isEmpty())
		// {
		// long x = startIndices.removeLast();
		// tourArray.get(x, node);
		// System.out.println(x + " " + node.getSubtreeSize() + " " + node.isValueEnd() + " "
		// + node);
		// if (node.getSubtreeSize() != 0)
		// {
		// startIndices.add(getRightChildIndex(x));
		// startIndices.add(getLeftChildIndex(x));
		// }
		// }

		insertAt(startIndex, emptyNode);
		centroidSplit(tourArray, node, startIndex);

		array.get(getLeftChildIndex(startIndex), node);
		startLeft = node.getSubtreeSize();
		array.get(getRightChildIndex(startIndex), node);
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
			copySubtree(getLeftChildIndex(treeIndex), getLeftChildIndex(insertionIndex),
					resultArray, currentPrefix);
			copySubtree(getRightChildIndex(treeIndex), getRightChildIndex(insertionIndex),
					resultArray, newPrefix);
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
					checkIndex = getParentIndex(checkIndex);
					continue;
				}
				parentSize = totalSize - node.getSubtreeSize();
				leftIndex = getLeftChildIndex(checkIndex);
				data.get(leftIndex, node);
				leftSize = node.getSubtreeSize();
				rightIndex = getRightChildIndex(checkIndex);
				data.get(rightIndex, node);
				rightSize = node.getSubtreeSize();
				rightParent = (getRightChildIndex(getParentIndex(checkIndex)) == checkIndex);

				while (leftSize == -1 && parentSize + 1 < totalSize)
				{
					boolean rightParentz = (getRightChildIndex(getParentIndex(leftIndex)) == leftIndex);
					if (rightParentz)
						leftIndex = getLeftChildIndex(leftIndex);
					else
						leftIndex = getRightChildIndex(leftIndex);
					data.get(leftIndex, node);
					leftSize = node.getSubtreeSize();
				}
				if (leftSize == -1)
				{
					leftSize = 0;
				}

				while (rightSize == -1 && parentSize + leftSize + 1 < totalSize)
				{
					boolean rightParentz = (getRightChildIndex(getParentIndex(rightIndex)) == rightIndex);
					if (rightParentz)
						rightIndex = getLeftChildIndex(rightIndex);
					else
						rightIndex = getRightChildIndex(rightIndex);
					data.get(rightIndex, node);
					rightSize = node.getSubtreeSize();
				}
				if (rightSize == -1)
				{
					rightSize = 0;
				}

				// long nonPrefixers = leftSize;
				// long prefixers = rightSize;
				// if (parentSize > 0 && rightParent)
				// {
				// nonPrefixers += 2. * parentSize / 4 + 1;
				// prefixers += parentSize / 4.;
				// } else if (parentSize > 0)
				// {
				// prefixers += 2. * parentSize / 4 + 1;
				// nonPrefixers += parentSize / 4.;
				// }
				//
				// long moveIndex = -1;
				// if (prefixers > nonPrefixers * 3 + 2)
				// {
				// if (parentSize > 0 && !rightParent)
				// {
				// long parentNonPrefixes = leftSize + rightSize + 1;
				// long parentPrefixes = parentSize - 1;
				// if (parentPrefixes > parentNonPrefixes)
				// moveIndex = getParentIndex(checkIndex);
				// }
				// if (moveIndex == -1)
				// moveIndex = rightIndex;
				// } else if (nonPrefixers > prefixers * 3 + 2)
				// {
				// if (parentSize > 0 && rightParent)
				// {
				// long parentPrefixes = leftSize + rightSize + 1;
				// long parentNonPrefixes = parentSize - 1;
				// if (parentNonPrefixes > parentPrefixes)
				// moveIndex = getParentIndex(checkIndex);
				// }
				// if (moveIndex == -1)
				// moveIndex = leftIndex;
				// }

				long moveIndex = -1;
				if (parentSize > rightSize + leftSize + 1)
				{
					moveIndex = getParentIndex(checkIndex);
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
				while ((updateIndex = getParentIndex(updateIndex)) >= 0)
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

		// startIndices.add(0l);
		// while (!startIndices.isEmpty())
		// {
		// long x = startIndices.remove();
		// data.get(x, node);
		// System.out.println(x + " " + node.getSubtreeSize());
		// if (node.getSubtreeSize() != 0)
		// {
		// startIndices.add(getLeftChildIndex(x));
		// startIndices.add(getRightChildIndex(x));
		// }
		// }
	}

	private long inOrderTour(long treeIndex, long insertionIndex,
			ExternalMemoryCache<CentroidTreeNode> resultArray, CentroidTreeNode currentPrefix)
	{
		CentroidTreeNode node = new CentroidTreeNode(100);
		array.get(treeIndex, node);
		if (node.getStringBitLength() == 0)
			return insertionIndex;

		insertionIndex = inOrderTour(getLeftChildIndex(treeIndex), insertionIndex, resultArray,
				currentPrefix);
		CentroidTreeNode.appendOnNode(currentPrefix, node);
		currentPrefix.setValueEnd(node.isValueEnd());
		resultArray.set(insertionIndex, currentPrefix);
		insertionIndex++;
		insertionIndex = inOrderTour(getRightChildIndex(treeIndex), insertionIndex, resultArray,
				currentPrefix);
		currentPrefix.setBitsUsed(currentPrefix.getStringBitLength() - node.getStringBitLength());
		return insertionIndex;
	}

	private void preOrderTour(ExternalMemoryCache<CentroidTreeNode> tourArray, long insertionIndex,
			long startIndex, long endIndex, CentroidTreeNode node)
	{
		// long middleIndex = (endIndex - startIndex) / 2 + startIndex;
		// tourArray.get(middleIndex, node);
		// insertStartingFrom(node, insertionIndex);
		// if (middleIndex - startIndex > 0)
		// preOrderTour(tourArray, insertionIndex, startIndex, middleIndex, node);
		// if (endIndex - middleIndex - 1 > 0)
		// preOrderTour(tourArray, insertionIndex, middleIndex + 1, endIndex, node);

		ArrayDeque<Long> startIndices = new ArrayDeque<Long>();
		startIndices.push(startIndex);
		startIndices.push(endIndex);
		while (startIndices.size() > 0)
		{
			startIndex = startIndices.removeLast();
			endIndex = startIndices.removeLast();
			long middleIndex = (endIndex - startIndex) / 2 + startIndex;
			tourArray.get(middleIndex, node);
			insertStartingFrom(node, insertionIndex);
			if (middleIndex - startIndex > 0)
			{
				startIndices.push(startIndex);
				startIndices.push(middleIndex);
			}
			if (endIndex - middleIndex - 1 > 0)
			{
				startIndices.push(middleIndex + 1);
				startIndices.push(endIndex);
			}
		}
	}

	private void randomOrderTour(ExternalMemoryCache<CentroidTreeNode> tourArray,
			long insertionIndex, long startIndex, long endIndex, CentroidTreeNode node)
	{
		HashSet<Long> insertedIndices = new HashSet<Long>();
		Random rand = new Random();

		long size = endIndex - startIndex + 1;
		while (insertedIndices.size() < size)
		{
			long nextIndex = Math.abs(rand.nextLong()) % size + startIndex;
			if (!insertedIndices.contains(nextIndex))
			{
				insertedIndices.add(nextIndex);
				tourArray.get(nextIndex, node);
				insertStartingFrom(node, insertionIndex);
			}
		}

		System.out.println(insertedIndices.size());
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
				indices.add(getLeftChildIndex(index));
				indices.add(getRightChildIndex(index));
			}
		}
		return result;
	}
}
