package ods.string.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.Test;

public class CentroidTreeNodeTest
{
	@Test
	public void testSerialization()
	{
		CentroidTreeNode node = new CentroidTreeNode(100);
		node.setString("yoplay");
		node.setSubtreeSize(3);
		node.setValueEnd(true);
		node.setBitsUsed(16);

		ByteBuffer buffer = ByteBuffer.allocate(1000);
		node.writeBytes(buffer, 0);

		CentroidTreeNode node2 = new CentroidTreeNode(100);
		node2.setFromBytes(buffer, 0);
		assertEquals("yo", node2.toString());
		assertEquals(3, node2.getSubtreeSize());
		assertEquals(16, node2.getStringBitLength());
		assertEquals(true, node2.isValueEnd());
	}

	@Test
	public void testSerializationSmallerString()
	{
		CentroidTreeNode node = new CentroidTreeNode(50);
		node.setString("yoplay");
		node.setSubtreeSize(3);
		node.setValueEnd(true);
		node.setBitsUsed(16);

		ByteBuffer buffer = ByteBuffer.allocate(1000);
		node.writeBytes(buffer, 0);

		CentroidTreeNode node2 = new CentroidTreeNode(50);
		node2.setFromBytes(buffer, 0);
		assertEquals("yo", node2.toString());
		assertEquals(3, node2.getSubtreeSize());
		assertEquals(16, node2.getStringBitLength());
		assertEquals(true, node2.isValueEnd());

		node = new CentroidTreeNode(50);
		node.setString("21412512215125125");
		node.setSubtreeSize(3);
		node.setValueEnd(true);
		node.setBitsUsed(32);

		buffer = ByteBuffer.allocate(1000);
		node.writeBytes(buffer, 1);

		node2 = new CentroidTreeNode(50);
		node2.setFromBytes(buffer, 1);
		assertEquals("2141", node2.toString());
		assertEquals(3, node2.getSubtreeSize());
		assertEquals(32, node2.getStringBitLength());
		assertEquals(true, node2.isValueEnd());
	}

	@Test
	public void testNodeSplitting()
	{
		CentroidTreeNode node = new CentroidTreeNode(100);
		node.setString("yoplay");
		node.setSubtreeSize(3);
		CentroidTreeNode node2 = new CentroidTreeNode(100);
		node2.setString("abc123");
		node2.setSubtreeSize(3);
		CentroidTreeNode.splitOnPrefix(node, node2);
		assertTrue(node.toString().contains("~"));
		assertTrue(node2.toString().contains("~"));
		CentroidTreeNode.appendOnNode(node, node2);
		assertEquals("abc123", node.toString());

		node.setString("yoplay");
		node2.setString("abc123");
		CentroidTreeNode.splitOnPrefix(node2, node);
		CentroidTreeNode.appendOnNode(node2, node);
		assertEquals("yoplay", node2.toString());

		node.setString("68");
		node2.setString("6");
		node2.setBitsUsed(4);
		CentroidTreeNode.splitOnPrefix(node2, node);

		node2.setString("B");
		node2.setBitsUsed(2);
		CentroidTreeNode.splitOnPrefix(node2, node);

		node2.setString("4");
		node2.setBitsUsed(6);

		CentroidTreeNode.appendOnNode(node2, node);
		assertEquals("68", node2.toString());
	}

	@Test
	public void testPrefixMatchSamePartialByte()
	{
		CentroidTreeNode node = new CentroidTreeNode(100);
		node.setString("1");
		node.setBitsUsed(3);
		CentroidTreeNode node2 = new CentroidTreeNode(100);
		node2.setString("1");
		node2.setBitsUsed(3);
		assertEquals(3, CentroidTreeNode.getCommonPrefixBits(node, node2));
	}
}
