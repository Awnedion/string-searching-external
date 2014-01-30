package ods.string.search;

import java.io.File;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;

import ods.string.search.array.BasicIndexLayout;
import ods.string.search.array.CentroidTree;
import ods.string.search.array.VebIndexLayout;
import ods.string.search.partition.ExternalMemorySkipList;
import ods.string.search.partition.ExternalMemorySplittableSet;
import ods.string.search.partition.ExternalMemoryTrie;
import ods.string.search.partition.ExternalizableArrayList;
import ods.string.search.partition.ExternalizableLinkedList;
import ods.string.search.partition.ExternalizableLinkedListSet;
import ods.string.search.partition.SplittableTreeSetAdapter;
import ods.string.search.partition.Treap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PerformanceITCase
{

	private static final int MAX_STRING_LENGTH = 12;

	@Before
	@After
	public void setup()
	{
		File partitionDir = new File("target/treap");
		if (partitionDir.list() != null)
		{
			System.out.println("Deleting " + partitionDir.list().length + " partitions.");
		}
		Assert.assertTrue(Utils.deleteRecursively(new File("target/centroidTree")));
		Assert.assertTrue(Utils.deleteRecursively(partitionDir));
		System.out.println("Deletion Complete");
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
				new File("target/treap"), 125, 50000000l, new Treap<String>());
		fillTreeRandomly(tree, 60000, 100000);
	}

	@Test
	public void testRandomAddExternalTreeSet()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 100, 50000000l, new SplittableTreeSetAdapter<String>());
		fillTreeRandomly(tree, 60000, 100000);
	}

	@Test
	public void testRandomAddExternalLinkedListBinarySearch()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 70, 50000000l, new ExternalizableLinkedListSet<String>(
						new ExternalizableLinkedList<String>(), false));
		fillTreeRandomly(tree, 60000, 150000);
	}

	@Test
	public void testRandomAddExternalLinkedListLinearCompare()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 55, 50000000l, new ExternalizableLinkedListSet<String>(
						new ExternalizableLinkedList<String>(), true));
		fillTreeRandomly(tree, 60000, 150000);
	}

	@Test
	public void testRandomAddExternalArrayListBinarySearch()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 70, 50000000l, new ExternalizableLinkedListSet<String>(
						new ExternalizableArrayList<String>(), false));
		fillTreeRandomly(tree, 60000, 150000);
	}

	@Test
	public void testRandomAddExternalArrayListLinearCompare()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 55, 50000000l, new ExternalizableLinkedListSet<String>(
						new ExternalizableArrayList<String>(), true));
		fillTreeRandomly(tree, 60000, 150000);
	}

	@Test
	public void testSequentialAddExternalTreap()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 5000, 50000000l, new Treap<String>());
		fillTreeSequentially(tree, 60000, 6000000);
	}

	@Test
	public void testSequentialAddExternalLinkedList()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 500, 50000000l, new ExternalizableLinkedListSet<String>());
		fillTreeSequentially(tree, 60000, 2000000);
	}

	@Test
	public void testSequentialAddExternalArrayListBinarySearch()
	{
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				new File("target/treap"), 7500, 50000000l, new ExternalizableLinkedListSet<String>(
						new ExternalizableArrayList<String>(), false));
		fillTreeSequentially(tree, 60000, 8000000);
	}

	@Test
	public void testRandomAddSkipList()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"), 1. / 35., 50000000l, new ExternalizableLinkedListSet<String>(
				new ExternalizableLinkedList<String>(), true));
		fillTreeRandomly(tree, 60000, 100000);
	}

	@Test
	public void testRandomAddSkipListSets()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"), 1. / 100., 50000000l, new Treap<String>());
		fillTreeRandomly(tree, 60000, 100000);
	}

	@Test
	public void testRandomAddSkipListArrayListLinearCompare()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"), 1. / 45., 50000000l, new ExternalizableLinkedListSet<String>(
				new ExternalizableArrayList<String>(), true));
		fillTreeRandomly(tree, 60000, 200000);
	}

	@Test
	public void testRandomAddSkipListArrayListBinarySearch()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"), 1. / 55., 50000000l, new ExternalizableLinkedListSet<String>(
				new ExternalizableArrayList<String>(), false));
		fillTreeRandomly(tree, 60000, 200000);
	}

	@Test
	public void testSequentialAddSkipList()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"), 1. / 50., 50000000l, new ExternalizableLinkedListSet<String>(
				new ExternalizableLinkedList<String>(), true));
		fillTreeSequentially(tree, 60000, 600000);
	}

	@Test
	public void testSequentialAddSkipListSets()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"), 1. / 450., 50000000l, new Treap<String>());
		fillTreeSequentially(tree, 60000, 2000000);
	}

	@Test
	public void testSequentialAddSkipListArrayListBinarySearch()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"), 1. / 3000., 50000000l, new ExternalizableLinkedListSet<String>(
				new ExternalizableArrayList<String>(), false));
		fillTreeSequentially(tree, 60000, 6000000);
	}

	@Test
	public void testRandomAddExternalTrieEvenSplits()
	{
		ExternalMemoryTrie<String> tree = new ExternalMemoryTrie<String>(new File("target/treap"),
				75, 50000000l, 0);
		fillTreeRandomly(tree, 60000, 100000);
	}

	@Test
	public void testRandomAddExternalTrieMinDepthSplits()
	{
		ExternalMemoryTrie<String> tree = new ExternalMemoryTrie<String>(new File("target/treap"),
				75, 50000000l, 4);
		fillTreeRandomly(tree, 60000, 10000);
	}

	@Test
	public void testSequentialAddExternalTrieEvenSplits()
	{
		ExternalMemoryTrie<String> tree = new ExternalMemoryTrie<String>(new File("target/treap"),
				6000, 50000000l, 0);
		fillTreeSequentially(tree, 60000, 2000000);
	}

	@Test
	public void testSequentialAddExternalTrieMinDepthSplits()
	{
		ExternalMemoryTrie<String> tree = new ExternalMemoryTrie<String>(new File("target/treap"),
				6000, 50000000l, 4);
		fillTreeSequentially(tree, 60000, 2000000);
	}

	private void fillTreeRandomly(PrefixSearchableSet<String> tree, long timeLimit)
	{
		fillTreeRandomly(tree, timeLimit, 999999999999l);
	}

	private void fillTreeRandomly(PrefixSearchableSet<String> tree, long timeLimit,
			long outputInterval)
	{
		Random rand = new Random(1);

		long startTime = System.currentTimeMillis();
		int count = 0;
		long reportingTime = System.currentTimeMillis() + outputInterval;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			if (System.currentTimeMillis() >= reportingTime)
			{
				System.out.println(count + " insert operations, " + tree.size()
						+ " size tree, created in " + (System.currentTimeMillis() - startTime)
						+ "ms");
				reportingTime += outputInterval;
			}
			String input = generateRandomString(rand, 1);
			tree.add(input);
			count++;
		}
		System.out.println(count + " insert operations, " + tree.size() + " size tree, created in "
				+ timeLimit + "ms");

		startTime = System.currentTimeMillis();
		count = 0;
		reportingTime = System.currentTimeMillis() + outputInterval;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			if (System.currentTimeMillis() >= reportingTime)
			{
				System.out.println(count + " find operations, performed in "
						+ (System.currentTimeMillis() - startTime) + "ms");
				reportingTime += outputInterval;
			}
			String input = generateRandomString(rand, 1);
			tree.contains(input);
			count++;
		}
		System.out.println(count + " find operations, performed in " + timeLimit + "ms");

		startTime = System.currentTimeMillis();
		count = 0;
		long iterationCount = 0;
		reportingTime = System.currentTimeMillis() + outputInterval;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			if (System.currentTimeMillis() >= reportingTime)
			{
				System.out.println(count + " prefix search operations, " + iterationCount
						+ " elements returned, performed in "
						+ (System.currentTimeMillis() - startTime) + "ms");
				reportingTime += outputInterval;
			}
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

		printSpaceStats(tree);
	}

	private void printSpaceStats(PrefixSearchableSet<String> tree)
	{
		tree.close();
		long totalSpaceUsage = 0;
		for (File f : new File("target/treap").listFiles())
		{
			totalSpaceUsage += f.length();
		}
		System.out.println("Total Space Usage: " + totalSpaceUsage / 1000000.
				+ "MB\nAverage Space Usage Per Value: " + (double) totalSpaceUsage / tree.size());
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
		fillTreeSequentially(tree, timeLimit, 999999999999l);
	}

	private void fillTreeSequentially(PrefixSearchableSet<String> tree, long timeLimit,
			long outputInterval)
	{
		long startTime = System.currentTimeMillis();
		int count = 0;
		long reportingTime = System.currentTimeMillis() + outputInterval;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			if (System.currentTimeMillis() >= reportingTime)
			{
				System.out.println(count + " insert operations, " + tree.size()
						+ " size tree, created in " + (System.currentTimeMillis() - startTime)
						+ "ms");
				reportingTime += outputInterval;
			}
			String val = convertToFixedLengthString(count);
			tree.add(val);
			count++;
		}
		System.out.println(count + " insert operations, " + tree.size() + " size tree, created in "
				+ timeLimit + "ms");

		int treeSize = (int) tree.size();
		startTime = System.currentTimeMillis();
		count = 0;
		reportingTime = System.currentTimeMillis() + outputInterval;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			if (System.currentTimeMillis() >= reportingTime)
			{
				System.out.println(count + " find operations, performed in "
						+ (System.currentTimeMillis() - startTime) + "ms");
				reportingTime += outputInterval;
			}
			tree.contains(convertToFixedLengthString(count % treeSize));
			count++;
		}
		System.out.println(count + " find operations, performed in " + timeLimit + "ms");

		startTime = System.currentTimeMillis();
		count = 0;
		long iterationCount = 0;
		reportingTime = System.currentTimeMillis() + outputInterval;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			if (System.currentTimeMillis() >= reportingTime)
			{
				System.out.println(count + " prefix search operations, " + iterationCount
						+ " elements returned, performed in "
						+ (System.currentTimeMillis() - startTime) + "ms");
				reportingTime += outputInterval;
			}
			String input = convertToFixedLengthString(count % treeSize);
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

		printSpaceStats(tree);
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
