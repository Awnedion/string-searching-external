package ods.string.search;

import java.nio.ByteBuffer;

public class StringNode implements ExternalMemoryNode
{
	private static final int CHAR_SIZE = " ".getBytes().length;

	private int stringBytes = 0;
	private ByteBuffer characters;

	public StringNode(int maxStringLength)
	{
		this.characters = ByteBuffer.allocate(maxStringLength * CHAR_SIZE);
	}

	public void setString(String str)
	{
		byte[] bytes = str.getBytes();
		if (bytes.length > characters.capacity())
			throw new IllegalArgumentException("String cannot be longer than "
					+ characters.capacity() / CHAR_SIZE);

		byte[] charBytes = characters.array();
		for (int x = 0; x < bytes.length; x++)
		{
			charBytes[x] = bytes[x];
		}
		stringBytes = bytes.length;
	}

	public String toString()
	{
		return new String(characters.array(), 0, stringBytes);
	}

	@Override
	public int byteSize()
	{
		return CHAR_SIZE * characters.capacity() + 4;
	}

	@Override
	public void setFromBytes(ByteBuffer bytes, int startIndex)
	{
		stringBytes = bytes.getInt(startIndex);
		characters.rewind();
		characters.put(bytes.array(), startIndex + 4, stringBytes);
	}

	@Override
	public void writeBytes(ByteBuffer bytes, int startIndex)
	{
		bytes.position(startIndex);
		bytes.putInt(stringBytes);
		bytes.put(characters.array(), 0, stringBytes);
	}

}
