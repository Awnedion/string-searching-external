package ods.string.search;

import java.util.Iterator;

/**
 * This interface defines the contract for sets that can perform prefix searches as well as dynamic
 * updates.
 */
public interface PrefixSearchableSet<T> extends Iterable<T>
{
	/**
	 * Adds an element to the set if it doesn't already exist.
	 * 
	 * @param u
	 *            The element to add.
	 * @return True if an element was inserted, false if the element was already in the set.
	 */
	boolean add(T u);

	/**
	 * Removes an element from the set if it exists.
	 * 
	 * @param x
	 *            The element to remove.
	 * @return True if an element was removed, false if no such element existed.
	 */
	boolean remove(T x);

	/**
	 * @return True if the specified element exists in the set, false otherwise.
	 */
	boolean contains(T x);

	/**
	 * @return The number of elements stored within the set.
	 */
	long size();

	/**
	 * Returns an iterator that performs a range search between the two element values specified.
	 * 
	 * @param from
	 *            The value to start range scanning from (inclusive). null will match from the first
	 *            element.
	 * @param to
	 *            The value to end range scanning at (exclusive). null will match all remaining
	 *            elements.
	 */
	Iterator<T> iterator(T from, T to);

	/**
	 * Performs any necessary cleanup operations. Should be called when ending use of the set.
	 */
	void close();
}
