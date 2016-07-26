package ods.string.search;

import java.io.File;
import java.io.Serializable;
import java.util.Iterator;

import ods.string.search.partition.EMPrefixSearchableSet;
import ods.string.search.partition.ExternalMemoryObjectCache;
import ods.string.search.partition.splitsets.ExternalizableMemoryObject;

public class NullSet<T extends Comparable<T> & Serializable> implements EMPrefixSearchableSet<T>
{
	private class NulIterator implements Iterator<T>
	{

		@Override
		public boolean hasNext()
		{
			return false;
		}

		@Override
		public T next()
		{
			return null;
		}

		@Override
		public void remove()
		{
		}

	}

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
		return new NulIterator();
	}

	@Override
	public void close()
	{
	}

	@Override
	public EMPrefixSearchableSet<T> createNewStructure(File newStorageDir)
	{
		return new NullSet<T>();
	}

	@Override
	public ExternalMemoryObjectCache<? extends ExternalizableMemoryObject> getObjectCache()
	{
		return new ExternalMemoryObjectCache<>(new File("target/tmp"));
	}

}
