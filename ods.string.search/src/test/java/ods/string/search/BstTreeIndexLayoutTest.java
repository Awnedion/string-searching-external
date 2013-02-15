package ods.string.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Random;
import java.util.Stack;

import org.junit.Test;

public class BstTreeIndexLayoutTest
{

	@Test
	public void testBasicIndexTraversal()
	{
		testLayout(new BasicIndexLayout());
	}

	@Test
	public void testVebIndexTraversal()
	{
		testLayout(new VebIndexLayout());
	}

	private void testLayout(BstTreeIndexLayout layout)
	{
		Stack<Long> indices = new Stack<Long>();
		Random rand = new Random();

		for (int x = 0; x < 10000; x++)
		{
			int depth = rand.nextInt(28) + 3;
			long index = 0;
			for (int y = 0; y < depth; y++)
			{
				assertTrue(indices.add(index));
				boolean right = rand.nextBoolean();
				if (right)
					index = layout.getRightChildIndex(index);
				else
					index = layout.getLeftChildIndex(index);
			}

			while (!indices.isEmpty())
			{
				long parentIndex = indices.pop();
				assertEquals(parentIndex, layout.getParentIndex(index));
				index = parentIndex;
			}
		}
	}

	@Test
	public void testLeftBranchesVeb()
	{
		ArrayList<Long> expectedIndices = new ArrayList<Long>();
		expectedIndices.add(0l);
		expectedIndices.add(1l);
		expectedIndices.add(3l);
		expectedIndices.add(4l);
		expectedIndices.add(15l);
		expectedIndices.add(16l);
		expectedIndices.add(18l);
		expectedIndices.add(19l);
		expectedIndices.add(255l);
		expectedIndices.add(256l);
		expectedIndices.add(258l);
		expectedIndices.add(259l);
		expectedIndices.add(270l);
		expectedIndices.add(271l);
		expectedIndices.add(273l);
		expectedIndices.add(274l);
		expectedIndices.add(65535l);

		VebIndexLayout layout = new VebIndexLayout();
		for (int x = 0; x < expectedIndices.size() - 1; x++)
		{
			assertEquals(expectedIndices.get(x + 1).longValue(),
					layout.getLeftChildIndex(expectedIndices.get(x)));
		}

		for (int x = expectedIndices.size() - 1; x > 0; x--)
		{
			assertEquals(expectedIndices.get(x - 1).longValue(),
					layout.getParentIndex(expectedIndices.get(x)));
		}
	}

	@Test
	public void testRightBranchesVeb()
	{
		ArrayList<Long> expectedIndices = new ArrayList<Long>();
		expectedIndices.add(0l);
		expectedIndices.add(2l);
		expectedIndices.add(12l);
		expectedIndices.add(14l);
		expectedIndices.add(240l);
		expectedIndices.add(242l);
		expectedIndices.add(252l);
		expectedIndices.add(254l);

		VebIndexLayout layout = new VebIndexLayout();
		for (int x = 0; x < expectedIndices.size() - 1; x++)
		{
			assertEquals(expectedIndices.get(x + 1).longValue(),
					layout.getRightChildIndex(expectedIndices.get(x)));
		}

		for (int x = expectedIndices.size() - 1; x > 0; x--)
		{
			assertEquals(expectedIndices.get(x - 1).longValue(),
					layout.getParentIndex(expectedIndices.get(x)));
		}
	}

	@Test
	public void testVebNoParent() {
		VebIndexLayout layout = new VebIndexLayout();
		assertEquals(-1, layout.getParentIndex(0l));
	}
}
