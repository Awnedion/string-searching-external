package ods.string.search;

public class BasicIndexLayout implements BstTreeIndexLayout
{

	@Override
	public long getLeftChildIndex(long nodeIndex)
	{
		return (nodeIndex + 1) * 2 - 1;
	}

	@Override
	public long getRightChildIndex(long nodeIndex)
	{
		return (nodeIndex + 1) * 2;
	}

	@Override
	public long getParentIndex(long nodeIndex)
	{
		return (nodeIndex + 1) / 2 - 1;
	}

}
