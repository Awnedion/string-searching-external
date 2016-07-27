package ods.string.search.partition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;

import ods.string.search.PrefixSearchableSet;
import ods.string.search.Utils;
import ods.string.search.partition.splitsets.ExternalizableArrayList;
import ods.string.search.partition.splitsets.ExternalizableLinkedList;
import ods.string.search.partition.splitsets.ExternalizableListSet;
import ods.string.search.partition.splitsets.SplittableTreeSetAdapter;
import ods.string.search.partition.splitsets.Treap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExternalMemorySplittableSetTest
{
	@Before
	public void setup()
	{
		Assert.assertTrue(Utils.deleteRecursively(new File("target/treap")));
	}

	@Test
	public void testAddSearchRemove()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 500, 300000, new Treap<String>());
		testOperations(tree);
	}

	@Test
	public void testAddSearchRemoveTreeSet()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 500, 300000, new SplittableTreeSetAdapter<String>());
		testOperations(tree);
	}

	@Test
	public void testAddSearchRemoveLinkedListBinarySearch()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 500, 300000, new ExternalizableListSet<String>(
						new ExternalizableLinkedList<String>(), false));
		testOperations(tree);
	}

	@Test
	public void testAddSearchRemoveLinkedListLinearCompare()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 500, 300000, new ExternalizableListSet<String>(
						new ExternalizableLinkedList<String>(), true));
		testOperations(tree);
	}

	@Test
	public void testAddSearchRemoveArrayListBinarySearch()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 500, 300000, new ExternalizableListSet<String>(
						new ExternalizableArrayList<String>(), false));
		testOperations(tree);
	}

	@Test
	public void testAddSearchRemoveArrayListLinearCompare()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 500, 300000, new ExternalizableListSet<String>(
						new ExternalizableArrayList<String>(), true));
		testOperations(tree);
	}

	static void testOperations(PrefixSearchableSet<String> tree)
	{
		TreeSet<String> set = new TreeSet<String>();
		Random rand = new Random();

		for (int x = 0; x < 10000; x++)
		{
			int inputLength = rand.nextInt(4) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			boolean centroidResult = tree.add(input);
			boolean treeResult = set.add(input);
			assertEquals(treeResult, centroidResult);
			assertEquals(set.size(), tree.size());
		}

		System.out.println("Searching...");
		for (int x = 0; x < 10000; x++)
		{
			int inputLength = rand.nextInt(4) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			assertEquals(set.contains(input), tree.contains(input));
		}

		System.out.println("Removing...");
		for (int x = 0; x < 100000; x++)
		{
			int inputLength = rand.nextInt(4) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			boolean centroidResult = tree.remove(input);
			boolean treeResult = set.remove(input);
			assertEquals(treeResult, centroidResult);
			assertEquals(set.size(), tree.size());
		}
	}

	@Test
	public void testIteratorAll()
	{
		ExternalMemorySplittableSet<Integer> tree = new ExternalMemorySplittableSet<Integer>(
				new File("target/treap"), 50, 30000000, new Treap<Integer>());

		testFullIterator(tree);
	}

	static void testFullIterator(PrefixSearchableSet<Integer> tree)
	{
		for (int x = 1; x < 200; x++)
		{
			tree.add(x);
		}

		Random rand = new Random();
		int count = 1;
		int modulus = rand.nextInt(15) + 2;
		for (Iterator<Integer> iter = tree.iterator(); iter.hasNext();)
		{
			assertEquals(count, iter.next().intValue());
			try
			{
				if (count % modulus == 0)
					iter.remove();
			} catch (UnsupportedOperationException e)
			{
				modulus = 1000;
			}
			count++;
		}

		for (int x = 1; x < 200; x++)
		{
			boolean expectedResult = !(x % modulus == 0);
			assertEquals(expectedResult, tree.contains(x));
		}
	}

	@Test
	public void testIteratorRange()
	{
		ExternalMemorySplittableSet<Integer> tree = new ExternalMemorySplittableSet<Integer>(
				new File("target/treap"), 50, 30000000, new Treap<Integer>());

		testRangeIterators(tree);
	}

	@Test
	public void testIteratorRangeTreeSet()
	{
		ExternalMemorySplittableSet<Integer> tree = new ExternalMemorySplittableSet<Integer>(
				new File("target/treap"), 50, 30000000, new SplittableTreeSetAdapter<Integer>());

		testRangeIterators(tree);
	}

	static void testRangeIterators(PrefixSearchableSet<Integer> tree)
	{
		for (int x = 0; x < 200; x++)
		{
			tree.add(x);
		}

		Random rand = new Random();
		int count = rand.nextInt(100) + 25;
		int endRange = rand.nextInt(74) + count;
		int origCount = count;
		for (Iterator<Integer> iter = tree.iterator(count, endRange); iter.hasNext();)
		{
			assertEquals("custom range [" + origCount + ", " + endRange + ") failed", count, iter
					.next().intValue());
			count++;
		}

		Iterator<Integer> iter = tree.iterator(200, 700);
		assertEquals(false, iter.hasNext());

		iter = tree.iterator(199, 700);
		while (iter.hasNext())
			iter.next();

		iter = tree.iterator(197, 197);
		assertEquals(false, iter.hasNext());

		iter = tree.iterator(198, 197);
		assertEquals(true, iter.hasNext());
		assertEquals(197, iter.next().intValue());
		assertEquals(false, iter.hasNext());
	}

	@Test
	public void testIteratorPrefix()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 50, 30000000, new Treap<String>());

		testPrefixIterators(tree);
	}

	@Test
	public void testIteratorPrefixArray()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 20, 1000, new ExternalizableListSet<String>(
						new ExternalizableArrayList<String>(), false));

		testPrefixIterators(tree);
	}

	@Test
	public void testIteratorPrefixArray2()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 150, 50000000, new ExternalizableListSet<String>(
						new ExternalizableArrayList<String>(), false));

		testPrefixWithManyMatches(tree);
	}

	static void testPrefixWithManyMatches(PrefixSearchableSet<String> tree)
	{
		for (int x = 0; x < 12000; x++)
		{
			assertEquals(x, tree.size());
			tree.add(Utils.convertToFixedLengthString(x, 12));
		}

		for (int x = 0; x < 10000; x++)
		{
			String input = null;
			input = Utils.convertToFixedLengthString((int) (x % (10000 / 100)), 10);
			Iterator<String> iter = tree.iterator(input, input.substring(0, input.length() - 1)
					+ (char) (input.charAt(input.length() - 1) + 1));
			int iterCount1 = 0;
			while (iter.hasNext())
			{
				iter.next();
				iterCount1++;
			}
			assertEquals(100, iterCount1);
		}
	}

	static void testPrefixIterators(PrefixSearchableSet<String> tree)
	{
		for (int x = 0; x < 200; x++)
		{
			tree.add(x + "");
		}
		assertEquals(200, tree.size());

		Iterator<String> iter = tree.iterator("1", "2");
		int count = 0;
		while (iter.hasNext())
		{
			assertTrue(iter.next().startsWith("1"));
			count++;
		}
		assertEquals(111, count);

		iter = tree.iterator("10", "11");
		count = 0;
		while (iter.hasNext())
		{
			assertTrue(iter.next().startsWith("10"));
			count++;
		}
		assertEquals(11, count);
	}
}
