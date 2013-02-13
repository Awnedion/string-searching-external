package ods.string.search;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Random;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CentroidTreeTest
{
	@Before
	public void setup()
	{
		Assert.assertTrue(ExternalMemoryCacheTest
				.deleteRecursively(new File("target/centroidTree")));
	}

	@Test
	public void testAdd()
	{
		CentroidTree tree = new CentroidTree(new File("target/centroidTree"));
		TreeSet<String> set = new TreeSet<String>();
		Random rand = new Random(1);

		for (int x = 0; x < 10000; x++)
		{
			int inputLength = rand.nextInt(4) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			boolean centroidResult = tree.insert(input);
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
	}
}
