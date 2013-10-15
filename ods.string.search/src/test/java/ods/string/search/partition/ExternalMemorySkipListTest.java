package ods.string.search.partition;

import java.io.File;

import org.junit.Test;

public class ExternalMemorySkipListTest
{
	@Test
	public void testAddSearchRemoveEMSkipList()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"));
		ExternalMemorySplittableSetTest.testOperations(tree);
	}

	@Test
	public void testAddSearchRemoveEMSkipListSet()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"), 1 / 35., 1000000000, Treap.class);
		ExternalMemorySplittableSetTest.testOperations(tree);
	}

	@Test
	public void testIteratorAll()
	{
		ExternalMemorySkipList<Integer> tree = new ExternalMemorySkipList<Integer>(new File(
				"target/treap"));

		ExternalMemorySplittableSetTest.testFullIterator(tree);
	}

	@Test
	public void testIteratorAllSet()
	{
		ExternalMemorySkipList<Integer> tree = new ExternalMemorySkipList<Integer>(new File(
				"target/treap"), 1 / 35., 1000000000, Treap.class);
		ExternalMemorySplittableSetTest.testFullIterator(tree);
	}

	@Test
	public void testIteratorRange()
	{
		ExternalMemorySkipList<Integer> tree = new ExternalMemorySkipList<Integer>(new File(
				"target/treap"));
		ExternalMemorySplittableSetTest.testRangeIterators(tree);
	}

	@Test
	public void testIteratorRangeSet()
	{
		ExternalMemorySkipList<Integer> tree = new ExternalMemorySkipList<Integer>(new File(
				"target/treap"), 1 / 35., 1000000000, Treap.class);
		ExternalMemorySplittableSetTest.testRangeIterators(tree);
	}

	@Test
	public void testIteratorPrefix()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"));
		ExternalMemorySplittableSetTest.testPrefixIterators(tree);
	}

	@Test
	public void testIteratorPrefixSet()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"), 1 / 35., 1000000000, Treap.class);
		ExternalMemorySplittableSetTest.testPrefixIterators(tree);
	}
}
