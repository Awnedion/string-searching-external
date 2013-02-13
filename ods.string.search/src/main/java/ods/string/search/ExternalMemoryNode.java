package ods.string.search;

import java.nio.ByteBuffer;

public interface ExternalMemoryNode
{
	int byteSize();

	void setFromBytes(ByteBuffer bytes, int startIndex);

	void writeBytes(ByteBuffer bytes, int startIndex);
}
