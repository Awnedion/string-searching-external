package ods.string.search;

import java.util.Iterator;


public interface PrefixSearchableSet<T> extends Iterable<T>
{
	boolean add(T u);

	boolean remove(T x);

	boolean contains(T x);

	long size();

	Iterator<T> iterator(T from, T to);
}
