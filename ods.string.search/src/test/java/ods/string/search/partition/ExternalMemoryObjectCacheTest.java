package ods.string.search.partition;

import static org.junit.Assert.assertEquals;

import java.io.File;

import ods.string.search.Utils;
import ods.string.search.partition.ExternalMemoryObjectCache.CompressType;
import ods.string.search.partition.splitsets.CoolString;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExternalMemoryObjectCacheTest
{

	@Before
	public void setup()
	{
		Assert.assertTrue(Utils.deleteRecursively(new File("target/blocks")));
	}

	@Test
	public void testStorage()
	{
		ExternalMemoryObjectCache<CoolString> cache = new ExternalMemoryObjectCache<CoolString>(
				new File("target/blocks"), 10, CompressType.SNAPPY);
		for (int x = 0; x < 100; x++)
			cache.register(x + "", new CoolString(x + ""));

		for (int x = 0; x < 100; x++)
			assertEquals(new CoolString(x + ""), cache.get(x + ""));

		assertEquals(100, new File("target/blocks").list().length);
		cache.register("101", new CoolString("hooligan"));
		cache.close();
		assertEquals(101, new File("target/blocks").list().length);
	}
}
