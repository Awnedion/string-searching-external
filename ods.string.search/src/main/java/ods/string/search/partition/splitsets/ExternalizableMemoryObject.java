package ods.string.search.partition.splitsets;

import java.io.Serializable;

public interface ExternalizableMemoryObject extends Serializable
{
	long getByteSize();

	boolean isDirty();
}
