package ods.string.search;


public class VebIndexLayout implements BstTreeIndexLayout
{

	@Override
	public long getLeftChildIndex(long nodeIndex)
	{
		long[] indices = getLayoutIndices(nodeIndex);
		long lastIndex = 0;
		int shift = 1;
		for (int x = indices.length - 1; x >= 0; x--)
		{
			long mask = 1l << shift;
			if (indices[x] == -1)
			{
				indices[x] = lastIndex;
				break;
			} else
			{
				lastIndex += indices[x] * mask;
				indices[x] = -1;
			}
			shift <<= 1;
		}

		return getIndexFromLayoutIndices(indices);
	}

	@Override
	public long getRightChildIndex(long nodeIndex)
	{
		long[] indices = getLayoutIndices(nodeIndex);
		long lastIndex = 0;
		int shift = 1;
		for (int x = indices.length - 1; x >= 0; x--)
		{
			long mask = 1l << shift;
			if (indices[x] == -1)
			{
				indices[x] = lastIndex + 1;
				break;
			} else
			{
				lastIndex += indices[x] * mask;
				indices[x] = -1;
			}
			shift <<= 1;
		}

		return getIndexFromLayoutIndices(indices);
	}

	@Override
	public long getParentIndex(long nodeIndex)
	{
		if (nodeIndex == 0)
			return -1;

		long[] indices = getLayoutIndices(nodeIndex);
		long lastIndexValue = 0;
		int lastIndex = 0;
		for (int x = indices.length - 1; x >= 0; x--)
		{
			if (indices[x] != -1)
			{
				lastIndexValue = indices[x];
				lastIndex = x + 1;
				indices[x] = -1;
				break;
			}
		}
		for (int x = lastIndex; x < indices.length; x++)
		{
			long mask = (long) Math.pow(2, Math.pow(2, indices.length - 1 - x));
			indices[x] = lastIndexValue / mask;
			lastIndexValue %= mask;
		}

		return getIndexFromLayoutIndices(indices);
	}

	private long[] getLayoutIndices(long nodeIndex)
	{
		long temp = nodeIndex;
		long[] indices = new long[6];
		int cur = 0;
		for (int x = 32; x >= 1; x >>= 1)
		{
			long mask = (1l << x);
			long index = -1;
			if (temp >= mask - 1)
			{
				temp -= mask - 1;
				index = temp / (mask - 1);
				temp -= index * (mask - 1);
			}
			indices[cur++] = index;
		}
		return indices;
	}

	private long getIndexFromLayoutIndices(long[] indices)
	{
		long result = 0;
		int cur = indices.length - 1;
		for (int x = 1; x <= 32; x <<= 1)
		{
			long mask = (1l << x);
			if (indices[cur] != -1)
			{
				result += mask - 1;
				result += indices[cur] * (mask - 1);
			}
			cur--;
		}
		return result;
	}

}
