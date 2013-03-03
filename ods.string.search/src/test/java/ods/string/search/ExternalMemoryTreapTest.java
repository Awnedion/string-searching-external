package ods.string.search;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Random;
import java.util.TreeSet;

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
	public void testAdd()
	{
		ExternalMemoryTreap<String> tree = new ExternalMemoryTreap<String>(
				new File("target/treap"), 500, 7);
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

		for (int x = 0; x < 10000; x++)
		{
			int inputLength = rand.nextInt(4) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			assertEquals(set.contains(input), tree.contains(input));
		}

		for (int x = 0; x < 10000; x++)
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
}
