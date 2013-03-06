package ods.string.search.partition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;

import ods.string.search.Utils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExternalMemoryTreapTest
{
	@Before
	public void setup()
	{
		Assert.assertTrue(Utils.deleteRecursively(new File("target/treap")));
	}

	@Test
	public void testAddSearchRemove()
	{
		ExternalMemoryTreap<String> tree = new ExternalMemoryTreap<String>(
				new File("target/treap"), 500, 300000);
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
		ExternalMemoryTreap<Integer> tree = new ExternalMemoryTreap<Integer>(new File(
				"target/treap"), 50, 30000000);

		for (int x = 0; x < 200; x++)
		{
			tree.add(x);
		}

		Random rand = new Random();
		int count = 0;
		int modulus = rand.nextInt(15) + 2;
		for (Iterator<Integer> iter = tree.iterator(); iter.hasNext();)
		{
			assertEquals(count, iter.next().intValue());
			if (count % modulus == 0)
				iter.remove();
			count++;
		}

		for (int x = 0; x < 200; x++)
		{
			boolean expectedResult = !(x % modulus == 0);
			assertEquals(expectedResult, tree.contains(x));
		}
	}

	@Test
	public void testIteratorRange()
	{
		ExternalMemoryTreap<Integer> tree = new ExternalMemoryTreap<Integer>(new File(
				"target/treap"), 50, 30000000);

		for (int x = 0; x < 200; x++)
		{
			tree.add(x);
		}

		Random rand = new Random();
		int count = rand.nextInt(100) + 25;
		int endRange = rand.nextInt(74) + count;
		for (Iterator<Integer> iter = tree.iterator(count, endRange); iter.hasNext();)
		{
			assertEquals(count, iter.next().intValue());
			count++;
		}
	}

	@Test
	public void testIteratorPrefix()
	{
		ExternalMemoryTreap<String> tree = new ExternalMemoryTreap<String>(
				new File("target/treap"), 50, 30000000);

		for (int x = 0; x < 200; x++)
		{
			tree.add(x + "");
		}

		Iterator<String> iter = tree.iterator("1", "2");
		int count = 0;
		while (iter.hasNext())
		{
			assertTrue(iter.next().startsWith("1"));
			count++;
		}
		assertTrue(count > 100);
	}
}
