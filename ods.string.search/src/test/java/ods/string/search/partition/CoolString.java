package ods.string.search.partition;

import ods.string.search.partition.ExternalizableMemoryObject;

public class CoolString implements ExternalizableMemoryObject
{
	private static final long serialVersionUID = -7796104766706881675L;

	private String data;

	public CoolString(String s)
	{
		data = s;
	}

	@Override
	public long getByteSize()
	{
		return data.length();
	}

	@Override
	public boolean isDirty()
	{
		return true;
	}

	public boolean equals(Object o)
	{
		if (o == null || !(o instanceof CoolString))
			return false;

		CoolString str = (CoolString) o;
		return data.equals(str.data);
	}

}
