package ods.string.search;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.HashSet;
import java.util.Random;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExternalMemoryCacheTest
{
	@Before
	public void setup()
	{
		Assert.assertTrue(deleteRecursively(new File("target/blocks")));
	}

	@Test
	public void testSequentialWriteRead()
	{
		ExternalMemoryCache<StringNode> cache = new ExternalMemoryCache<StringNode>(new File(
				"target/blocks"), 5000000l);
		StringNode node = new StringNode(100);
		node.setString("awwww yea");
		cache.set(0, node);
		node.setString("fasf");
		cache.get(0, node);
		assertEquals("awwww yea", node.toString());

		for (int x = 0; x < 10000000; x++)
		{
			node.setString(x + "");
			cache.set(x, node);
		}
		for (int x = 0; x < 10000000; x++)
		{
			cache.get(x, node);
			assertEquals(x + "", node.toString());
		}
		cache.close();
	}

	@Test
	public void testRandomWriteRead()
	{
		ExternalMemoryCache<StringNode> cache = new ExternalMemoryCache<StringNode>(new File(
				"target/blocks"), 10000000l);
		StringNode node = new StringNode(100);
		Random rand = new Random();
		HashSet<Integer> inserts = new HashSet<Integer>();

		for (int x = 0; x < 100000; x++)
		{
			int randNum = rand.nextInt(100000);
			inserts.add(randNum);
			node.setString(randNum + "");
			cache.set(randNum, node);
		}
		for (int x = 0; x < 100000; x++)
		{
			int randNum = rand.nextInt(100000);
			cache.get(randNum, node);
			if (inserts.contains(randNum))
				assertEquals(randNum + "", node.toString());
			else
				assertEquals("", node.toString());
		}
		cache.close();
	}

	@Test
	public void testDataReopen()
	{
		ExternalMemoryCache<StringNode> cache = new ExternalMemoryCache<StringNode>(new File(
				"target/blocks"), 5000000l);
		StringNode node = new StringNode(100);

		for (int x = 0; x < 1000000; x++)
		{
			node.setString(x + "");
			cache.set(x, node);
		}
		cache.close();

		cache = new ExternalMemoryCache<StringNode>(new File("target/blocks"), 5000000l);
		for (int x = 0; x < 1000000; x++)
		{
			cache.get(x, node);
			assertEquals(x + "", node.toString());
		}
		cache.close();
	}

	public static boolean deleteRecursively(File file)
	{
		if (!file.exists())
			return true;
		if (file.isDirectory())
		{
			for (File f : file.listFiles())
			{
				deleteRecursively(f);
			}
		}
		return file.delete();
	}
}
