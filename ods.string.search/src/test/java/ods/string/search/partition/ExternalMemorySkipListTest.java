package ods.string.search.partition;

import java.io.File;

import ods.string.search.Utils;
import ods.string.search.partition.splitsets.ExternalizableArrayList;
import ods.string.search.partition.splitsets.ExternalizableListSet;
import ods.string.search.partition.splitsets.Treap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExternalMemorySkipListTest
{
	@Before
	public void setup()
	{
		Assert.assertTrue(Utils.deleteRecursively(new File("target/treap")));
	}

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
				"target/treap"), 1 / 35., 1000000000, new Treap<String>());
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
				"target/treap"), 1 / 35., 1000000000, new Treap<Integer>());
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
				"target/treap"), 1 / 35., 1000000000, new Treap<Integer>());
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
				"target/treap"), 1 / 35., 1000000000, new Treap<String>());
		ExternalMemorySplittableSetTest.testPrefixIterators(tree);
	}

	@Test
	public void testIteratorPrefixArray()
	{
		ExternalMemorySkipList<String> tree = new ExternalMemorySkipList<String>(new File(
				"target/treap"), 1 / 20., 50000000, new ExternalizableListSet<String>(
				new ExternalizableArrayList<String>(), false));

		ExternalMemorySplittableSetTest.testPrefixWithManyMatches(tree);
	}
}
