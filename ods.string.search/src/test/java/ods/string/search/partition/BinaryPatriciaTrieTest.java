package ods.string.search.partition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;

import ods.string.search.partition.BinaryPatriciaTrie.Node;

import org.junit.Test;

public class BinaryPatriciaTrieTest
{
	@Test
	public void testAdd()
	{
		Random rand = new Random();
		BinaryPatriciaTrie<String> trie = new BinaryPatriciaTrie<String>();
		TreeSet<String> tree = new TreeSet<String>();
		for (int x = 0; x < 10000; x++)
		{
			String input = "";
			for (int y = 0; y < 10; y++)
				input += (char) (rand.nextInt(10) + '0');
			boolean trieResult = trie.add(input);
			boolean treeResult = tree.add(input);
			assertEquals(treeResult, trieResult);
			assertEquals(tree.size(), trie.n);
		}

		for (String s : tree)
		{
			assertTrue(trie.contains(s));
		}

		for (int x = 0; x < 100000; x++)
		{
			String input = "";
			for (int y = 0; y < 10; y++)
				input += (char) (rand.nextInt(10) + '0');
			assertEquals(tree.contains(input), trie.contains(input));
		}
	}

	@Test
	public void testAddVaryingLength()
	{
		Random rand = new Random();
		BinaryPatriciaTrie<String> trie = new BinaryPatriciaTrie<String>();
		TreeSet<String> tree = new TreeSet<String>();
		for (int x = 0; x < 10000; x++)
		{
			int inputLength = rand.nextInt(4) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			boolean trieResult = trie.add(input);
			boolean treeResult = tree.add(input);
			assertEquals(treeResult, trieResult);
			assertEquals(tree.size(), trie.n);
		}

		for (String s : tree)
		{
			assertTrue(trie.contains(s));
		}

		for (int x = 0; x < 100000; x++)
		{
			int inputLength = rand.nextInt(4) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			assertEquals(tree.contains(input), trie.contains(input));
		}

		verifyTrieNodeCompression(trie);
	}

	private void verifyTrieNodeCompression(BinaryPatriciaTrie<String> trie)
	{
		ArrayList<Node> nodes = new ArrayList<Node>();
		nodes.add(trie.r);
		while (!nodes.isEmpty())
		{
			Node n = nodes.remove(nodes.size() - 1);
			assertTrue(n.bitsUsed == 0 || n.leftChild != null && n.rightChild != null || n.valueEnd);
			if (n.leftChild != null)
				nodes.add(n.leftChild);
			if (n.rightChild != null)
				nodes.add(n.rightChild);
		}
	}

	@Test
	public void testRemove()
	{
		Random rand = new Random();
		BinaryPatriciaTrie<String> trie = new BinaryPatriciaTrie<String>();
		TreeSet<String> tree = new TreeSet<String>();
		ArrayList<String> inputs = new ArrayList<String>();
		for (int x = 0; x < 10000; x++)
		{
			String input = "";
			for (int y = 0; y < 10; y++)
				input += (char) (rand.nextInt(10) + '0');
			boolean trieResult = trie.add(input);
			boolean treeResult = tree.add(input);
			inputs.add(input);
			assertEquals(treeResult, trieResult);
			assertEquals(tree.size(), trie.n);
		}

		for (int x = 0; x < 5000; x++)
		{
			String s = inputs.get(x);
			assertEquals(tree.remove(s), trie.remove(s));
			assertEquals(tree.size(), trie.n);
		}

		for (int x = 0; x < 50000; x++)
		{
			String input = "";
			for (int y = 0; y < 10; y++)
				input += (char) (rand.nextInt(10) + '0');
			assertEquals(tree.contains(input), trie.contains(input));
		}

		for (int x = 5000; x < 10000; x++)
		{
			String s = inputs.get(x);
			assertEquals(tree.remove(s), trie.remove(s));
			assertEquals(tree.size(), trie.n);
		}

		for (int x = 0; x < 50000; x++)
		{
			String input = "";
			for (int y = 0; y < 10; y++)
				input += (char) (rand.nextInt(10) + '0');
			assertEquals(tree.contains(input), trie.contains(input));
		}
	}

	@Test
	public void testAddRemoveInts()
	{
		Random rand = new Random();
		BinaryPatriciaTrie<Integer> trie = new BinaryPatriciaTrie<Integer>();
		TreeSet<Integer> tree = new TreeSet<Integer>();
		ArrayList<Integer> inputs = new ArrayList<Integer>();
		for (int x = 0; x < 10000; x++)
		{
			Integer input = rand.nextInt();
			boolean trieResult = trie.add(input);
			boolean treeResult = tree.add(input);
			inputs.add(input);
			assertEquals(treeResult, trieResult);
			assertEquals(tree.size(), trie.n);
		}

		for (int x = 0; x < 5000; x++)
		{
			Integer s = inputs.get(x);
			assertEquals(tree.remove(s), trie.remove(s));
			assertEquals(tree.size(), trie.n);
		}

		for (int x = 0; x < 50000; x++)
		{
			Integer input = rand.nextInt();
			assertEquals(tree.contains(input), trie.contains(input));
		}

		for (int x = 5000; x < 10000; x++)
		{
			Integer s = inputs.get(x);
			assertEquals(tree.remove(s), trie.remove(s));
			assertEquals(tree.size(), trie.n);
		}

		for (int x = 0; x < 50000; x++)
		{
			Integer input = rand.nextInt();
			assertEquals(tree.contains(input), trie.contains(input));
		}
	}

	@Test
	public void testRemoveVaryingLength()
	{
		Random rand = new Random();
		BinaryPatriciaTrie<String> trie = new BinaryPatriciaTrie<String>();
		TreeSet<String> tree = new TreeSet<String>();
		ArrayList<String> inputs = new ArrayList<String>();
		for (int x = 0; x < 10000; x++)
		{
			int inputLength = rand.nextInt(4) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			boolean trieResult = trie.add(input);
			boolean treeResult = tree.add(input);
			inputs.add(input);
			assertEquals(treeResult, trieResult);
			assertEquals(tree.size(), trie.n);
		}

		for (int x = 0; x < 5000; x++)
		{
			String s = inputs.get(x);
			assertEquals(tree.remove(s), trie.remove(s));
			assertEquals(tree.size(), trie.n);
		}

		verifyTrieNodeCompression(trie);

		for (int x = 0; x < 50000; x++)
		{
			String input = "";
			for (int y = 0; y < 10; y++)
				input += (char) (rand.nextInt(10) + '0');
			assertEquals(tree.contains(input), trie.contains(input));
		}

		for (int x = 5000; x < 10000; x++)
		{
			String s = inputs.get(x);
			assertEquals(tree.remove(s), trie.remove(s));
			assertEquals(tree.size(), trie.n);
		}

		for (int x = 0; x < 50000; x++)
		{
			String input = "";
			for (int y = 0; y < 10; y++)
				input += (char) (rand.nextInt(10) + '0');
			assertEquals(tree.contains(input), trie.contains(input));
		}
	}

	@Test
	public void testPrefixMany()
	{
		Random rand = new Random();
		BinaryPatriciaTrie<String> trie = new BinaryPatriciaTrie<String>();
		HashMap<String, ArrayList<String>> prefixes = new HashMap<String, ArrayList<String>>();
		for (int x = 0; x < 10000; x++)
		{
			int inputLength = rand.nextInt(6) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			boolean result = trie.add(input);
			if (result)
			{
				for (int y = 0; y < inputLength; y++)
				{
					ArrayList<String> vals = prefixes.get(input.substring(0, y + 1));
					if (vals == null)
					{
						vals = new ArrayList<String>();
						prefixes.put(input.substring(0, y + 1), vals);
					}
					vals.add(input);
				}
			}
		}

		for (String s : prefixes.keySet())
		{
			Iterator<String> iter = trie.iterator(s, s);
			ArrayList<String> expectedResults = prefixes.get(s);
			ArrayList<String> actualResults = new ArrayList<String>();
			while (iter.hasNext())
				actualResults.add(iter.next());

			assertEquals(expectedResults.size(), actualResults.size());
			for (int x = 0; x < actualResults.size(); x++)
			{
				assertTrue(expectedResults.contains(actualResults.get(x)));
			}
		}

		for (int x = 0; x < 50000; x++)
		{
			int inputLength = rand.nextInt(6) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			Iterator<String> iter = trie.iterator(input, input);
			while (iter.hasNext())
			{
				assertTrue(iter.next().startsWith(input));
			}
		}
	}

	@Test
	public void testIteratorAll()
	{
		Random rand = new Random();
		BinaryPatriciaTrie<String> trie = new BinaryPatriciaTrie<String>();
		HashSet<String> tree = new HashSet<String>();
		for (int x = 0; x < 10000; x++)
		{
			int inputLength = rand.nextInt(6) + 1;
			String input = "";
			for (int y = 0; y < inputLength; y++)
				input += (char) (rand.nextInt(10) + '0');
			boolean result = trie.add(input);
			boolean treeResult = tree.add(input);
			assertEquals(treeResult, result);
		}

		Iterator<String> iter = trie.iterator();
		int count = 0;
		while (iter.hasNext())
		{
			assertTrue(tree.contains(iter.next()));
			count++;
		}
		assertEquals(tree.size(), count);
	}
}
