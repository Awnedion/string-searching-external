package ods.string.search.partition.splitsets;

import java.io.Serializable;

import ods.string.search.PrefixSearchableSet;

public interface SplittableSet<T extends Comparable<T> & Serializable> extends
		ExternalizableMemoryObject, PrefixSearchableSet<T>
{
	SplittableSet<T> split(T x);

	boolean merge(SplittableSet<T> t);

	T locateMiddleValue();

	T floor(T val);

	SplittableSet<T> createNewSet();
}
