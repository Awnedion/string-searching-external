package ods.string.search;

import java.io.File;
import java.util.Random;
import java.util.TreeSet;

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
	public void testRandomAddExternalTreap()
	{
		ExternalMemoryTreap<String> tree = new ExternalMemoryTreap<String>(
				new File("target/treap"), 100000, 1000000000l);
		fillTreeRandomly(tree, 600000);
	}

	private void fillTreeRandomly(PrefixSearchableSet<String> tree, long timeLimit)
	{
		Random rand = new Random(1);

		long startTime = System.currentTimeMillis();
		int count = 0;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			int inputLength = rand.nextInt(MAX_STRING_LENGTH) + 1;
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

	private void fillTreeSequentially(PrefixSearchableSet<String> tree, long timeLimit)
	{
		long startTime = System.currentTimeMillis();
		int count = 0;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			tree.add(String.valueOf(count));
			count++;
		}
		System.out.println(count + " insert operations, " + tree.size() + " size tree, created in "
				+ timeLimit + "ms");

		startTime = System.currentTimeMillis();
		count = 0;
		while (System.currentTimeMillis() - startTime < timeLimit)
		{
			tree.contains(String.valueOf(count));
			count++;
		}
		System.out.println(count + " find operations, performed in " + timeLimit + "ms");

		System.out.println(Runtime.getRuntime().totalMemory());
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
