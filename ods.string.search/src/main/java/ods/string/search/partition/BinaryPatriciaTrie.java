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
 * need of an Edge type.
 */
public class BinaryPatriciaTrie<T extends Comparable<T> & Serializable> implements SplittableSet<T>
{
	private static final long serialVersionUID = -766158369075318012L;

	/**
	 * 16 base + 40 non-labels + 24 label array + 32 BitString
	 */
	private static final int BYTES_PER_NODE = 112;

	static class BitString
	{
		public int bitsUsed;
		public byte[] label;

		public BitString()
		{

		}

		public BitString(byte[] label)
		{
			this.label = label;
			bitsUsed = label.length * 8;
		}

		public BitString(byte[] label, int bitsUsed)
		{
			this.label = label;
			this.bitsUsed = bitsUsed;
		}
	}

	protected static class Node
	{
		/**
		 * The string represented by the 'edge' that points to this node.
		 */
		BitString bits;

		int subtreeSize;

		/**
		 * If true, this node represents a complete string stored in the trie and not just a prefix.
		 */
		boolean valueEnd;

		Node leftChild;

		Node rightChild;

		Node parent;

		public Node()
		{

		}

		public Node(byte[] label)
		{
			init(label, label.length * 8);
		}

		public Node(BitString label)
		{
			bits = label;
			valueEnd = false;
			subtreeSize = 1;
		}

		public Node(byte[] label, int matchingBits)
		{
			init(label, matchingBits);
		}

		private void init(byte[] label, int matchingBits)
		{
			bits = new BitString(label, matchingBits);
			valueEnd = false;
			subtreeSize = 1;
		}

		public String toString()
		{
			int length = bits.bitsUsed / 8;
			int mod = bits.bitsUsed % 8;
			String result = new String(bits.label, 0, length);
			if (mod != 0)
			{
				short mask = (short) ((255 << (8 - mod)) % 256);
				String partialByte = Integer.toHexString(bits.label[length] & mask);
				if (partialByte.length() == 1)
					partialByte += "0";
				result += "~" + partialByte + "-" + mod;
			}
			return result;
		}

		public void writeExternal(ObjectOutput out) throws IOException
		{
			byte flags = 0;
			if (subtreeSize == 0)
				flags |= 0x80;
			if (leftChild != null)
				flags |= 0x40;
			if (rightChild != null)
				flags |= 0x20;
			if (valueEnd)
				flags |= 0x10;
			if (bits.bitsUsed <= Byte.MAX_VALUE)
				flags |= 0x08;
			else if (bits.bitsUsed <= Short.MAX_VALUE)
				flags |= 0x04;
			out.writeByte(flags);

			// Prefer to write out bitsUsed as a byte, then short, then int.
			if (bits.bitsUsed <= Byte.MAX_VALUE)
				out.writeByte(bits.bitsUsed);
			else if (bits.bitsUsed <= Short.MAX_VALUE)
				out.writeShort(bits.bitsUsed);
			else
				out.writeInt(bits.bitsUsed);
			out.write(bits.label, 0, (int) Math.ceil(bits.bitsUsed / 8.));

			if (leftChild != null)
				leftChild.writeExternal(out);
			if (rightChild != null)
				rightChild.writeExternal(out);
		}

		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
		{
			byte flags = in.readByte();

			bits = new BitString();
			if ((flags & 0x08) != 0)
				bits.bitsUsed = in.readByte();
			else if ((flags & 0x04) != 0)
				bits.bitsUsed = in.readShort();
			else
				bits.bitsUsed = in.readInt();
			bits.label = new byte[(int) Math.ceil(bits.bitsUsed / 8.)];
			int bytesRead = 0;
			while (bytesRead < bits.label.length)
			{
				bytesRead += in.read(bits.label, bytesRead, bits.label.length - bytesRead);
			}

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
				leftChild.parent = this;
				left.readExternal(in);
				subtreeSize += left.subtreeSize;
			}
			if ((flags & 0x20) != 0)
			{
				Node right = new Node();
				rightChild = right;
				rightChild.parent = this;
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
		public Node lastMatchingNode;
		public BitString leftOver;
		public int bitsMatched;

		public SearchPoint(Node n, byte[] s)
		{
			this.lastMatchingNode = n;
			this.leftOver = new BitString(s);
		}

		public SearchPoint(Node n, BitString s)
		{
			this.lastMatchingNode = n;
			this.leftOver = s;
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

	public static void splitOnPrefix(BitString prefixNode, BitString suffixNode)
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

	public static int getCommonPrefixBits(BitString node1, BitString node2)
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

	public static void appendOnNode(BitString prefixNode, BitString suffixNode)
	{
		int requiredLength = (int) (Math.ceil((prefixNode.bitsUsed + suffixNode.bitsUsed) / 8.));
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

	protected transient ByteArrayConversion converter;

	private transient boolean dirty = true;
	private long dataBytesEstimate = 0;

	protected transient BitString childTrieLabel;

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
		if (lastNode.lastMatchingNode.subtreeSize == 0)
			return lastNode.bitsMatched;

		dataBytesEstimate += Math.ceil(lastNode.leftOver.bitsUsed / 8.);
		if (lastNode.leftOver.bitsUsed > 0)
		{
			boolean goRight = (lastNode.leftOver.label[0] & 0x80) != 0;
			Node existingEdge = goRight ? lastNode.lastMatchingNode.rightChild
					: lastNode.lastMatchingNode.leftChild;
			if (existingEdge == null)
			{
				// New edge and node required.
				Node newNode = new Node(lastNode.leftOver);
				newNode.valueEnd = true;
				newNode.parent = lastNode.lastMatchingNode;
				if (goRight)
					lastNode.lastMatchingNode.rightChild = newNode;
				else
					lastNode.lastMatchingNode.leftChild = newNode;
				incrementNodesToRoot(lastNode.lastMatchingNode, 1);
			} else
			{

				/*
				 * An existing edge needs to be split.
				 */
				int matchingBits = getCommonPrefixBits(lastNode.leftOver, existingEdge.bits);

				byte[] oldLabel = existingEdge.bits.label;
				Node internalNode = new Node(Arrays.copyOf(oldLabel,
						(int) Math.ceil((double) matchingBits / 8)), matchingBits);
				internalNode.subtreeSize = existingEdge.subtreeSize + 1;

				splitOnPrefix(internalNode.bits, lastNode.leftOver);
				splitOnPrefix(internalNode.bits, existingEdge.bits);

				internalNode.parent = existingEdge.parent;
				existingEdge.parent = internalNode;
				if ((existingEdge.bits.label[0] & 0x80) != 0)
					internalNode.rightChild = existingEdge;
				else
					internalNode.leftChild = existingEdge;

				if (lastNode.leftOver.bitsUsed > 0)
				{
					/*
					 * The new string and label have some characters in common but not all.
					 */
					Node leafNode = new Node(lastNode.leftOver);
					leafNode.valueEnd = true;
					leafNode.parent = internalNode;
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

				if ((internalNode.bits.label[0] & 0x80) != 0)
					lastNode.lastMatchingNode.rightChild = internalNode;
				else
					lastNode.lastMatchingNode.leftChild = internalNode;
				incrementNodesToRoot(lastNode.lastMatchingNode, internalNode.subtreeSize
						- existingEdge.subtreeSize);
			}
			n++;
			dirty = true;
			return 0;
		} else if (!lastNode.lastMatchingNode.valueEnd)
		{
			n++;
			lastNode.lastMatchingNode.valueEnd = true;
			dirty = true;
			return 0;
		}
		return -1;
	}

	private void incrementNodesToRoot(Node lastNode, int increment)
	{
		Node node = lastNode;
		while (node != null)
		{
			node.subtreeSize += increment;
			node = node.parent;
		}
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
		if (r.bits.bitsUsed != 0)
		{
			if (getCommonPrefixBits(r.bits, result.leftOver) == r.bits.bitsUsed)
			{
				splitOnPrefix(r.bits, result.leftOver);
				result.bitsMatched += r.bits.bitsUsed;
			} else
				result.leftOver = new BitString(new byte[0]);
		}

		while (result.leftOver.bitsUsed > 0 && result.lastMatchingNode.subtreeSize != 0)
		{
			Node selectedEdge;
			if ((result.leftOver.label[0] & 0x80) != 0)
				selectedEdge = result.lastMatchingNode.rightChild;
			else
				selectedEdge = result.lastMatchingNode.leftChild;
			if (selectedEdge == null)
				break;
			int labelLength = selectedEdge.bits.bitsUsed;
			if (result.leftOver.bitsUsed < labelLength)
				break;

			int matchingBits = getCommonPrefixBits(result.leftOver, selectedEdge.bits);
			if (matchingBits == selectedEdge.bits.bitsUsed)
				splitOnPrefix(selectedEdge.bits, result.leftOver);
			else
				break;

			result.bitsMatched += selectedEdge.bits.bitsUsed;
			result.lastMatchingNode = selectedEdge;
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
		if (result.lastMatchingNode.subtreeSize == 0)
			return result.bitsMatched;

		if (result.leftOver.bitsUsed != 0)
			return -1;

		Node deleteNode = result.lastMatchingNode;
		if (!deleteNode.valueEnd)
			return -1;
		deleteNode.valueEnd = false;

		cleanupNodeStructure(deleteNode);

		n--;
		dirty = true;
		return 0;
	}

	private void cleanupNodeStructure(Node deleteNode)
	{
		if (deleteNode == r)
			return;

		Node parentNode = deleteNode.parent;
		if (deleteNode.leftChild == null && deleteNode.rightChild == null)
		{
			dataBytesEstimate -= Math.ceil(deleteNode.bits.bitsUsed / 8.);
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
				Node parentParentNode = parentNode.parent;
				compressNode(parentParentNode, parentNode);
				incrementNodesToRoot(parentParentNode, -2);
			} else
				incrementNodesToRoot(parentNode, -1);
		} else
		{
			// The node has childen so it can't be removed. It may be compressable though.
			if (deleteNode != r && (deleteNode.leftChild == null || deleteNode.rightChild == null))
			{
				compressNode(parentNode, deleteNode);
				incrementNodesToRoot(parentNode, -1);
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

		appendOnNode(compressNode.bits, newChild.bits);
		newChild.bits = compressNode.bits;
		newChild.parent = parent;
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
		if (result.lastMatchingNode.subtreeSize == 0)
			return result.bitsMatched;

		if (result.leftOver.bitsUsed == 0 && result.lastMatchingNode.valueEnd)
			return 0;
		return -1;
	}

	@Override
	public long getByteSize()
	{
		// 16 base class, 24 BAConversion, 32 class variables, 64 child trie label
		return dataBytesEstimate + r.subtreeSize * BYTES_PER_NODE + 136;
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
			BitString matchedBits = new BitString(Arrays.copyOf(prefix, prefix.length),
					result.bitsMatched);
			if (result.lastMatchingNode == r && result.bitsMatched == 0)
				appendOnNode(matchedBits, result.lastMatchingNode.bits);

			Node traverseEdge;
			if (result.leftOver.bitsUsed > 0 && result.lastMatchingNode.subtreeSize != 0)
			{
				if ((result.leftOver.label[0] & 0x80) != 0)
					traverseEdge = result.lastMatchingNode.rightChild;
				else
					traverseEdge = result.lastMatchingNode.leftChild;
				if (traverseEdge == null)
					return;

				int matchingBits = getCommonPrefixBits(traverseEdge.bits, result.leftOver);
				if (matchingBits != result.leftOver.bitsUsed)
					return;
				appendOnNode(matchedBits, traverseEdge.bits);
				result.lastMatchingNode = traverseEdge;
			}

			uncheckNodes.add(new SearchPoint(result.lastMatchingNode, matchedBits));
		}

		public boolean hasNext()
		{
			if (nextResult != null)
				return true;

			while (!uncheckNodes.isEmpty())
			{
				SearchPoint result = uncheckNodes.remove(uncheckNodes.size() - 1);

				Node rightChild = result.lastMatchingNode.rightChild;
				generateSearchNode(result, uncheckNodes, rightChild);

				Node leftChild = result.lastMatchingNode.leftChild;
				generateSearchNode(result, uncheckNodes, leftChild);

				if (result.lastMatchingNode.valueEnd || result.lastMatchingNode.subtreeSize == 0)
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
				BitString newMatchingString = new BitString(Arrays.copyOf(result.leftOver.label,
						(int) Math.ceil((result.leftOver.bitsUsed + child.bits.bitsUsed) / 8.)),
						result.leftOver.bitsUsed);
				appendOnNode(newMatchingString, child.bits);
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

		int idealSize = r.subtreeSize >> 1;

		Node curNode = r;
		int curDepth = 0;
		BitString matchedLabel = new BitString(new byte[0]);
		while (curNode != null)
		{
			if (curNode.bits.bitsUsed > 0)
				appendOnNode(matchedLabel, curNode.bits);

			int leftSize = (curNode.leftChild != null) ? curNode.leftChild.subtreeSize : 0;
			int rightSize = (curNode.rightChild != null) ? curNode.rightChild.subtreeSize : 0;
			Node nextNode = null;
			int nextMidDiff = Integer.MAX_VALUE;
			if (leftSize >= rightSize)
			{
				nextNode = curNode.leftChild;
				nextMidDiff = Math.abs(idealSize - leftSize);
			} else
			{
				nextNode = curNode.rightChild;
				nextMidDiff = Math.abs(idealSize - rightSize);
			}

			if (Math.abs(curNode.subtreeSize - idealSize) < nextMidDiff
					&& (curDepth >= minPartitionDepth || curNode.subtreeSize == 1))
			{
				Node pointerNode = new Node(Arrays.copyOf(curNode.bits.label,
						curNode.bits.label.length), curNode.bits.bitsUsed);
				pointerNode.subtreeSize = 0;
				pointerNode.parent = curNode.parent;
				result.r = curNode;
				if (curNode.parent.leftChild == curNode)
					curNode.parent.leftChild = pointerNode;
				else
					curNode.parent.rightChild = pointerNode;
				result.r.parent = null;
				curNode = pointerNode;
				break;
			}

			curNode = nextNode;
			curDepth++;
		}

		result.dataBytesEstimate = dataBytesEstimate * result.r.subtreeSize / r.subtreeSize;
		dataBytesEstimate -= result.dataBytesEstimate;
		incrementNodesToRoot(curNode.parent, -result.r.subtreeSize);
		result.r.bits.bitsUsed = matchedLabel.bitsUsed;
		result.r.bits.label = Arrays.copyOf(matchedLabel.label, matchedLabel.label.length);
		childTrieLabel = matchedLabel;
		dirty = true;
		// System.out.println(r.subtreeSize + " " + result.r.subtreeSize + " " + curDepth);
		return result;
	}

	@Override
	public boolean merge(SplittableSet<T> t)
	{
		BinaryPatriciaTrie<T> childTrie = (BinaryPatriciaTrie<T>) t;
		if (childTrieLabel != null
				&& getCommonPrefixBits(childTrieLabel, childTrie.r.bits) == childTrieLabel.bitsUsed)
			childTrieLabel = childTrie.childTrieLabel;

		SearchPoint pointer = findLastNode(childTrie.r.bits.label);
		Node parentOfPointer = pointer.lastMatchingNode.parent;
		if (parentOfPointer.leftChild == pointer.lastMatchingNode)
			parentOfPointer.leftChild = childTrie.r;
		else
			parentOfPointer.rightChild = childTrie.r;
		childTrie.r.parent = parentOfPointer;

		// Change merged root node to be only an appended string instead of a full value string.
		splitOnPrefix(new BitString(childTrie.r.bits.label, childTrie.r.bits.bitsUsed
				- pointer.lastMatchingNode.bits.bitsUsed), childTrie.r.bits);

		dataBytesEstimate += childTrie.dataBytesEstimate;

		incrementNodesToRoot(pointer.lastMatchingNode.parent, childTrie.r.subtreeSize);
		dirty = true;

		if (!childTrie.r.valueEnd)
			cleanupNodeStructure(childTrie.r);

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
		{
			s.writeInt(childTrieLabel.bitsUsed);
			s.write(childTrieLabel.label, 0, (int) Math.ceil(childTrieLabel.bitsUsed / 8.));
		}
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
			childTrieLabel = new BitString();
			childTrieLabel.bitsUsed = inputStream.readInt();

			childTrieLabel.label = new byte[(int) Math.ceil(childTrieLabel.bitsUsed / 8.)];
			int bytesRead = 0;
			while (bytesRead < childTrieLabel.label.length)
			{
				bytesRead += inputStream.read(childTrieLabel.label, bytesRead,
						childTrieLabel.label.length - bytesRead);
			}
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
