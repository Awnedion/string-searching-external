package ods.string.search;

import java.io.Serializable;
import java.util.Iterator;

public class NullSet<T extends Comparable<T> & Serializable> implements PrefixSearchableSet<T>
{

	@Override
	public Iterator<T> iterator()
	{
		return null;
	}

	@Override
	public boolean add(T u)
	{
		return false;
	}

	@Override
	public boolean remove(T x)
	{
		return false;
	}

	@Override
	public boolean contains(T x)
	{
		return false;
	}

	@Override
	public long size()
	{
		return 0;
	}

	@Override
	public Iterator<T> iterator(T from, T to)
	{
		return null;
	}

	@Override
	public void close()
	{
	}

}
