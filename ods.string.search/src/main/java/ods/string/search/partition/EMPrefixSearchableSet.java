package ods.string.search.partition;

import java.io.File;
import java.io.Serializable;

import ods.string.search.PrefixSearchableSet;
import ods.string.search.partition.splitsets.ExternalizableMemoryObject;

public interface EMPrefixSearchableSet<T extends Comparable<T> & Serializable> extends
		PrefixSearchableSet<T>
{

	EMPrefixSearchableSet<T> createNewStructure(File newStorageDir);

	ExternalMemoryObjectCache<? extends ExternalizableMemoryObject> getObjectCache();

}
