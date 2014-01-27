package ods.string.search.partition;

import java.io.File;

import org.junit.Test;

public class ExternalMemoryTrieTest
{
	@Test
	public void testAddSearchRemove()
	{
		ExternalMemoryTrie<String> tree = new ExternalMemoryTrie<String>(new File("target/treap"),
				50, 100000000, 0);
		ExternalMemorySplittableSetTest.testOperations(tree);
	}

	@Test
	public void testAddSearchRemoveMinDepth()
	{
		ExternalMemoryTrie<String> tree = new ExternalMemoryTrie<String>(new File("target/treap"),
				50, 100000000, 3);
		ExternalMemorySplittableSetTest.testOperations(tree);
	}

	@Test
	public void testIteratorAll()
	{
		ExternalMemoryTrie<Integer> tree = new ExternalMemoryTrie<Integer>(
				new File("target/treap"), 50, 100000000, 0);

		ExternalMemorySplittableSetTest.testFullIterator(tree);
	}

	@Test
	public void testIteratorPrefix()
	{
		ExternalMemoryTrie<String> tree = new ExternalMemoryTrie<String>(new File("target/treap"),
				50, 100000000, 0);
		ExternalMemorySplittableSetTest.testPrefixIterators(tree);
	}
}
