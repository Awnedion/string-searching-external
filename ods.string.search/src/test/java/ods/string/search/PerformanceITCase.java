package ods.string.search;

import java.io.File;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;

import ods.string.search.array.BasicIndexLayout;
import ods.string.search.array.CentroidTree;
import ods.string.search.array.VebIndexLayout;
import ods.string.search.partition.ExternalMemorySplittableSet;
import ods.string.search.partition.SplittableTreeSetAdapter;
import ods.string.search.partition.Treap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PerformanceITCase
{

	private static final int MAX_STRING_LENGTH = 12;

	@Before
	public void setup()
	{
		Assert.assertTrue(Utils.deleteRecursively(new File("target/centroidTree")));
		Assert.assertTrue(Utils.deleteRecursively(new File("target/treap")));
	}

	@Test
	public void testRandomAddCentroidBasicIndexCompressed()
	{
		CentroidTree tree = new CentroidTree(new File("target/centroidTree"), 1000000000l,
				MAX_STRING_LENGTH + 1, new BasicIndexLayout(), true);
		fillTreeRandomly(tree, 600000);
	}

	@Test
	public void testRandomAddCentroidBasicIndexUncompressed()
	{
		CentroidTree tree = new CentroidTree(new File("target/centroidTree"), 1000000000l,
				MAX_STRING_LENGTH + 1, new BasicIndexLayout(), false);
		fillTreeRandomly(tree, 600000);
	}

	@Test
	public void testSequentialAddCentroidBasicIndex()
	{
		CentroidTree tree = new CentroidTree(new File("target/centroidTree"), 1000000000l,
				MAX_STRING_LENGTH + 1, new BasicIndexLayout(), true);
		fillTreeSequentially(tree, 20000);
	}

	@Test
	public void testRandomAddCentroidVebIndex()
	{
		CentroidTree tree = new CentroidTree(new File("target/centroidTree"), 1000000000l,
				MAX_STRING_LENGTH + 1, new VebIndexLayout(), true);
		fillTreeRandomly(tree, 600000);
	}

	@Test
	public void testSequentialAddCentroidVebIndex()
	{
		CentroidTree tree = new CentroidTree(new File("target/centroidTree"), 1000000000l,
				MAX_STRING_LENGTH + 1, new VebIndexLayout(), true);
		fillTreeSequentially(tree, 20000);
	}

	@Test
	public void testRandomAddMemoryTreap()
	{
		Treap<String> tree = new Treap<String>();
		fillTreeRandomly(tree, 600000);
	}

	@Test
	public void testSequentialAddMemoryTreap()
	{
		Treap<String> tree = new Treap<String>();
		fillTreeSequentially(tree, 600000);
	}

	@Test
	public void testRandomAddExternalTreap()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 10000, 1000000000l, Treap.class);
		fillTreeRandomly(tree, 600000);
	}

	@Test
	public void testRandomAddExternalTreeSet()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 10000, 1000000000l, SplittableTreeSetAdapter.class);
		fillTreeRandomly(tree, 600000);
	}

	@Test
	public void testSequentialAddExternalTreap()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 100000, 1000000000l, Treap.class);
		fillTreeSequentially(tree, 600000);
	}

	private void fillTreeRandomly(PrefixSearchableSet<String> tree, long timeLimit)
	{
		Random rand = new Random(1);

		long startTime = System.currentTimeMillis();
		int count = 0;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			String input = generateRandomString(rand, 1);
			tree.add(input);
			count++;
		}
		System.out.println(count + " insert operations, " + tree.size() + " size tree, created in "
				+ timeLimit + "ms");

		startTime = System.currentTimeMillis();
		count = 0;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			String input = generateRandomString(rand, 1);
			tree.contains(input);
			count++;
		}
		System.out.println(count + " find operations, performed in " + timeLimit + "ms");

		startTime = System.currentTimeMillis();
		count = 0;
		long iterationCount = 0;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			String input = generateRandomString(rand, 6);
			Iterator<String> iter = tree.iterator(input, input.substring(0, input.length() - 1)
					+ (char) (input.charAt(input.length() - 1) + 1));
			while (iter.hasNext())
			{
				iter.next();
				iterationCount++;
			}
			count++;
		}
		System.out.println(count + " prefix search operations, " + iterationCount
				+ " elements returned, performed in " + timeLimit + "ms");

		System.out.println(Runtime.getRuntime().totalMemory());
	}

	private String generateRandomString(Random rand, int minLength)
	{
		int inputLength = rand.nextInt(MAX_STRING_LENGTH - minLength + 1) + minLength;
		StringBuilder input = new StringBuilder(inputLength);
		for (int y = 0; y < inputLength; y++)
			input.append((char) (rand.nextInt(10) + '0'));
		return input.toString();
	}

	private void fillTreeSequentially(PrefixSearchableSet<String> tree, long timeLimit)
	{
		long startTime = System.currentTimeMillis();
		int count = 0;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			String val = convertToFixedLengthString(count);
			tree.add(val);
			count++;
		}
		System.out.println(count + " insert operations, " + tree.size() + " size tree, created in "
				+ timeLimit + "ms");

		startTime = System.currentTimeMillis();
		count = 0;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			tree.contains(convertToFixedLengthString(count));
			count++;
		}
		System.out.println(count + " find operations, performed in " + timeLimit + "ms");

		System.out.println(Runtime.getRuntime().totalMemory());
	}

	private String convertToFixedLengthString(int value)
	{
		StringBuilder result = new StringBuilder(MAX_STRING_LENGTH);
		String s = String.valueOf(value);
		for (int x = 0; x < MAX_STRING_LENGTH - s.length(); x++)
			result.append("0");
		result.append(s);
		return result.toString();
	}

	@Test
	public void testAddTreeSet()
	{
		TreeSet<String> tree = new TreeSet<String>();
		long timeLimit = 120000;
		Random rand = new Random(1);

		long startTime = System.currentTimeMillis();
		int count = 0;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			int inputLength = rand.nextInt(10) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			tree.add(input);
			count++;
		}
		System.out.println(count + " insert operations, " + tree.size() + " size tree, created in "
				+ timeLimit + "ms");

		startTime = System.currentTimeMillis();
		count = 0;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			int inputLength = rand.nextInt(10) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			tree.contains(input);
			count++;
		}
		System.out.println(count + " find operations, performed in " + timeLimit + "ms");

		System.out.println(Runtime.getRuntime().totalMemory());
	}

}
