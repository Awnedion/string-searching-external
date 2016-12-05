package ods.string.search.partition;

import java.io.File;
import java.io.Serializable;

import ods.string.search.PrefixSearchableSet;
import ods.string.search.partition.splitsets.ExternalizableMemoryObject;

/**
 * This interface defines a prefix searchable set that also supports caching data to disk when
 * necessary.
 */
public interface EMPrefixSearchableSet<T extends Comparable<T> & Serializable> extends
		PrefixSearchableSet<T>
{

	/**
	 * Returns a new empty set with the same configuration as the current except that the specified
	 * storage directory will be used instead.
	 * 
	 * @param newStorageDir
	 *            The directory that the new set should store data in.
	 */
	EMPrefixSearchableSet<T> createNewStructure(File newStorageDir);

	/**
	 * Returns the ExternalMemoryObjectCache used by this set.
	 */
	ExternalMemoryObjectCache<? extends ExternalizableMemoryObject> getObjectCache();

}
