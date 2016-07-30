package ods.string.search.partition;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import ods.string.search.partition.splitsets.SplittableSet;

/**
 * This class implements a variation of the Patricia Trie data structure. This is a tree whose
 * internal nodes all have 2 or more children and whose edges are labelled with strings. Each leaf,
 * v, corresponds to a string stored in the trie that can be obtained by concatenating the labels of
 * all edges on the path from the root to v.
 * 
 * In this implementation, the edge labels have been merged into the nodes themselves to remove the
 * need of an Edge type. Also, rather than using an array to store the edges, a HashMap is instead
 * used to reduce the memory footprint.
 */
public class BinaryPatriciaTrie<T extends Comparable<T> & Serializable> implements SplittableSet<T>
{
	private static final long serialVersionUID = -766158369075318012L;

	/**
	 * 16 base + 32 non-labels + 32 label array
	 */
	private static final int BYTES_PER_NODE = 80;

	protected static class Node
	{
		/**
		 * The string represented by the 'edge' that points to this node.
		 */
		byte[] label;

		int bitsUsed;

		int subtreeSize;

		/**
		 * If true, this node represents a complete string stored in the trie and not just a prefix.
		 */
		boolean valueEnd;

		Node leftChild;

		Node rightChild;

		public Node()
		{

		}

		public Node(byte[] label)
		{
			init(label, label.length * 8);
		}

		public Node(byte[] label, int matchingBits)
		{
			init(label, matchingBits);
		}

		private void init(byte[] label, int matchingBits)
		{
			this.label = label;
			bitsUsed = matchingBits;
			valueEnd = false;
			subtreeSize = 1;
		}

		public String toString()
		{
			int length = bitsUsed / 8;
			int mod = bitsUsed % 8;
			String result = new String(label, 0, length);
			if (mod != 0)
			{
				short mask = (short) ((255 << (8 - mod)) % 256);
				String partialByte = Integer.toHexString(label[length] & mask);
				if (partialByte.length() == 1)
					partialByte += "0";
				result += "~" + partialByte + "-" + mod;
			}
			return result;
		}

		public void writeExternal(ObjectOutput out) throws IOException
		{
			out.writeInt(bitsUsed);
			out.write(label, 0, (int) Math.ceil(bitsUsed / 8.));

			byte flags = 0;
			if (subtreeSize == 0)
				flags |= 0x80;
			if (leftChild != null)
				flags |= 0x40;
			if (rightChild != null)
				flags |= 0x20;
			if (valueEnd)
				flags |= 0x10;
			out.writeByte(flags);

			if (leftChild != null)
				leftChild.writeExternal(out);
			if (rightChild != null)
				rightChild.writeExternal(out);
		}

		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
		{
			bitsUsed = in.readInt();
			label = new byte[(int) Math.ceil(bitsUsed / 8.)];
			int bytesRead = 0;
			while (bytesRead < label.length)
			{
				bytesRead += in.read(label, bytesRead, label.length - bytesRead);
			}

			byte flags = in.readByte();
			if ((flags & 0x80) != 0)
			{
				subtreeSize = 0;
				return;
			}

			subtreeSize = 1;
			if ((flags & 0x10) != 0)
				valueEnd = true;

			if ((flags & 0x40) != 0)
			{
				Node left = new Node();
				leftChild = left;
				left.readExternal(in);
				subtreeSize += left.subtreeSize;
			}
			if ((flags & 0x20) != 0)
			{
				Node right = new Node();
				rightChild = right;
				right.readExternal(in);
				subtreeSize += right.subtreeSize;
			}
		}
	}

	/**
	 * This class is used to store a node and string as a single value.
	 */
	protected static class SearchPoint
	{
		public ArrayList<Node> lastMatchingNode;
		public Node leftOver;
		public int bitsMatched;

		public SearchPoint(Node n, byte[] s)
		{
			this.lastMatchingNode = new ArrayList<Node>();
			this.lastMatchingNode.add(n);
			this.leftOver = new Node(s);
		}

		public SearchPoint(Node n, Node s)
		{
			this.lastMatchingNode = new ArrayList<Node>();
			this.lastMatchingNode.add(n);
			this.leftOver = s;
		}

		public Node getLastMatchingNode()
		{
			return lastMatchingNode.get(lastMatchingNode.size() - 1);
		}
	}

	interface ByteArrayConversion
	{
		byte[] getBytes(Object val);

		Object readFromBytes(byte[] bytes);
	}

	private static class StringConversion implements ByteArrayConversion, Serializable
	{
		private static final long serialVersionUID = -4297528634952085522L;

		@Override
		public byte[] getBytes(Object val)
		{
			return ((String) val).getBytes();
		}

		@Override
		public Object readFromBytes(byte[] bytes)
		{
			return new String(bytes).trim();
		}
	}

	private static class IntegerConversion implements ByteArrayConversion, Serializable
	{
		private static final long serialVersionUID = -2746654070188456294L;

		@Override
		public byte[] getBytes(Object val)
		{
			ByteBuffer result = ByteBuffer.allocate(4);
			result.putInt((int) val);
			return result.array();
		}

		@Override
		public Object readFromBytes(byte[] bytes)
		{
			ByteBuffer result = ByteBuffer.wrap(bytes);
			return result.getInt();
		}
	}

	public static void splitOnPrefix(Node prefixNode, Node suffixNode)
	{
		int matchedBits = getCommonPrefixBits(prefixNode, suffixNode);
		prefixNode.bitsUsed = matchedBits;

		byte[] suffixArray = suffixNode.label;
		for (int x = matchedBits; x < suffixNode.bitsUsed; x++)
		{
			short startMask = (short) (128 >> ((x - matchedBits) % 8));
			short endMask = (short) (128 >> (x % 8));
			if ((suffixArray[x / 8] & endMask) != 0)
				suffixArray[(x - matchedBits) / 8] |= startMask;
			else
			{
				startMask ^= 255;
				suffixArray[(x - matchedBits) / 8] &= startMask;
			}
		}
		suffixNode.bitsUsed -= matchedBits;
	}

	public static int getCommonPrefixBits(Node node1, Node node2)
	{
		int matchedBits = 0;
		int length1 = (int) Math.ceil(node1.bitsUsed / 8.);
		int length2 = (int) Math.ceil(node2.bitsUsed / 8.);
		for (int x = 0; x < length1 && x < length2; x++)
		{
			if (node1.label[x] == node2.label[x])
			{
				int bitsMatched = 8;
				int bitsRemaining = node1.bitsUsed - x * 8;
				if (bitsRemaining < bitsMatched)
					bitsMatched = bitsRemaining;
				bitsRemaining = node2.bitsUsed - x * 8;
				if (bitsRemaining < bitsMatched)
					bitsMatched = bitsRemaining;
				matchedBits += bitsMatched;
			} else
			{
				short mask = (short) 128;
				for (; mask > 0 && matchedBits < node1.bitsUsed && matchedBits < node2.bitsUsed; mask = (short) (mask >> 1))
				{
					if ((node1.label[x] & mask) == (node2.label[x] & mask))
						matchedBits++;
					else
						break;
				}
				break;
			}
		}
		return matchedBits;
	}

	public static void appendOnNode(Node prefixNode, Node suffixNode)
	{
		int requiredLength = (int) (Math.ceil(prefixNode.bitsUsed + suffixNode.bitsUsed / 8.));
		if (requiredLength > prefixNode.label.length)
			prefixNode.label = Arrays.copyOf(prefixNode.label, requiredLength);

		byte[] prefixArray = prefixNode.label;
		byte[] suffixArray = suffixNode.label;
		for (int x = prefixNode.bitsUsed; x < prefixNode.bitsUsed + suffixNode.bitsUsed; x++)
		{
			short startMask = (short) (128 >> (x % 8));
			short endMask = (short) (128 >> ((x - prefixNode.bitsUsed) % 8));
			if ((suffixArray[(x - prefixNode.bitsUsed) / 8] & endMask) != 0)
				prefixArray[x / 8] |= startMask;
			else
			{
				startMask ^= 255;
				prefixArray[x / 8] &= startMask;
			}
		}
		prefixNode.bitsUsed += suffixNode.bitsUsed;
	}

	/**
	 * The number of strings stored in the trie
	 */
	protected int n;

	/**
	 * The root
	 */
	protected transient Node r;

	protected ByteArrayConversion converter;

	private transient boolean dirty = true;
	private long dataBytesEstimate = 0;

	protected transient Node childTrieLabel;

	public BinaryPatriciaTrie()
	{
		r = new Node(new byte[0]);
		n = 0;
	}

	/**
	 * Add the x to this trie.
	 * 
	 * @param x
	 *            The string to add.
	 * @return True if x was successfully added or false if x is already in the trie.
	 */
	public boolean add(T x)
	{
		if (x == null)
			throw new IllegalArgumentException("The trie doesn't store null values.");

		byte[] bytesToAdd = convertToBytes(x);
		int result = add(bytesToAdd);
		if (result == 0)
			return true;
		return false;
	}

	public int add(byte[] bytesToAdd)
	{
		SearchPoint lastNode = findLastNode(bytesToAdd);
		if (lastNode.getLastMatchingNode().subtreeSize == 0)
			return lastNode.bitsMatched;

		dataBytesEstimate += Math.ceil(lastNode.leftOver.bitsUsed / 8.);
		if (lastNode.leftOver.bitsUsed > 0)
		{
			boolean goRight = (lastNode.leftOver.label[0] & 0x80) != 0;
			Node existingEdge = goRight ? lastNode.getLastMatchingNode().rightChild : lastNode
					.getLastMatchingNode().leftChild;
			if (existingEdge == null)
			{
				// New edge and node required.
				Node newNode = lastNode.leftOver;
				newNode.valueEnd = true;
				if (goRight)
					lastNode.getLastMatchingNode().rightChild = newNode;
				else
					lastNode.getLastMatchingNode().leftChild = newNode;
				incrementNodesToRoot(lastNode, 1);
			} else
			{

				/*
				 * An existing edge needs to be split.
				 */
				int matchingBits = getCommonPrefixBits(lastNode.leftOver, existingEdge);

				byte[] oldLabel = existingEdge.label;
				Node internalNode = new Node(Arrays.copyOf(oldLabel,
						(int) Math.ceil((double) matchingBits / 8)), matchingBits);
				internalNode.subtreeSize = existingEdge.subtreeSize + 1;

				splitOnPrefix(internalNode, lastNode.leftOver);
				splitOnPrefix(internalNode, existingEdge);

				if ((existingEdge.label[0] & 0x80) != 0)
					internalNode.rightChild = existingEdge;
				else
					internalNode.leftChild = existingEdge;

				if (lastNode.leftOver.bitsUsed > 0)
				{
					/*
					 * The new string and label have some characters in common but not all.
					 */
					Node leafNode = lastNode.leftOver;
					leafNode.valueEnd = true;
					if (internalNode.leftChild == null)
						internalNode.leftChild = leafNode;
					else
						internalNode.rightChild = leafNode;
					internalNode.subtreeSize++;
				} else
				{
					// The new string is a prefix of the existing label.
					internalNode.valueEnd = true;
				}

				if ((internalNode.label[0] & 0x80) != 0)
					lastNode.getLastMatchingNode().rightChild = internalNode;
				else
					lastNode.getLastMatchingNode().leftChild = internalNode;
				incrementNodesToRoot(lastNode, internalNode.subtreeSize - existingEdge.subtreeSize);
			}
			n++;
			dirty = true;
			return 0;
		} else if (!lastNode.getLastMatchingNode().valueEnd)
		{
			n++;
			lastNode.getLastMatchingNode().valueEnd = true;
			dirty = true;
			return 0;
		}
		return -1;
	}

	private void incrementNodesToRoot(SearchPoint lastNode, int increment)
	{
		for (Node pathNode : lastNode.lastMatchingNode)
			pathNode.subtreeSize += increment;
	}

	public byte[] convertToBytes(T x)
	{
		initTypeToByteConverter(x);
		return converter.getBytes(x);
	}

	private void initTypeToByteConverter(T x)
	{
		if (converter == null)
		{
			if (x instanceof String)
				converter = new StringConversion();
			else if (x instanceof Integer)
				converter = new IntegerConversion();
		}
	}

	private SearchPoint findLastNode(byte[] s)
	{
		return findLastNode(s, s.length * 8);
	}

	/**
	 * Searches the trie for the specified string. The node that most closely matches the string
	 * without representing a longer string will be returned. Also the remaining unmatched portion
	 * of the input string is also returned.
	 * 
	 * @param s
	 *            The string to search for the closest matching node.
	 */
	private SearchPoint findLastNode(byte[] s, int bitsUsed)
	{
		SearchPoint result = new SearchPoint(r, Arrays.copyOf(s, s.length));
		result.leftOver.bitsUsed = bitsUsed;
		if (r.bitsUsed != 0)
		{
			if (getCommonPrefixBits(r, result.leftOver) == r.bitsUsed)
			{
				splitOnPrefix(r, result.leftOver);
				result.bitsMatched += r.bitsUsed;
			} else
				result.leftOver = new Node(new byte[0]);
		}

		while (result.leftOver.bitsUsed > 0 && result.getLastMatchingNode().subtreeSize != 0)
		{
			Node selectedEdge;
			if ((result.leftOver.label[0] & 0x80) != 0)
				selectedEdge = result.getLastMatchingNode().rightChild;
			else
				selectedEdge = result.getLastMatchingNode().leftChild;
			if (selectedEdge == null)
				break;
			int labelLength = selectedEdge.bitsUsed;
			if (result.leftOver.bitsUsed < labelLength)
				break;

			int matchingBits = getCommonPrefixBits(result.leftOver, selectedEdge);
			if (matchingBits == selectedEdge.bitsUsed)
				splitOnPrefix(selectedEdge, result.leftOver);
			else
				break;

			result.bitsMatched += selectedEdge.bitsUsed;
			result.lastMatchingNode.add(selectedEdge);
		}

		return result;
	}

	/**
	 * Remove x from this trie.
	 * 
	 * @param x
	 *            The string to remove.
	 * @return True if x was successfully removed or false if x is not stored in the trie.
	 */
	public boolean remove(T x)
	{
		if (x == null)
			throw new IllegalArgumentException("The trie doesn't store null values.");

		byte[] bytesToRemove = convertToBytes(x);
		return remove(bytesToRemove) == 0;
	}

	public int remove(byte[] bytesToRemove)
	{
		// Find the parent node of the node to be deleted.
		SearchPoint result = findLastNode(bytesToRemove);
		if (result.getLastMatchingNode().subtreeSize == 0)
			return result.bitsMatched;

		if (result.leftOver.bitsUsed != 0)
			return -1;

		Node deleteNode = result.getLastMatchingNode();
		if (!deleteNode.valueEnd)
			return -1;
		deleteNode.valueEnd = false;

		result.lastMatchingNode.remove(result.lastMatchingNode.size() - 1);
		cleanupNodeStructure(result, deleteNode);

		n--;
		dirty = true;
		return 0;
	}

	private void cleanupNodeStructure(SearchPoint searchPathToParent, Node deleteNode)
	{
		if (deleteNode == r)
			return;

		Node parentNode = searchPathToParent.getLastMatchingNode();
		if (deleteNode.leftChild == null && deleteNode.rightChild == null)
		{
			dataBytesEstimate -= Math.ceil(deleteNode.bitsUsed / 8.);
			// Remove the link from the parent node to the deleted node.
			if (parentNode.leftChild == deleteNode)
				parentNode.leftChild = null;
			else
				parentNode.rightChild = null;

			if (parentNode != r && !parentNode.valueEnd)
			{
				/*
				 * If the internal node now has 1 child, it must be compressed. We need it's parent
				 * now.
				 */
				Node parentParentNode = searchPathToParent.lastMatchingNode
						.get(searchPathToParent.lastMatchingNode.size() - 2);
				compressNode(parentParentNode, parentNode);
				searchPathToParent.lastMatchingNode.remove(searchPathToParent.lastMatchingNode
						.size() - 1);
				incrementNodesToRoot(searchPathToParent, -2);
			} else
				incrementNodesToRoot(searchPathToParent, -1);
		} else
		{
			// The node has childen so it can't be removed. It may be compressable though.
			if (deleteNode != r && (deleteNode.leftChild == null || deleteNode.rightChild == null))
			{
				compressNode(parentNode, deleteNode);
				incrementNodesToRoot(searchPathToParent, -1);
			}
		}
	}

	/**
	 * This method will compress the specified node into its single child. The compress node is
	 * assumed to have only one child.
	 * 
	 * @param parent
	 *            The parent of the node to compress.
	 * @param compressNode
	 *            The node to compress.
	 */
	private void compressNode(Node parent, Node compressNode)
	{
		Node newChild = null;
		if (compressNode.leftChild != null)
			newChild = compressNode.leftChild;
		else
			newChild = compressNode.rightChild;

		appendOnNode(compressNode, newChild);
		newChild.label = compressNode.label;
		newChild.bitsUsed = compressNode.bitsUsed;
		if (parent.leftChild == compressNode)
			parent.leftChild = newChild;
		else
			parent.rightChild = newChild;
	}

	/**
	 * Return the String stored in this trie that is equal to x.
	 * 
	 * @return A String equal x if x was found and null otherwise.
	 */
	public boolean contains(T x)
	{
		if (x == null)
			throw new IllegalArgumentException("The trie doesn't store null values.");

		byte[] bytesToFind = convertToBytes(x);
		return contains(bytesToFind) == 0;
	}

	public int contains(byte[] bytesToFind)
	{
		SearchPoint result = findLastNode(bytesToFind);
		if (result.getLastMatchingNode().subtreeSize == 0)
			return result.bitsMatched;

		if (result.leftOver.bitsUsed == 0 && result.getLastMatchingNode().valueEnd)
			return 0;
		return -1;
	}

	@Override
	public long getByteSize()
	{
		// 16 base class, 24 BAConversion, 32 class variables
		return dataBytesEstimate + r.subtreeSize * BYTES_PER_NODE + 72;
	}

	@Override
	public boolean isDirty()
	{
		return dirty;
	}

	@Override
	public long size()
	{
		return n;
	}

	private abstract class TrieIteratorParent
	{
		private ArrayList<SearchPoint> uncheckNodes = new ArrayList<SearchPoint>();
		private Object nextResult;

		public TrieIteratorParent(byte[] prefix)
		{
			SearchPoint result = findLastNode(prefix);
			Node matchedBits = new Node(new byte[prefix.length], 0);
			for (Node n : result.lastMatchingNode)
				appendOnNode(matchedBits, n);

			Node traverseEdge;
			if (result.leftOver.bitsUsed > 0 && result.getLastMatchingNode().subtreeSize != 0)
			{
				if ((result.leftOver.label[0] & 0x80) != 0)
					traverseEdge = result.getLastMatchingNode().rightChild;
				else
					traverseEdge = result.getLastMatchingNode().leftChild;
				if (traverseEdge == null)
					return;

				int matchingBits = getCommonPrefixBits(traverseEdge, result.leftOver);
				if (matchingBits != result.leftOver.bitsUsed)
					return;
				appendOnNode(matchedBits, traverseEdge);
				result.lastMatchingNode.add(traverseEdge);
			}

			uncheckNodes.add(new SearchPoint(result.getLastMatchingNode(), matchedBits));
		}

		public boolean hasNext()
		{
			if (nextResult != null)
				return true;

			while (!uncheckNodes.isEmpty())
			{
				SearchPoint result = uncheckNodes.remove(uncheckNodes.size() - 1);

				Node rightChild = result.getLastMatchingNode().rightChild;
				generateSearchNode(result, uncheckNodes, rightChild);

				Node leftChild = result.getLastMatchingNode().leftChild;
				generateSearchNode(result, uncheckNodes, leftChild);

				if (result.getLastMatchingNode().valueEnd
						|| result.getLastMatchingNode().subtreeSize == 0)
				{
					nextResult = getNextReturnResult(result);
					return true;
				}
			}

			return false;
		}

		protected abstract Object getNextReturnResult(SearchPoint result);

		protected Object nextObject()
		{
			hasNext();
			Object result = nextResult;
			nextResult = null;
			return result;
		}

		public void remove()
		{
			throw new UnsupportedOperationException();
		}

		private void generateSearchNode(SearchPoint result, ArrayList<SearchPoint> uncheckNodes,
				Node child)
		{
			if (child != null)
			{
				Node newMatchingString = new Node(Arrays.copyOf(result.leftOver.label,
						result.leftOver.label.length + child.label.length),
						result.leftOver.bitsUsed);
				appendOnNode(newMatchingString, child);
				uncheckNodes.add(new SearchPoint(child, newMatchingString));
			}
		}
	}

	private class TrieIterator extends TrieIteratorParent implements Iterator<T>
	{

		public TrieIterator(byte[] prefix)
		{
			super(prefix);
		}

		protected Object getNextReturnResult(SearchPoint result)
		{
			return converter.readFromBytes(result.leftOver.label);
		}

		@SuppressWarnings("unchecked")
		@Override
		public T next()
		{
			return (T) nextObject();
		}
	}

	private class TrieIteratorSearchPoints extends TrieIteratorParent implements
			Iterator<SearchPoint>
	{

		public TrieIteratorSearchPoints(byte[] prefix)
		{
			super(prefix);
		}

		protected Object getNextReturnResult(SearchPoint result)
		{
			return result;
		}

		@Override
		public SearchPoint next()
		{
			return (SearchPoint) nextObject();
		}
	}

	@Override
	public Iterator<T> iterator(T from, T to)
	{
		if (from == null)
			throw new IllegalArgumentException("The trie doesn't store null values.");

		byte[] bytesToFind = convertToBytes(from);
		return new TrieIterator(bytesToFind);
	}

	@Override
	public Iterator<T> iterator()
	{
		return new TrieIterator(new byte[0]);
	}

	public Iterator<SearchPoint> iterator(byte[] prefix)
	{
		if (prefix == null)
			throw new IllegalArgumentException("The trie doesn't store null values.");

		return new TrieIteratorSearchPoints(prefix);
	}

	@Override
	public SplittableSet<T> split(T x)
	{
		return split(x, 0);
	}

	public SplittableSet<T> split(T x, int minPartitionDepth)
	{
		BinaryPatriciaTrie<T> result = new BinaryPatriciaTrie<T>();
		result.converter = converter;

		int upperBoundSize = r.subtreeSize / 3 * 2;

		ArrayList<Node> parents = new ArrayList<Node>();
		Node curNode = r;
		int curDepth = 0;
		Node matchedLabel = new Node(new byte[0]);
		while (curNode != null)
		{
			if (curNode.bitsUsed > 0)
				appendOnNode(matchedLabel, curNode);

			parents.add(curNode);

			int leftSize = (curNode.leftChild != null) ? curNode.leftChild.subtreeSize : 0;
			int rightSize = (curNode.rightChild != null) ? curNode.rightChild.subtreeSize : 0;
			if (leftSize >= rightSize && (curDepth >= minPartitionDepth || leftSize == 1)
					&& leftSize <= upperBoundSize && leftSize > 0)
			{
				appendOnNode(matchedLabel, curNode.leftChild);
				Node pointerNode = new Node(Arrays.copyOf(curNode.leftChild.label,
						curNode.leftChild.label.length), curNode.leftChild.bitsUsed);
				pointerNode.subtreeSize = 0;
				result.r = curNode.leftChild;
				curNode.leftChild = pointerNode;
				break;
			} else if (rightSize > leftSize && (curDepth >= minPartitionDepth || rightSize == 1)
					&& rightSize <= upperBoundSize)
			{
				appendOnNode(matchedLabel, curNode.rightChild);
				Node pointerNode = new Node(Arrays.copyOf(curNode.rightChild.label,
						curNode.rightChild.label.length), curNode.rightChild.bitsUsed);
				pointerNode.subtreeSize = 0;
				result.r = curNode.rightChild;
				curNode.rightChild = pointerNode;
				break;
			} else
			{
				if (leftSize > rightSize)
					curNode = curNode.leftChild;
				else
					curNode = curNode.rightChild;
				curDepth++;
			}
		}

		result.dataBytesEstimate = dataBytesEstimate * result.r.subtreeSize / r.subtreeSize;
		dataBytesEstimate -= result.dataBytesEstimate;
		for (Node n : parents)
			n.subtreeSize -= result.r.subtreeSize;
		result.r.bitsUsed = matchedLabel.bitsUsed;
		result.r.label = Arrays.copyOf(matchedLabel.label, matchedLabel.label.length);
		childTrieLabel = matchedLabel;
		dirty = true;

		return result;
	}

	@Override
	public boolean merge(SplittableSet<T> t)
	{
		BinaryPatriciaTrie<T> childTrie = (BinaryPatriciaTrie<T>) t;
		if (childTrieLabel != null
				&& getCommonPrefixBits(childTrieLabel, childTrie.r) == childTrieLabel.bitsUsed)
			childTrieLabel = childTrie.childTrieLabel;

		SearchPoint pointer = findLastNode(childTrie.r.label);
		Node parentOfPointer = pointer.lastMatchingNode.get(pointer.lastMatchingNode.size() - 2);
		if (parentOfPointer.leftChild == pointer.getLastMatchingNode())
			parentOfPointer.leftChild = childTrie.r;
		else
			parentOfPointer.rightChild = childTrie.r;

		// Change merged root node to be only an appended string instead of a full value string.
		splitOnPrefix(
				new Node(childTrie.r.label, childTrie.r.bitsUsed
						- pointer.getLastMatchingNode().bitsUsed), childTrie.r);

		dataBytesEstimate += childTrie.dataBytesEstimate;

		pointer.lastMatchingNode.remove(pointer.lastMatchingNode.size() - 1);
		incrementNodesToRoot(pointer, childTrie.r.subtreeSize);
		dirty = true;

		if (!childTrie.r.valueEnd)
			cleanupNodeStructure(pointer, childTrie.r);

		return true;
	}

	@Override
	public T locateMiddleValue()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public T floor(T val)
	{
		throw new UnsupportedOperationException();
	}

	public String toString()
	{
		String result = "";
		Iterator<T> vals = iterator();
		while (vals.hasNext())
			result += " " + vals.next();

		return result;
	}

	private void writeObject(ObjectOutputStream s) throws IOException
	{
		s.defaultWriteObject();

		byte flags = 0;
		if (r != null)
			flags |= 0x80;
		if (childTrieLabel != null)
			flags |= 0x40;
		s.writeByte(flags);

		if (r != null)
			r.writeExternal(s);
		if (childTrieLabel != null)
			childTrieLabel.writeExternal(s);
	}

	private void readObject(ObjectInputStream inputStream) throws IOException,
			ClassNotFoundException
	{
		inputStream.defaultReadObject();
		dirty = false;

		byte flags = inputStream.readByte();
		if ((flags & 0x80) != 0)
		{
			r = new Node();
			r.readExternal(inputStream);
		}
		if ((flags & 0x40) != 0)
		{
			childTrieLabel = new Node();
			childTrieLabel.readExternal(inputStream);
		}
	}

	@Override
	public void close()
	{
	}

	@Override
	public SplittableSet<T> createNewSet()
	{
		BinaryPatriciaTrie<T> result = new BinaryPatriciaTrie<T>();
		return result;
	}

	@Override
	public T lower(T val)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public T higher(T val)
	{
		throw new UnsupportedOperationException();
	}
}
