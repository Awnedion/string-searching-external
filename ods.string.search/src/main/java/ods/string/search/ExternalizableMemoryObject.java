package ods.string.search;

import java.io.Serializable;

public interface ExternalizableMemoryObject extends Serializable
{
	long getByteSize();

	boolean isDirty();
}
