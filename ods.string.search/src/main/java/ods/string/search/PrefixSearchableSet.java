package ods.string.search;


public interface PrefixSearchableSet<T>
{
	boolean add(T u);

	boolean remove(T x);

	boolean contains(T x);

	long size();
}
