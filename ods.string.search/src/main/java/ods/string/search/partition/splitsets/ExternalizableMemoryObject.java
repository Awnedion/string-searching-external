package ods.string.search.partition.splitsets;

import java.io.Serializable;

/**
 * Any object that will support being cached to disk via the ExternalMemoryObjectCache must
 * implement this interface.
 */
public interface ExternalizableMemoryObject extends Serializable
{
	/**
	 * Returns the size in bytes that this object takes while stored in memory.
	 */
	long getByteSize();

	/**
	 * Returns true if this object has been modified since being loaded, false otherwise.
	 */
	boolean isDirty();
}
