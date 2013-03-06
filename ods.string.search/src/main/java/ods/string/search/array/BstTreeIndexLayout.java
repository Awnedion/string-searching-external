package ods.string.search.array;

public interface BstTreeIndexLayout
{
	long getLeftChildIndex(long nodeIndex);

	long getRightChildIndex(long nodeIndex);

	long getParentIndex(long nodeIndex);
}
