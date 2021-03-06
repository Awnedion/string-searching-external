package ods.string.search.partition.splitsets;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

public class ExternalizableArrayList<T extends Serializable> extends ArrayList<T> implements
		ExternalMemoryList<T>
{
	private static final long serialVersionUID = 5282627367797663843L;

	private static final int BYTES_PER_NODE = 8;

	private long dataBytesEstimate = 0;
	private transient boolean dirty = true;
	private int bytesPerNodeWithData = -1;

	public ExternalizableArrayList()
	{
		super();
	}

	public ExternalizableArrayList(Collection<T> c)
	{
		for (T elem : c)
		{
			add(size(), elem);
		}
	}

	@Override
	public long getByteSize()
	{
		// 16 class object, 16 for child variables, 40 arraylist variables
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
			bytesPerNodeWithData = Treap.getObjectBaseSize(element) + BYTES_PER_NODE;
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
