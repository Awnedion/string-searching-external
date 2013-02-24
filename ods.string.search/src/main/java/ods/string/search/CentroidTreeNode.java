package ods.string.search;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;

public class CentroidTreeNode implements ExternalMemoryNode
{
	public static class StringLengthComparator implements Comparator<CentroidTreeNode>
	{
		@Override
		public int compare(CentroidTreeNode o1, CentroidTreeNode o2)
		{
			if (o1.getStringBitLength() < o2.getStringBitLength())
				return -1;
			else if (o2.getStringBitLength() < o1.getStringBitLength())
				return 1;
			else
				return 0;
		}
	}

	public static void splitOnPrefix(CentroidTreeNode prefixNode, CentroidTreeNode suffixNode)
	{
		int matchedBits = getCommonPrefixBits(prefixNode, suffixNode);
		prefixNode.bitsUsed = matchedBits;

		byte[] suffixArray = suffixNode.bits.array();
		for (int x = matchedBits; x < suffixNode.bitsUsed; x++)
		{
			short startMask = (short) (128 >> ((x - matchedBits) % 8));
			short endMask = (short) (128 >> (x % 8));
			if ((suffixArray[x / 8] & endMask) != 0)
				suffixArray[(x - matchedBits) / 8] |= startMask;
			else
			{
				startMask ^= 255;
				suffixArray[(x - matchedBits) / 8] &= startMask;
			}
		}
		suffixNode.bitsUsed -= matchedBits;
	}

	public static int getCommonPrefixBits(CentroidTreeNode node1, CentroidTreeNode node2)
	{
		int matchedBits = 0;
		int length1 = (int) Math.ceil(node1.bitsUsed / 8.);
		int length2 = (int) Math.ceil(node2.bitsUsed / 8.);
		for (int x = 0; x < length1 && x < length2; x++)
		{
			if (node1.bits.get(x) == node2.bits.get(x))
			{
				int bitsMatched = 8;
				int bitsRemaining = node1.bitsUsed - x * 8;
				if (bitsRemaining < bitsMatched)
					bitsMatched = bitsRemaining;
				bitsRemaining = node2.bitsUsed - x * 8;
				if (bitsRemaining < bitsMatched)
					bitsMatched = bitsRemaining;
				matchedBits += bitsMatched;
			} else
			{
				short mask = (short) 128;
				for (; mask > 0 && matchedBits < node1.bitsUsed && matchedBits < node2.bitsUsed; mask = (short) (mask >> 1))
				{
					if ((node1.bits.get(x) & mask) == (node2.bits.get(x) & mask))
						matchedBits++;
					else
						break;
				}
				break;
			}
		}
		return matchedBits;
	}

	public static void appendOnNode(CentroidTreeNode prefixNode, CentroidTreeNode suffixNode)
	{
		byte[] prefixArray = prefixNode.bits.array();
		byte[] suffixArray = suffixNode.bits.array();
		for (int x = prefixNode.bitsUsed; x < prefixNode.bitsUsed + suffixNode.bitsUsed; x++)
		{
			short startMask = (short) (128 >> (x % 8));
			short endMask = (short) (128 >> ((x - prefixNode.bitsUsed) % 8));
			if ((suffixArray[(x - prefixNode.bitsUsed) / 8] & endMask) != 0)
				prefixArray[x / 8] |= startMask;
			else
			{
				startMask ^= 255;
				prefixArray[x / 8] &= startMask;
			}
		}
		prefixNode.bitsUsed += suffixNode.bitsUsed;
	}

	private int bitsUsed = 0;
	private ByteBuffer bits;
	private long subtreeSize = 0;
	private boolean valueEnd = false;

	public CentroidTreeNode(int maxStringLength)
	{
		this.bits = ByteBuffer.allocate(maxStringLength);
	}

	public CentroidTreeNode(CentroidTreeNode node)
	{
		bits = ByteBuffer.wrap(Arrays.copyOf(node.bits.array(), node.bits.capacity()));
		bitsUsed = node.bitsUsed;
		subtreeSize = node.subtreeSize;
		valueEnd = node.valueEnd;
	}

	public void setString(String str)
	{
		byte[] bytes = str.getBytes();
		if (bytes.length > bits.capacity())
			throw new IllegalArgumentException("String cannot be longer than " + bits.capacity());

		byte[] charBytes = bits.array();
		for (int x = 0; x < bytes.length; x++)
		{
			charBytes[x] = bytes[x];
		}
		bitsUsed = bytes.length * 8;
	}

	public void setBitsUsed(int bits)
	{
		bitsUsed = bits;
	}

	public void copyString(CentroidTreeNode node)
	{
		if (node == this)
			return;

		bits.clear();
		bits.put(node.bits.array(), 0, (int) Math.ceil(node.bitsUsed / 8.));
		bitsUsed = node.bitsUsed;
	}

	public String toString()
	{
		int length = bitsUsed / 8;
		int mod = bitsUsed % 8;
		String result = new String(bits.array(), 0, length);
		if (mod != 0)
		{
			short mask = (short) ((255 << (8 - mod)) % 256);
			String partialByte = Integer.toHexString(bits.get(length) & mask);
			if (partialByte.length() == 1)
				partialByte += "0";
			result += "~" + partialByte + "-" + mod;
		}
		return result;
	}

	/**
	 * Equality is based on all fields except valueEnd.<br>
	 * 
	 * {@inheritDoc}
	 */
	public boolean equals(Object o)
	{
		if (o == this)
			return true;
		if (!(o instanceof CentroidTreeNode))
			return false;
		CentroidTreeNode node = (CentroidTreeNode) o;
		if (bitsUsed == node.bitsUsed && subtreeSize == node.subtreeSize
				&& getCommonPrefixBits(this, node) == bitsUsed)
			return true;
		return false;
	}

	public int hashCode()
	{
		int result = (int) (31 * subtreeSize);
		for (int x = 0; x < bitsUsed / 8; x++)
		{
			result *= 31 * bits.get(x);
		}
		short mask = (short) (255 << (8 - (bitsUsed % 8)));
		result += 31 * (bits.get(bitsUsed / 8) & mask);
		return result;
	}

	public long getSubtreeSize()
	{
		return subtreeSize;
	}

	public void setSubtreeSize(long size)
	{
		subtreeSize = size;
	}

	public ByteBuffer getBytes()
	{
		return bits;
	}

	public int getStringBitLength()
	{
		return bitsUsed;
	}

	public boolean isValueEnd()
	{
		return valueEnd;
	}

	public void setValueEnd(boolean valueEnd)
	{
		this.valueEnd = valueEnd;
	}

	@Override
	public int byteSize()
	{
		return bits.capacity() + 13;
	}

	@Override
	public void setFromBytes(ByteBuffer bytes, int startIndex)
	{
		valueEnd = bytes.get(startIndex) == 1;
		bitsUsed = bytes.getInt(startIndex + 1);
		subtreeSize = bytes.getLong(startIndex + 5);
		bits.rewind();
		bits.put(bytes.array(), startIndex + 13, (int) Math.ceil(bitsUsed / 8.));
	}

	@Override
	public void writeBytes(ByteBuffer bytes, int startIndex)
	{
		bytes.position(startIndex);
		bytes.put((byte) (valueEnd ? 1 : 0));
		bytes.putInt(bitsUsed);
		bytes.putLong(subtreeSize);
		bytes.put(bits.array(), 0, (int) Math.ceil(bitsUsed / 8.));
	}
}
