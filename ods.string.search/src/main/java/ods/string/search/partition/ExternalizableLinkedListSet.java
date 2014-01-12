package ods.string.search.partition;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ExternalizableLinkedListSet<T extends Serializable & Comparable<T>> implements
		SplittableSet<T>
{
	private static final long serialVersionUID = -5606125403006815540L;

	private ExternalizableLinkedList<T> linkedList;

	public ExternalizableLinkedListSet()
	{
		linkedList = new ExternalizableLinkedList<T>();
	}

	public ExternalizableLinkedListSet(ExternalizableLinkedList<T> list)
	{
		linkedList = list;
	}

	@Override
	public boolean remove(T x)
	{
		int index = Collections.binarySearch(linkedList, x);
		if (index >= 0)
			linkedList.remove(index);
		else
			return false;
		return true;
	}

	@Override
	public boolean contains(T x)
	{
		int index = Collections.binarySearch(linkedList, x);
		return index >= 0;
	}

	@Override
	public Iterator<T> iterator(T from, T to)
	{
		int fromIndex = Collections.binarySearch(linkedList, from);
		if (fromIndex < 0)
			fromIndex = Math.abs(fromIndex) - 1;
		int toIndex = Collections.binarySearch(linkedList, to);
		if (toIndex < 0)
			toIndex = Math.abs(Collections.binarySearch(linkedList, to)) - 1;
		return linkedList.subList(fromIndex, toIndex).iterator();
	}

	@Override
	public SplittableSet<T> split(T x)
	{
		int splitIndex = Math.abs(Collections.binarySearch(linkedList, x));
		List<T> suffixList = linkedList.subList(splitIndex, linkedList.size());
		ExternalizableLinkedListSet<T> result = new ExternalizableLinkedListSet<T>(
				new ExternalizableLinkedList<T>(suffixList));

		int elementsToRemove = suffixList.size();
		for (int y = 0; y < elementsToRemove; y++)
			linkedList.remove(linkedList.size() - 1);
		return result;
	}

	@Override
	public boolean merge(SplittableSet<T> t)
	{
		ExternalizableLinkedListSet<T> higherSet = (ExternalizableLinkedListSet<T>) t;
		if (higherSet.linkedList.getFirst().compareTo(linkedList.getLast()) < 0)
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
		int index = Collections.binarySearch(linkedList, val);
		if (index < 0)
			index = Math.abs(index) - 1;
		return linkedList.get(index);
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
		int index = Collections.binarySearch(linkedList, u);
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
}
