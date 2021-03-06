package ods.string.search.partition.splitsets;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Random;

public class Treap<T extends Comparable<T> & Serializable> implements SplittableSet<T>
{
	private static final int BYTES_PER_NODE = 56;

	private static final long serialVersionUID = -3580605497396945943L;

	@SuppressWarnings("rawtypes")
	private static class DefaultComparator implements Comparator, Serializable
	{
		private static final long serialVersionUID = 7278018401326439622L;

		@SuppressWarnings("unchecked")
		public int compare(Object a, Object b)
		{
			return ((Comparable) a).compareTo(b);
		}
	};

	/**
	 * A random number source
	 */
	private transient Random rand = new Random();

	private transient boolean dirty = true;
	private long dataBytesEstimate = 0;
	private transient int bytesPerNodeWithData = -1;

	/**
	 * The root of this tree
	 */
	protected transient Node<T> r;

	@SuppressWarnings("unchecked")
	private Comparator<T> c = new DefaultComparator();

	protected static class Node<T>
	{

		public Node<T> left;
		public Node<T> right;
		public Node<T> parent;
		public T x;
		public int p;
		public int size;

		public void writeExternal(ObjectOutput out) throws IOException
		{
			// Write the elements in sorted order.
			if (left != null)
				left.writeExternal(out);

			out.writeObject(x);

			if (right != null)
				right.writeExternal(out);
		}
	}

	public Treap()
	{
	}

	public Treap(Treap<T> template)
	{
		c = template.c;
	}

	public Treap(Comparator<T> c)
	{
		this.c = c;
	}

	public boolean add(T x)
	{
		return add(x, r);
	}

	protected boolean add(T x, Node<T> startingNode)
	{
		Node<T> u = newNode();
		u.size = 1;
		u.x = x;
		u.p = rand.nextInt();
		if (bytesPerNodeWithData == -1)
			bytesPerNodeWithData = getObjectBaseSize(x) + BYTES_PER_NODE;
		return add(u, startingNode);
	}

	private Node<T> newNode()
	{
		Node<T> u = new Node<T>();
		return u;
	}

	protected Node<T> newNode(T x)
	{
		Node<T> u = newNode();
		u.x = x;
		return u;
	}

	/**
	 * Add a new value
	 * 
	 * @param x
	 * @return
	 */
	private boolean insert(Node<T> u, Node<T> startingNode)
	{
		Node<T> p = findLast(u.x, startingNode);
		return addChild(p, u);
	}

	/**
	 * Add the node u as a child of node p -- ASSUMES p has no child where u should be added
	 * 
	 * @param p
	 * @param u
	 * @return true if the child was added, false otherwise
	 */
	private boolean addChild(Node<T> p, Node<T> u)
	{
		if (p == null)
		{
			r = u; // inserting into empty tree
		} else
		{
			int comp = c.compare(u.x, p.x);
			if (comp < 0)
			{
				p.left = u;
			} else if (comp > 0)
			{
				p.right = u;
			} else
			{
				return false; // u.x is already in the tree
			}
			u.parent = p;
		}
		return true;
	}

	private boolean add(Node<T> u, Node<T> startingNode)
	{
		u.size = 1;
		if (insert(u, startingNode))
		{
			Node<T> temp = u.parent;
			while (temp != null)
			{
				temp.size++;
				temp = temp.parent;
			}
			bubbleUp(u);
			dirty = true;
			dataBytesEstimate += u.x.toString().length();
			return true;
		}
		return false;
	}

	/**
	 * Do a left rotation at u
	 * 
	 * @param u
	 */
	protected void rotateLeft(Node<T> u)
	{
		Node<T> w = u.right;
		w.parent = u.parent;
		if (w.parent != null)
		{
			if (w.parent.left == u)
			{
				w.parent.left = w;
			} else
			{
				w.parent.right = w;
			}
		}
		u.right = w.left;
		if (u.right != null)
		{
			u.right.parent = u;
		}
		u.parent = w;
		w.left = u;
		if (u == r)
		{
			r = w;
			r.parent = null;
		}

		u.parent.size++;
		if (u.left != null)
		{
			u.parent.size += u.left.size;
		}
		u.size--;
		if (u.parent.right != null)
		{
			u.size -= u.parent.right.size;
		}
	}

	/**
	 * Do a right rotation at u
	 * 
	 * @param u
	 */
	protected void rotateRight(Node<T> u)
	{
		Node<T> w = u.left;
		w.parent = u.parent;
		if (w.parent != null)
		{
			if (w.parent.left == u)
			{
				w.parent.left = w;
			} else
			{
				w.parent.right = w;
			}
		}
		u.left = w.right;
		if (u.left != null)
		{
			u.left.parent = u;
		}
		u.parent = w;
		w.right = u;
		if (u == r)
		{
			r = w;
			r.parent = null;
		}

		u.parent.size++;
		if (u.right != null)
		{
			u.parent.size += u.right.size;
		}
		u.size--;
		if (u.parent.left != null)
		{
			u.size -= u.parent.left.size;
		}
	}

	public long size()
	{
		if (r == null)
		{
			return 0;
		}
		return r.size;
	}

	protected void bubbleUp(Node<T> u)
	{
		while (u.parent != null && u.parent.p > u.p)
		{
			if (u.parent.right == u)
			{
				rotateLeft(u.parent);
			} else
			{
				rotateRight(u.parent);
			}
		}
		if (u.parent == null)
		{
			r = u;
		}
	}

	public boolean remove(T x)
	{
		Node<T> u = findLast(x);
		if (u != null && c.compare(u.x, x) == 0)
		{
			trickleDown(u);
			splice(u);
			dirty = true;
			dataBytesEstimate -= x.toString().length();
			return true;
		}
		return false;
	}

	protected void splice(Node<T> u)
	{
		Node<T> s, p;
		if (u.left != null)
		{
			s = u.left;
		} else
		{
			s = u.right;
		}
		if (u == r)
		{
			r = s;
			p = null;
		} else
		{
			p = u.parent;
			if (p.left == u)
			{
				p.left = s;
			} else
			{
				p.right = s;
			}
		}
		if (s != null)
		{
			s.parent = p;
		}

		s = u;
		while (s.parent != null)
		{
			s = s.parent;
			s.size--;
		}
	}

	/**
	 * Do rotations to make u a leaf
	 */
	protected void trickleDown(Node<T> u)
	{
		while (u.left != null || u.right != null)
		{
			if (u.left == null)
			{
				rotateLeft(u);
			} else if (u.right == null)
			{
				rotateRight(u);
			} else if (u.left.p < u.right.p)
			{
				rotateRight(u);
			} else
			{
				rotateLeft(u);
			}
			if (r == u)
			{
				r = u.parent;
			}
		}
	}

	/**
	 * Remove all elements greater than or equal to x from this treap. Return a new treap that
	 * contains all the elements greater or equal to x
	 * 
	 * @param x
	 * @return a Treap containing all elements greater than x
	 */
	public SplittableSet<T> split(T x)
	{
		if (r == null)
			return new Treap<T>();

		Node<T> treeNode = findLast(x);
		Treap<T> t = new Treap<T>(c);
		if (treeNode.x.equals(x))
		{
			int origP = treeNode.p;
			treeNode.p = Integer.MIN_VALUE;
			bubbleUp(treeNode);
			r = treeNode.left;
			if (r != null)
			{
				r.parent = null;
			}
			t.r = treeNode;
			if (t.r.left != null)
			{
				t.r.size -= t.r.left.size;
			}
			treeNode.left = null;
			treeNode.p = origP;
			t.trickleDown(treeNode);
			t.bubbleUp(treeNode);
		} else
		{
			Node<T> splitNode = newNode(x);
			splitNode.p = Integer.MIN_VALUE;
			splitNode.size = 1;
			add(splitNode, r);
			bubbleUp(splitNode);
			r = splitNode.left;
			if (r != null)
			{
				r.parent = null;
			}
			t.r = splitNode.right;
			if (t.r != null)
			{
				t.r.parent = null;
			}
		}

		dirty = true;
		t.dirty = true;

		int leftSize = (r == null ? 0 : r.size);
		int rightSize = (t.r == null ? 0 : t.r.size);
		t.dataBytesEstimate = (long) ((double) rightSize / (leftSize + rightSize) * dataBytesEstimate);
		dataBytesEstimate = (long) Math.ceil((double) leftSize / (leftSize + rightSize)
				* dataBytesEstimate);
		return t;
	}

	/**
	 * Search for a value in the tree
	 * 
	 * @return the last node on the search path for x
	 */
	protected Node<T> findLast(T x)
	{
		return findLast(x, r);
	}

	private Node<T> findLast(T x, Node<T> initialNode)
	{
		Node<T> w = initialNode, prev = null;
		while (w != null)
		{
			prev = w;
			int comp = c.compare(x, w.x);
			if (comp < 0)
			{
				w = w.left;
			} else if (comp > 0)
			{
				w = w.right;
			} else
			{
				return w;
			}
		}
		return prev;
	}

	/**
	 * Merge this treap and t into a single treap, emptying t in the process Precondition: Every
	 * element in t is greater than every element in this treap
	 * 
	 * @param t
	 * @return true if successful and false if the precondition is not satisfied
	 */
	public boolean merge(SplittableSet<T> set)
	{
		Treap<T> t = (Treap<T>) set;

		if (t.r == null)
		{
			return true;
		} else if (r == null)
		{
			Node<T> temp = r;
			r = t.r;
			t.r = temp;
			dirty = true;
			t.dirty = true;
			dataBytesEstimate = t.dataBytesEstimate;
			t.dataBytesEstimate = 0;
			return true;
		}

		Node<T> largestNode = r;
		while (largestNode.right != null)
		{
			largestNode = largestNode.right;
		}
		Node<T> smallestNode = t.r;
		while (smallestNode.left != null)
		{
			smallestNode = smallestNode.left;
		}
		if (c.compare(smallestNode.x, largestNode.x) <= 0)
		{
			return false;
		}

		Node<T> newRoot = newNode();
		newRoot.left = r;
		newRoot.right = t.r;
		newRoot.size = r.size + t.r.size;
		r.parent = newRoot;
		t.r.parent = newRoot;
		r = newRoot;
		newRoot.p = Integer.MAX_VALUE;
		trickleDown(newRoot);
		splice(newRoot);
		t.r = null;
		dirty = true;
		t.dirty = true;
		dataBytesEstimate += t.dataBytesEstimate;
		t.dataBytesEstimate = 0;

		return true;
	}

	/**
	 * Return the item in the treap whose rank is i - This is the item x such that the treap
	 * contains exactly i element less than x.
	 * 
	 * @param i
	 * @return
	 */
	public T get(int i)
	{
		if (i < 0 || i >= r.size)
		{
			return null;
		}

		int currentRank = 0;
		Node<T> currentNode = r;
		while (currentNode != null)
		{
			int leftSize = 0;
			if (currentNode.left != null)
			{
				leftSize = currentNode.left.size;
			}

			if (i < currentRank + leftSize)
			{
				currentNode = currentNode.left;
			} else if (i > currentRank + leftSize)
			{
				currentRank += leftSize + 1;
				currentNode = currentNode.right;
			} else
			{
				return currentNode.x;
			}
		}
		return null;
	}

	protected class BTI implements Iterator<T>
	{
		protected Node<T> w, prev;
		protected T endValue;

		public BTI(Node<T> iw, T endValue)
		{
			w = iw;
			this.endValue = endValue;
		}

		public boolean hasNext()
		{
			return w != null && (endValue == null || c.compare(w.x, endValue) < 0);
		}

		public T next()
		{
			T x = w.x;
			prev = w;
			w = nextNode(w);
			return x;
		}

		public void remove()
		{
			Treap.this.remove(prev.x);
		}
	}

	public Iterator<T> iterator(Node<T> u, T endValue)
	{
		return new BTI(u, endValue);
	}

	public Iterator<T> iterator()
	{
		return iterator(firstNode(), null);
	}

	public Iterator<T> iterator(T x)
	{
		return iterator(findGENode(x), null);
	}

	public Iterator<T> iterator(T startValue, T endValue)
	{
		if (endValue == null || c.compare(startValue, endValue) < 0)
			return iterator(findGENode(startValue), endValue);
		else
			return iterator(findGENode(endValue), startValue);
	}

	/**
	 * Find the first node in an in-order traversal
	 * 
	 * @return the first node reported in an in-order traversal
	 */
	public Node<T> firstNode()
	{
		Node<T> w = r;
		if (w == null)
			return null;
		while (w.left != null)
			w = w.left;
		return w;
	}

	/**
	 * Search for a value in the tree
	 * 
	 * @return the last node on the search path for x
	 */
	protected Node<T> findGENode(T x)
	{
		Node<T> w = r, z = null;
		while (w != null)
		{
			int comp = c.compare(x, w.x);
			if (comp < 0)
			{
				z = w;
				w = w.left;
			} else if (comp > 0)
			{
				w = w.right;
			} else
			{
				return w;
			}
		}
		return z;
	}

	/**
	 * Search for a value equal to the specified value in the tree. If the specified value doesn't
	 * exist, return the closest value that is less than the specified value.
	 */
	protected Node<T> findLENode(T x)
	{
		Node<T> w = r, z = null;
		while (w != null)
		{
			int comp = c.compare(x, w.x);
			if (comp < 0)
			{
				w = w.left;
			} else if (comp > 0)
			{
				z = w;
				w = w.right;
			} else
			{
				return w;
			}
		}
		return z;
	}

	/**
	 * Find the node that follows w in an in-order traversal
	 * 
	 * @param w
	 * @return the node that follows w in an in-order traversal
	 */
	public Node<T> nextNode(Node<T> w)
	{
		if (w.right != null)
		{
			w = w.right;
			while (w.left != null)
				w = w.left;
		} else
		{
			while (w.parent != null && w.parent.left != w)
				w = w.parent;
			w = w.parent;
		}
		return w;
	}

	/**
	 * Find the node that follows w in a reverse in-order traversal
	 * 
	 * @param w
	 * @return the node that follows w in a reverse in-order traversal
	 */
	public Node<T> prevNode(Node<T> w)
	{
		if (w.left != null)
		{
			w = w.left;
			while (w.right != null)
				w = w.right;
		} else
		{
			while (w.parent != null && w.parent.right != w)
				w = w.parent;
			w = w.parent;
		}
		return w;
	}

	public boolean contains(T x)
	{
		Node<T> n = findLast(x);
		return n.x.equals(x);
	}

	public String toString()
	{
		return r.x + " " + r.size;
	}

	private void writeObject(ObjectOutputStream s) throws IOException
	{
		// Write out the Comparator and any hidden stuff
		s.defaultWriteObject();

		// Write out size (number of Mappings)
		s.writeInt(r != null ? r.size : 0);

		if (r != null)
			r.writeExternal(s);
	}

	private void readObject(ObjectInputStream inputStream) throws IOException,
			ClassNotFoundException
	{
		inputStream.defaultReadObject();
		dirty = false;
		rand = new Random();

		int size = inputStream.readInt();
		int height = (int) (Math.log10(size) / Math.log10(2));
		if (size > 0)
			r = constuctNode(inputStream, height, 0, size);

		if (r != null)
			bytesPerNodeWithData = getObjectBaseSize(r.x) + BYTES_PER_NODE;
		else
			bytesPerNodeWithData = -1;
	}

	@SuppressWarnings("unchecked")
	private Node<T> constuctNode(ObjectInput in, int height, int curDepth, int sizeLeft)
			throws IOException, ClassNotFoundException
	{
		Node<T> newNode = new Node<T>();
		newNode.size = 1;
		if (curDepth < height && sizeLeft > newNode.size)
		{
			Node<T> leftChild = constuctNode(in, height, curDepth + 1, sizeLeft - newNode.size);
			newNode.left = leftChild;
			leftChild.parent = newNode;
			newNode.size += leftChild.size;
		}

		T elem = (T) in.readObject();
		newNode.x = elem;
		newNode.p = (int) (Integer.MIN_VALUE + ((long) Integer.MAX_VALUE - Integer.MIN_VALUE)
				* (curDepth + 1) / (height + 2));

		if (curDepth < height && sizeLeft > newNode.size)
		{
			Node<T> rightChild = constuctNode(in, height, curDepth + 1, sizeLeft - newNode.size);
			newNode.right = rightChild;
			rightChild.parent = newNode;
			newNode.size += rightChild.size;
		}

		return newNode;
	}

	@Override
	public long getByteSize()
	{
		// 16 base object, 24 treap variables, 24 comparator, 32 rand
		return (r == null ? 0 : r.size) * bytesPerNodeWithData + (dataBytesEstimate << 1) + 96;
	}

	public static int getObjectBaseSize(Object obj)
	{
		if (obj instanceof String)
			return 64;
		else if (obj instanceof ExternalizableMemoryObject)
			return (int) ((ExternalizableMemoryObject) obj).getByteSize();
		else
			return 24;
	}

	@Override
	public boolean isDirty()
	{
		return dirty;
	}

	public T locateMiddleValue()
	{
		Node<T> curNode = r;
		int idealSize = r.size / 2;
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
	public T floor(T val)
	{
		Node<T> result = findLENode(val);
		return result == null ? null : result.x;
	}

	@Override
	public void close()
	{
	}

	@Override
	public SplittableSet<T> createNewSet()
	{
		return new Treap<T>(this);
	}

	@Override
	public T lower(T val)
	{
		Node<T> node = findLast(val);
		if (node.x.compareTo(val) < 0)
			return node.x;
		else
			node = prevNode(node);

		if (node != null)
			return node.x;
		return null;
	}

	@Override
	public T higher(T val)
	{
		Node<T> node = findLast(val);
		if (node.x.compareTo(val) > 0)
			return node.x;
		else
			node = nextNode(node);

		if (node != null)
			return node.x;
		return null;
	}
}
