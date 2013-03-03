package ods.string.search;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Random;

public class Treap<T extends Comparable<T> & Serializable> implements ExternalizableMemoryObject,
		PrefixSearchableSet<T>
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

	@SuppressWarnings("rawtypes")
	private static Node templateNil = null;

	/**
	 * A random number source
	 */
	private Random rand = new Random();

	private transient boolean dirty = false;
	private long dataBytesEstimate = 0;
	private int bytesPerNodeWithData = -1;

	/**
	 * The root of this tree
	 */
	protected Node<T> r;

	@SuppressWarnings("unchecked")
	private Node<T> nil = templateNil;

	@SuppressWarnings("unchecked")
	private Comparator<T> c = new DefaultComparator();

	protected static class Node<T> implements Serializable
	{
		private static final long serialVersionUID = 2416477592385110366L;

		public Node<T> left;
		public Node<T> right;
		public Node<T> parent;
		public T x;
		public int p;
		public int size;
	}

	public Treap()
	{
		r = nil;
	}

	public Treap(Comparator<T> c)
	{
		this.c = c;
		r = nil;
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
		u.parent = u.left = u.right = nil;
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
		if (p == nil)
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
			while (temp != nil)
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
		if (w.parent != nil)
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
		if (u.right != nil)
		{
			u.right.parent = u;
		}
		u.parent = w;
		w.left = u;
		if (u == r)
		{
			r = w;
			r.parent = nil;
		}

		u.parent.size++;
		if (u.left != nil)
		{
			u.parent.size += u.left.size;
		}
		u.size--;
		if (u.parent.right != nil)
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
		if (w.parent != nil)
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
		if (u.left != nil)
		{
			u.left.parent = u;
		}
		u.parent = w;
		w.right = u;
		if (u == r)
		{
			r = w;
			r.parent = nil;
		}

		u.parent.size++;
		if (u.right != nil)
		{
			u.parent.size += u.right.size;
		}
		u.size--;
		if (u.parent.left != nil)
		{
			u.size -= u.parent.left.size;
		}
	}

	public long size()
	{
		if (r == nil)
		{
			return 0;
		}
		return r.size;
	}

	protected void bubbleUp(Node<T> u)
	{
		while (u.parent != nil && u.parent.p > u.p)
		{
			if (u.parent.right == u)
			{
				rotateLeft(u.parent);
			} else
			{
				rotateRight(u.parent);
			}
		}
		if (u.parent == nil)
		{
			r = u;
		}
	}

	public boolean remove(T x)
	{
		Node<T> u = findLast(x);
		if (u != nil && c.compare(u.x, x) == 0)
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
		if (u.left != nil)
		{
			s = u.left;
		} else
		{
			s = u.right;
		}
		if (u == r)
		{
			r = s;
			p = nil;
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
		if (s != nil)
		{
			s.parent = p;
		}

		s = u;
		while (s.parent != nil)
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
		while (u.left != nil || u.right != nil)
		{
			if (u.left == nil)
			{
				rotateLeft(u);
			} else if (u.right == nil)
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
	public Treap<T> split(T x)
	{
		Node<T> treeNode = findLast(x);
		Treap<T> t = new Treap<T>(c);
		if (treeNode.x.equals(x))
		{
			int origP = treeNode.p;
			treeNode.p = Integer.MIN_VALUE;
			bubbleUp(treeNode);
			r = treeNode.left;
			if (r != nil)
			{
				r.parent = nil;
			}
			t.r = treeNode;
			if (t.r.left != nil)
			{
				t.r.size -= t.r.left.size;
			}
			treeNode.left = nil;
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
			if (r != nil)
			{
				r.parent = nil;
			}
			t.r = splitNode.right;
			if (t.r != nil)
			{
				t.r.parent = nil;
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
		Node<T> w = initialNode, prev = nil;
		while (w != nil)
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
	public boolean merge(Treap<T> t)
	{
		if (t.r == nil)
		{
			return true;
		} else if (r == nil)
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
		while (largestNode.right != nil)
		{
			largestNode = largestNode.right;
		}
		Node<T> smallestNode = t.r;
		while (smallestNode.left != nil)
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
		t.r = nil;
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
		while (currentNode != nil)
		{
			int leftSize = 0;
			if (currentNode.left != nil)
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

	public Iterator<T> iterator(Node<T> u)
	{
		class BTI implements Iterator<T>
		{
			protected Node<T> w, prev;

			public BTI(Node<T> iw)
			{
				w = iw;
			}

			public boolean hasNext()
			{
				return w != nil;
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
				// FIXME: This is a bug. remove() methods have to be changed
				Treap.this.remove(prev.x);
			}
		}
		return new BTI(u);
	}

	public Iterator<T> iterator()
	{
		return iterator(firstNode());
	}

	public Iterator<T> iterator(T x)
	{
		return iterator(findGENode(x));
	}

	/**
	 * Find the first node in an in-order traversal
	 * 
	 * @return the first node reported in an in-order traversal
	 */
	public Node<T> firstNode()
	{
		Node<T> w = r;
		if (w == nil)
			return nil;
		while (w.left != nil)
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
		Node<T> w = r, z = nil;
		while (w != nil)
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
	 * Find the node that follows w in an in-order traversal
	 * 
	 * @param w
	 * @return the node that follows w in an in-order traversal
	 */
	public Node<T> nextNode(Node<T> w)
	{
		if (w.right != nil)
		{
			w = w.right;
			while (w.left != nil)
				w = w.left;
		} else
		{
			while (w.parent != nil && w.parent.left != w)
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

	private void readObject(ObjectInputStream inputStream) throws IOException,
			ClassNotFoundException
	{
		inputStream.defaultReadObject();
		dirty = false;
	}

	@Override
	public long getByteSize()
	{
		return (r == null ? 0 : r.size) * bytesPerNodeWithData + (dataBytesEstimate << 1);
	}

	private int getObjectBaseSize(T obj)
	{
		if (obj instanceof String)
			return 64;
		else
			return 24;
	}

	@Override
	public boolean isDirty()
	{
		return dirty;
	}
}
