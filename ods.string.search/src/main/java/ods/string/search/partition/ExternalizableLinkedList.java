package ods.string.search.partition;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.LinkedList;

public class ExternalizableLinkedList<T extends Serializable> extends LinkedList<T> implements
		ExternalizableMemoryObject
{
	private static final long serialVersionUID = 5282627367797663843L;

	private static final int BYTES_PER_NODE = 40;

	protected String nextPartitionId;
	protected String prevPartitionId;

	private long dataBytesEstimate = 0;
	private transient boolean dirty = true;
	private int bytesPerNodeWithData = -1;

	public ExternalizableLinkedList()
	{
		super();
	}

	@Override
	public long getByteSize()
	{
		return size() * bytesPerNodeWithData + (dataBytesEstimate << 1) + 72;
	}

	@Override
	public boolean isDirty()
	{
		return dirty;
	}

	public T remove(int index)
	{
		T result = super.remove(index);
		dataBytesEstimate -= result.toString().length();
		dirty = true;
		return result;
	}

	public void add(int index, T element)
	{
		if (bytesPerNodeWithData == -1)
			bytesPerNodeWithData = Treap.getObjectBaseSize(element.getClass()) + BYTES_PER_NODE;
		dirty = true;
		dataBytesEstimate += element.toString().length();
		super.add(index, element);
	}

	private void readObject(ObjectInputStream inputStream) throws IOException,
			ClassNotFoundException
	{
		inputStream.defaultReadObject();
		dirty = false;
	}

}
