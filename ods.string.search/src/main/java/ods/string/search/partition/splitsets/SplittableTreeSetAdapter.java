package ods.string.search.partition.splitsets;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class SplittableTreeSetAdapter<T extends Comparable<T> & Serializable> implements
		SplittableSet<T>, Serializable
{
	private static final long serialVersionUID = 2042335756796153072L;

	private static final int BYTES_PER_NODE = 64;

	private TreeSet<T> adaptee;
	private transient boolean dirty = true;
	private int bytesPerNodeWithData = -1;
	private long dataBytesEstimate = 0;

	public SplittableTreeSetAdapter()
	{
		adaptee = new TreeSet<T>();
	}

	public SplittableTreeSetAdapter(SplittableTreeSetAdapter<T> template)
	{
		adaptee = new TreeSet<T>();
	}

	public SplittableTreeSetAdapter(Collection<T> elems)
	{
		adaptee = new TreeSet<T>(elems);
		bytesPerNodeWithData = getObjectBaseSize(elems.iterator().next()) + BYTES_PER_NODE;
		for (Iterator<T> iter = elems.iterator(); iter.hasNext();)
			dataBytesEstimate += iter.next().toString().length();
	}

	@Override
	public long getByteSize()
	{
		// 16 base object, 24 adapter variables, 24 treeSet variables, 40 treeMap
		return adaptee.size() * bytesPerNodeWithData + (dataBytesEstimate << 1) + 104;
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

	@Override
	public boolean add(T u)
	{
		if (bytesPerNodeWithData == -1)
			bytesPerNodeWithData = getObjectBaseSize(u) + BYTES_PER_NODE;
		boolean result = adaptee.add(u);
		if (result)
		{
			dirty = true;
			dataBytesEstimate += u.toString().length();
		}
		return result;
	}

	@Override
	public boolean remove(T x)
	{
		boolean result = adaptee.remove(x);
		if (result)
		{
			dirty = true;
			dataBytesEstimate -= x.toString().length();
		}
		return result;
	}

	@Override
	public boolean contains(T x)
	{
		return adaptee.contains(x);
	}

	@Override
	public long size()
	{
		return adaptee.size();
	}

	@Override
	public Iterator<T> iterator(T from, T to)
	{
		boolean endInclusive = false;
		if (from == null)
			from = adaptee.first();
		if (to == null)
		{
			to = adaptee.last();
			endInclusive = true;
		}

		return adaptee.subSet(from, true, to, endInclusive).iterator();
	}

	@Override
	public Iterator<T> iterator()
	{
		return adaptee.iterator();
	}

	@Override
	public SplittableSet<T> split(T x)
	{
		Set<T> subset = adaptee.tailSet(x);
		SplittableTreeSetAdapter<T> result = new SplittableTreeSetAdapter<T>(subset);
		adaptee.removeAll(result.adaptee);
		if (result.size() > 0)
		{
			dirty = true;
			result.dirty = true;
			result.dataBytesEstimate = (long) ((double) result.adaptee.size()
					/ (adaptee.size() + result.adaptee.size()) * dataBytesEstimate);
			dataBytesEstimate = (long) Math.ceil((double) adaptee.size()
					/ (adaptee.size() + result.adaptee.size()) * dataBytesEstimate);
		}

		return result;
	}

	@Override
	public boolean merge(SplittableSet<T> t)
	{
		SplittableTreeSetAdapter<T> set = (SplittableTreeSetAdapter<T>) t;
		if (adaptee.last().compareTo(set.adaptee.first()) >= 0)
			return false;
		if (set.adaptee.size() > 0)
		{
			dirty = true;
			set.dirty = true;
			dataBytesEstimate += set.dataBytesEstimate;
			set.dataBytesEstimate = 0;
		}
		adaptee.addAll(set.adaptee);
		return true;
	}

	@Override
	public T locateMiddleValue()
	{
		int halfwaySize = adaptee.size() / 2;
		Iterator<T> iter = adaptee.iterator();
		for (int x = 0; x < halfwaySize; x++)
			iter.next();
		return iter.next();
	}

	private void readObject(ObjectInputStream inputStream) throws IOException,
			ClassNotFoundException
	{
		inputStream.defaultReadObject();
		dirty = false;
	}

	@Override
	public T floor(T val)
	{
		return adaptee.floor(val);
	}

	@Override
	public void close()
	{
	}

	@Override
	public SplittableSet<T> createNewSet()
	{
		return new SplittableTreeSetAdapter<T>(this);
	}
}
