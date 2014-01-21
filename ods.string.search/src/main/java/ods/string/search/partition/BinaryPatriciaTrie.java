package ods.string.search.partition;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

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

	protected static class Node implements Serializable
	{
		private static final long serialVersionUID = 4014282151450729129L;

		/**
		 * The string represented by the 'edge' that points to this node.
		 */
		byte[] label;

		int bitsUsed;

		/**
		 * If true, this node represents a complete string stored in the trie and not just a prefix.
		 */
		boolean valueEnd;

		Node leftChild;

		Node rightChild;

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
	}

	/**
	 * This class is used to store a node and string as a single value.
	 */
	protected static class SearchPoint
	{
		public ArrayList<Node> lastMatchingNode;
		public Node leftOver;

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
	protected Node r;

	private ByteArrayConversion converter;

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

		initByteConverterIfNecessary(x);

		byte[] bytesToAdd = converter.getBytes(x);
		SearchPoint lastNode = findLastNode(bytesToAdd);
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
			} else
			{

				/*
				 * An existing edge needs to be split.
				 */
				int matchingBits = getCommonPrefixBits(lastNode.leftOver, existingEdge);

				byte[] oldLabel = existingEdge.label;
				Node internalNode = new Node(Arrays.copyOf(oldLabel,
						(int) Math.ceil((double) matchingBits / 8)), matchingBits);

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
				} else
				{
					// The new string is a prefix of the existing label.
					internalNode.valueEnd = true;
				}

				if ((internalNode.label[0] & 0x80) != 0)
					lastNode.getLastMatchingNode().rightChild = internalNode;
				else
					lastNode.getLastMatchingNode().leftChild = internalNode;
			}
			n++;
			return true;
		} else if (!lastNode.getLastMatchingNode().valueEnd)
		{
			n++;
			lastNode.getLastMatchingNode().valueEnd = true;
			return true;
		}
		return false;
	}

	private void initByteConverterIfNecessary(T x)
	{
		if (converter == null)
		{
			if (x instanceof String)
				converter = new ByteArrayConversion()
				{
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
				};
			else if (x instanceof Integer)
				converter = new ByteArrayConversion()
				{
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
				};
		}
	}

	/**
	 * Searches the trie for the specified string. The node that most closely matches the string
	 * without representing a longer string will be returned. Also the remaining unmatched portion
	 * of the input string is also returned.
	 * 
	 * @param s
	 *            The string to search for the closest matching node.
	 */
	private SearchPoint findLastNode(byte[] s)
	{
		SearchPoint result = new SearchPoint(r, s);
		while (result.leftOver.bitsUsed > 0)
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

		initByteConverterIfNecessary(x);

		byte[] bytesToRemove = converter.getBytes(x);

		// Find the parent node of the node to be deleted.
		SearchPoint result = findLastNode(bytesToRemove);
		if (result.leftOver.bitsUsed != 0)
			return false;

		Node parentNode = null;
		if (result.lastMatchingNode.size() > 1)
			parentNode = result.lastMatchingNode.get(result.lastMatchingNode.size() - 2);
		Node deleteNode = result.getLastMatchingNode();
		if (!deleteNode.valueEnd)
			return false;

		if (deleteNode.leftChild == null && deleteNode.rightChild == null)
		{
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
				Node parentParentNode = result.lastMatchingNode
						.get(result.lastMatchingNode.size() - 3);
				compressNode(parentParentNode, parentNode);
			}
		} else
		{
			// The node has childen so it can't be removed. It may be compressable though.
			deleteNode.valueEnd = false;
			if (deleteNode.leftChild == null || deleteNode.rightChild == null)
				compressNode(parentNode, deleteNode);
		}

		n--;
		return true;
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

		initByteConverterIfNecessary(x);

		byte[] bytesToFind = converter.getBytes(x);
		SearchPoint result = findLastNode(bytesToFind);
		if (result.leftOver.bitsUsed == 0 && result.getLastMatchingNode().valueEnd)
			return true;
		return false;
	}

	@Override
	public long getByteSize()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isDirty()
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public long size()
	{
		return n;
	}

	protected class TrieIterator implements Iterator<T>
	{
		private ArrayList<SearchPoint> uncheckNodes = new ArrayList<SearchPoint>();
		private T nextResult;

		public TrieIterator(byte[] prefix)
		{
			SearchPoint result = findLastNode(prefix);
			Node matchedBits = new Node(new byte[prefix.length], 0);
			for (Node n : result.lastMatchingNode)
				appendOnNode(matchedBits, n);

			Node traverseEdge;
			if (result.leftOver.bitsUsed > 0)
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

		@SuppressWarnings("unchecked")
		public boolean hasNext()
		{
			if (nextResult != null)
				return true;

			while (!uncheckNodes.isEmpty())
			{
				SearchPoint result = uncheckNodes.remove(uncheckNodes.size() - 1);

				Node leftChild = result.getLastMatchingNode().leftChild;
				generateSearchNode(result, uncheckNodes, leftChild);

				Node rightChild = result.getLastMatchingNode().rightChild;
				generateSearchNode(result, uncheckNodes, rightChild);

				if (result.getLastMatchingNode().valueEnd)
				{
					nextResult = (T) converter.readFromBytes(result.leftOver.label);
					return true;
				}
			}

			return false;
		}

		public T next()
		{
			hasNext();
			T result = nextResult;
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

	@Override
	public Iterator<T> iterator(T from, T to)
	{
		if (from == null)
			throw new IllegalArgumentException("The trie doesn't store null values.");

		// Find the first node that matches the entire prefix.
		initByteConverterIfNecessary(from);
		byte[] bytesToFind = converter.getBytes(from);

		return new TrieIterator(bytesToFind);
	}

	@Override
	public Iterator<T> iterator()
	{
		return new TrieIterator(new byte[0]);
	}

	@Override
	public SplittableSet<T> split(T x)
	{
		// TODO
		return null;
	}

	@Override
	public boolean merge(SplittableSet<T> t)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public T locateMiddleValue()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public T floor(T val)
	{
		// TODO Auto-generated method stub
		return null;
	}
}
