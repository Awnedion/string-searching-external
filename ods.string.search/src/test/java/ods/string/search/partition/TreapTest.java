package ods.string.search.partition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;

public class TreapTest
{
	private Treap<Double> treap;

	@Before
	public void setup()
	{
		treap = new Treap<Double>();

		for (int x = 0; x < 100; x++)
		{
			treap.add(new Double(x));
		}
	}

	@Test
	public void testSplitNonExistingValue()
	{
		Treap<Double> treap2 = splitOn(49.5);
		assertEquals(50, treap.size());
		assertEquals(50, treap2.size());
	}

	@Test
	public void testSplitExistingValue()
	{
		Treap<Double> treap2 = splitOn(45.);
		assertEquals(45, treap.size());
		assertEquals(55, treap2.size());
	}

	@Test
	public void testSplitVeryLow()
	{
		Treap<Double> treap2 = splitOn(-1.);
		assertEquals(0, treap.size());
		assertEquals(100, treap2.size());
	}

	@Test
	public void testSplitVeryHigh()
	{
		Treap<Double> treap2 = splitOn(101.);
		assertEquals(100, treap.size());
		assertEquals(0, treap2.size());

	}

	@Test
	public void testSplitLowestValue()
	{
		Treap<Double> treap2 = splitOn(0.);
		assertEquals(0, treap.size());
		assertEquals(100, treap2.size());
	}

	@Test
	public void testSplitHighestValue()
	{
		Treap<Double> treap2 = splitOn(99.);
		assertEquals(99, treap.size());
		assertEquals(1, treap2.size());

	}

	private Treap<Double> splitOn(double x)
	{
		SplittableSet<Double> treap2 = treap.split(x);
		for (Iterator<Double> iter = treap.iterator(); iter.hasNext();)
		{
			assertTrue(iter.next() < x);
		}
		for (Iterator<Double> iter = treap2.iterator(); iter.hasNext();)
		{
			assertTrue(iter.next() >= x);
		}
		assertEquals(treap.size(), treap.size());
		assertEquals(treap2.size(), treap2.size());

		return (Treap<Double>) treap2;
	}

	@Test
	public void testMerge()
	{
		Treap<Double> higher = new Treap<Double>();
		for (int x = 100; x < 150; x++)
		{
			higher.add(new Double(x));
		}
		assertTrue(treap.merge(higher));
		assertEquals(150, treap.size());
		assertEquals(treap.size(), treap.size());
		assertEquals(0, higher.size());
		assertEquals(higher.size(), higher.size());
		assertTrue(treap.merge(higher));
		assertTrue(higher.merge(treap));
		assertEquals(150, higher.size());
		assertEquals(0, treap.size());

		treap.add(-1.);
		assertTrue(treap.merge(higher));
		assertEquals(151, treap.size());
		assertEquals(treap.size(), treap.size());
		assertEquals(0, higher.size());
		assertEquals(higher.size(), higher.size());

		higher.add(151.);
		assertTrue(treap.merge(higher));
		assertEquals(152, treap.size());
		assertEquals(0, higher.size());
	}

	@Test
	public void testMergeInvalid()
	{
		Treap<Double> higher = new Treap<Double>();
		for (int x = 99; x < 149; x++)
		{
			higher.add(new Double(x));
		}
		assertFalse(treap.merge(higher));
		assertEquals(100, treap.size());
		assertEquals(treap.size(), treap.size());
		assertEquals(50, higher.size());
		assertEquals(higher.size(), higher.size());

		Treap<Double> lower = new Treap<Double>();
		for (int x = -49; x < 1; x++)
		{
			lower.add(new Double(x));
		}
		assertFalse(lower.merge(treap));
		assertEquals(100, treap.size());
		assertEquals(treap.size(), treap.size());
		assertEquals(50, lower.size());
	}

	@Test
	public void testRemove()
	{
		treap.remove(48.);
		assertEquals(99, treap.size());
		assertEquals(treap.size(), treap.size());
	}

	@Test
	public void testGetRank()
	{
		for (int x = 0; x < 100; x++)
		{
			assertEquals(new Double(x), treap.get(x));
		}
	}

	@Test
	public void testGetRankRemove()
	{
		treap.remove(new Double(3));
		treap.remove(new Double(14));
		treap.remove(new Double(76));
		for (int x = 0; x < 97; x++)
		{
			assertNotNull(treap.get(x));
		}
		assertEquals(new Double(99), treap.get(96));
		assertEquals(new Double(0), treap.get(0));
	}

	@Test
	public void testGetRankRandomInput()
	{
		Treap<Double> treap = new Treap<Double>();

		for (int x = 0; x < 100; x++)
		{
			treap.add(Math.random());
		}

		for (int x = 0; x < 100; x++)
		{
			assertNotNull(treap.get(x));
		}
	}

	@Test
	public void testGetRankInvalid()
	{
		assertNull(treap.get(-1));
		assertNull(treap.get(100));
		assertNull(treap.get(1003));
		assertNull(treap.get(-1127));
	}

	@Test
	public void testIteratorRange()
	{
		Iterator<Double> iter = treap.iterator(20., 35.);
		double expectedValue = 20;
		while (iter.hasNext())
		{
			assertEquals(new Double(expectedValue++), iter.next());
			iter.remove();
		}

		for (int x = 0; x < 20; x++)
			assertEquals(true, treap.contains(new Double(x)));

		for (int x = 20; x < 35; x++)
			assertEquals(false, treap.contains(new Double(x)));

		for (int x = 35; x < 100; x++)
			assertEquals(true, treap.contains(new Double(x)));
	}

	@Test
	public void testIteratorAll()
	{
		Iterator<Double> iter = treap.iterator();
		double expectedValue = 0;
		while (iter.hasNext())
		{
			assertEquals(new Double(expectedValue++), iter.next());
			if (Math.random() > 0.5)
				iter.remove();
		}
		assertEquals(100., expectedValue, 0.0001);
	}
}
