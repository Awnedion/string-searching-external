package ods.string.search.partition.splitsets;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ExternalizableListSet<T extends Serializable & Comparable<T>> implements
		SplittableSet<T>
{
	private static final long serialVersionUID = -5606125403006815540L;

	private ExternalMemoryList<T> linkedList;
	private boolean linearCompare;
	private transient Constructor<ExternalMemoryList<T>> copyConstructor;

	public ExternalizableListSet()
	{
		linkedList = new ExternalizableLinkedList<T>();
		linearCompare = false;
		init();
	}

	@SuppressWarnings("unchecked")
	public ExternalizableListSet(ExternalizableListSet<T> template)
	{
		try
		{
			linkedList = (ExternalMemoryList<T>) template.linkedList.getClass().newInstance();
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		linearCompare = template.linearCompare;
		init();
	}

	public ExternalizableListSet(ExternalMemoryList<T> list, boolean linearCompare)
	{
		linkedList = list;
		this.linearCompare = linearCompare;
		init();
	}

	@SuppressWarnings("unchecked")
	private void init()
	{
		try
		{
			copyConstructor = (Constructor<ExternalMemoryList<T>>) linkedList.getClass()
					.getConstructor(Collection.class);
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean remove(T x)
	{
		int index = findIndex(x);
		if (index >= 0)
			linkedList.remove(index);
		else
			return false;
		return true;
	}

	private int findIndex(T val)
	{
		int index;
		if (!linearCompare)
			index = Collections.binarySearch(linkedList, val);
		else
			index = linearFind(val);
		return index;
	}

	private int linearFind(T val)
	{
		int result = 0;
		Iterator<T> iter = linkedList.iterator();
		while (iter.hasNext())
		{
			int compare = val.compareTo(iter.next());
			if (compare == 0)
				return result;
			else if (compare < 0)
				break;
			result++;
		}
		result = -result - 1;
		return result;
	}

	@Override
	public boolean contains(T x)
	{
		int index = findIndex(x);
		return index >= 0;
	}

	@Override
	public Iterator<T> iterator(T from, T to)
	{
		int fromIndex = findIndex(from);
		if (fromIndex < 0)
			fromIndex = Math.abs(fromIndex) - 1;
		int toIndex = findIndex(to);
		if (toIndex < 0)
			toIndex = Math.abs(toIndex) - 1;
		return linkedList.subList(fromIndex, toIndex).iterator();
	}

	@Override
	public SplittableSet<T> split(T x)
	{
		int splitIndex = findIndex(x);
		if (splitIndex < 0)
			splitIndex = Math.abs(splitIndex) - 1;
		List<T> suffixList = linkedList.subList(splitIndex, linkedList.size());
		ExternalizableListSet<T> result;
		try
		{
			result = new ExternalizableListSet<T>(copyConstructor.newInstance(suffixList),
					linearCompare);
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}

		int elementsToRemove = suffixList.size();
		for (int y = 0; y < elementsToRemove; y++)
			linkedList.remove(linkedList.size() - 1);
		return result;
	}

	@Override
	public boolean merge(SplittableSet<T> t)
	{
		if (t.size() == 0)
			return true;

		ExternalizableListSet<T> higherSet = (ExternalizableListSet<T>) t;
		int thisSize = linkedList.size();
		if (thisSize != 0
				&& higherSet.linkedList.get(0).compareTo(linkedList.get(thisSize - 1)) < 0)
			throw new IllegalArgumentException(
					"The passed in set must contain elements of greater value.");

		for (T elem : t)
		{
			linkedList.add(linkedList.size(), elem);
		}
		return true;
	}

	@Override
	public T locateMiddleValue()
	{
		return linkedList.get(linkedList.size() / 2);
	}

	@Override
	public T floor(T val)
	{
		int index = findIndex(val);
		if (index < 0)
			index = Math.abs(index) - 2;
		return index < 0 ? null : linkedList.get(index);
	}

	@Override
	public long getByteSize()
	{
		return linkedList.getByteSize();
	}

	@Override
	public boolean isDirty()
	{
		return linkedList.isDirty();
	}

	@Override
	public boolean add(T u)
	{
		int index = findIndex(u);
		if (index >= 0)
			return false;
		linkedList.add(Math.abs(index) - 1, u);
		return true;
	}

	@Override
	public long size()
	{
		return linkedList.size();
	}

	@Override
	public Iterator<T> iterator()
	{
		return linkedList.iterator();
	}

	private void readObject(ObjectInputStream inputStream) throws IOException,
			ClassNotFoundException
	{
		inputStream.defaultReadObject();
		init();
	}

	@Override
	public void close()
	{
	}

	@Override
	public SplittableSet<T> createNewSet()
	{
		return new ExternalizableListSet<T>(this);
	}
}
