package ods.string.search;

public interface BstTreeIndexLayout
{
	long getLeftChildIndex(long nodeIndex);

	long getRightChildIndex(long nodeIndex);

	long getParentIndex(long nodeIndex);
}
