package ods.string.search.partition;

import java.io.Serializable;

public interface ExternalizableMemoryObject extends Serializable
{
	long getByteSize();

	boolean isDirty();
}
