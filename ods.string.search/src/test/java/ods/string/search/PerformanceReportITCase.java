package ods.string.search;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import ods.string.search.partition.EMPrefixSearchableSet;
import ods.string.search.partition.ExternalMemoryObjectCache;
import ods.string.search.partition.ExternalMemoryObjectCache.CompressType;
import ods.string.search.partition.ExternalMemorySkipList;
import ods.string.search.partition.ExternalMemorySplittableSet;
import ods.string.search.partition.ExternalMemoryTrie;
import ods.string.search.partition.splitsets.ExternalizableArrayList;
import ods.string.search.partition.splitsets.ExternalizableLinkedList;
import ods.string.search.partition.splitsets.ExternalizableListSet;
import ods.string.search.partition.splitsets.ExternalizableMemoryObject;
import ods.string.search.partition.splitsets.SplittableSet;
import ods.string.search.partition.splitsets.SplittableTreeSetAdapter;
import ods.string.search.partition.splitsets.Treap;

import org.junit.Test;

@SuppressWarnings("unchecked")
public class PerformanceReportITCase
{
	private enum InputType
	{
		SEQUENTIAL, RANDOM, WORDS
	}

	private class ReportCase
	{
		public String name;
		public EMPrefixSearchableSet<String> implementation;
		public InputType inputType;

		public ReportCase(String name, EMPrefixSearchableSet<String> impl, InputType inputType)
		{
			this.name = name;
			this.implementation = impl;
			this.inputType = inputType;
		}
	}

	private void printReport(String reportName, List<ReportCase> cases) throws Exception
	{
		int insertLimit = 1000000;
		int searchLimit = 1600000;
		int prefixLimit = 800000;
		int removeLimit = (int) (insertLimit * 0.75);

		warmUpVm(insertLimit, searchLimit, prefixLimit);

		BufferedWriter writer = new BufferedWriter(new FileWriter("target/" + reportName + ".tsv"));
		writeReportHeader(insertLimit, searchLimit, prefixLimit, removeLimit, writer);

		ArrayList<ArrayList<Double>> caseAvgs = new ArrayList<ArrayList<Double>>();
		for (ReportCase rCase : cases)
		{
			ArrayList<ArrayList<Double>> rawAttempts = new ArrayList<ArrayList<Double>>();
			for (int x = 0; x < 3; x++)
			{
				String resultName = rCase.name + "-rawIteration" + x;
				System.out.println("Starting tests for " + resultName);

				File curStorageDir = new File("target/" + rCase.name + "-" + x);
				Utils.deleteRecursively(curStorageDir);

				EMPrefixSearchableSet<String> curImpl = rCase.implementation
						.createNewStructure(curStorageDir);
				Random rand = new Random(x);
				ArrayList<Double> metrics = fillTreeRandomly(curImpl, insertLimit, searchLimit,
						prefixLimit, rand, rCase.inputType);
				curImpl.close();

				writeResultMetrics(writer, resultName, metrics);

				curImpl.close();
				Utils.deleteRecursively(curStorageDir);
				rawAttempts.add(metrics);
			}

			ArrayList<Double> avgMetrics = new ArrayList<Double>();
			for (int x = 0; x < rawAttempts.get(0).size(); x++)
			{
				double avg = 0;
				for (int y = 0; y < rawAttempts.size(); y++)
					avg += rawAttempts.get(y).get(x);
				avg = Utils.trimDecimals(avg / rawAttempts.size(), 2);
				avgMetrics.add(avg);
			}
			caseAvgs.add(avgMetrics);
		}

		writer.write("\n");
		writeReportHeader(insertLimit, searchLimit, prefixLimit, removeLimit, writer);
		for (int x = 0; x < cases.size(); x++)
			writeResultMetrics(writer, cases.get(x).name + "-avg", caseAvgs.get(x));

		writer.close();
	}

	private void warmUpVm(int insertLimit, int searchLimit, int prefixLimit) throws Exception
	{
		File warmUpDir = new File("target/warmUp");
		Utils.deleteRecursively(warmUpDir);
		ExternalMemorySplittableSet<String> tree = new ExternalMemorySplittableSet<String>(
				warmUpDir, 7500, 200000000l, new ExternalizableListSet<String>(
						new ExternalizableArrayList<String>(), false));
		fillTreeRandomly(tree, insertLimit, searchLimit, prefixLimit, new Random(),
				InputType.SEQUENTIAL);
		tree.close();
		Utils.deleteRecursively(warmUpDir);
	}

	private void writeResultMetrics(BufferedWriter writer, String resultName,
			ArrayList<Double> metrics) throws IOException
	{
		writer.write(resultName + "\t");
		for (int y = 0; y < 8; y++)
			writer.write(metrics.get(y) + "\t");

		writer.write("\t" + resultName + "\t");
		for (int y = 8; y < 14; y++)
			writer.write(metrics.get(y) + "\t");

		writer.write("\t" + resultName + "\t");
		for (int y = 14; y < 22; y++)
			writer.write(metrics.get(y) + "\t");

		writer.write("\t" + resultName + "\t");
		for (int y = 22; y < 31; y++)
			writer.write(metrics.get(y) + "\t");

		writer.write("\t" + resultName + "\t");
		for (int y = 31; y < 39; y++)
			writer.write(metrics.get(y) + "\t");

		writer.write("\n");
	}

	private void writeReportHeader(int insertLimit, int searchLimit, int prefixLimit,
			int removeLimit, BufferedWriter writer) throws IOException
	{
		writer.write("Insertions\t");
		for (int x = 0; x < 8; x++)
			writer.write((insertLimit * (x + 1) / 8) + "\t");

		writer.write("\tCacheStats\tDisk Usage (MB)\tUncompressed Size (MB)\tCompression Ratio\tParition Count\tSerialization Time (%)\tDisk Write Time (%)\t");

		writer.write("\tContains\t");
		for (int x = 0; x < 8; x++)
			writer.write((searchLimit * (x + 1) / 8) + "\t");

		writer.write("\tPrefixSearches\t");
		for (int x = 0; x < 8; x++)
			writer.write((prefixLimit * (x + 1) / 8) + "\t");

		writer.write("TotalResults\t\tRemovals\t");
		for (int x = 0; x < 8; x++)
			writer.write((removeLimit * (x + 1) / 8) + "\t");
		writer.write("\n");
	}

	private ArrayList<Double> fillTreeRandomly(EMPrefixSearchableSet<String> tree, long sizeLimit,
			long searchLimit, long prefixSearchLimit, Random rand, InputType inputType)
			throws Exception
	{

		Pattern tokenPat = Pattern.compile("(\\S+)");
		ArrayList<Double> resultMetrics = new ArrayList<Double>(50);
		ArrayList<String> insertionStrings = new ArrayList<String>((int) (sizeLimit + 2));
		if (inputType.equals(InputType.RANDOM) || inputType.equals(InputType.WORDS))
		{
			HashSet<String> insertedStrings = new HashSet<String>();
			if (inputType.equals(InputType.RANDOM))
			{
				while (insertionStrings.size() < sizeLimit)
				{
					String newInsert = Utils.generateRandomString(rand, 3, 12);
					if (!insertedStrings.contains(newInsert))
					{
						insertedStrings.add(newInsert);
						insertionStrings.add(newInsert);
					}
				}
			} else
			{
				BufferedReader reader = new BufferedReader(new InputStreamReader(
						new GZIPInputStream(getClass()
								.getResourceAsStream("/multiLangWords.txt.gz"))));
				Matcher m = null;
				while (insertionStrings.size() < sizeLimit)
				{
					while (m == null || !m.find())
					{
						m = tokenPat.matcher(reader.readLine());
					}
					String newInsert = m.group(1);
					if (!insertedStrings.contains(newInsert))
					{
						insertedStrings.add(newInsert);
						insertionStrings.add(newInsert);
					}
				}
				reader.close();
			}
		}

		System.gc();

		long reportInterval = sizeLimit / 8;
		long startTime = System.currentTimeMillis();
		for (int x = 0; x < sizeLimit; x++)
		{
			if (x % reportInterval == 0 && x != 0)
			{
				resultMetrics.add(convertMillisToSeconds(System.currentTimeMillis() - startTime));
				System.out.println(x + " insert operations, created in "
						+ (System.currentTimeMillis() - startTime) + "ms");
			}
			if (inputType.equals(InputType.RANDOM) || inputType.equals(InputType.WORDS))
				tree.add(insertionStrings.get(x));
			else if (inputType.equals(InputType.SEQUENTIAL))
			{
				String val = Utils.convertToFixedLengthString(x, 12);
				tree.add(val);
			}
		}
		long totalInsertionTime = System.currentTimeMillis() - startTime;
		resultMetrics.add(convertMillisToSeconds(totalInsertionTime));
		System.out.println(sizeLimit + " insert operations, created in " + totalInsertionTime
				+ "ms");

		tree.close();
		ExternalMemoryObjectCache<? extends ExternalizableMemoryObject> cache = tree
				.getObjectCache();
		File storageDir = cache.getStorageDirectory();
		long spaceUsage = Utils.getDirectorySpaceUsage(storageDir);
		int partitionCount = storageDir.list().length;
		double compressionRatio = cache.getCompressionRatio();
		long serializationTime = cache.getSerializationTime();
		long diskWriteTime = cache.getDiskWriteTime();

		resultMetrics.add(Utils.trimDecimals(spaceUsage / 1000000., 2));
		resultMetrics.add(Utils.trimDecimals(spaceUsage / compressionRatio / 1000000., 2));
		resultMetrics.add(Utils.trimDecimals(compressionRatio, 3));
		resultMetrics.add((double) partitionCount);
		resultMetrics.add(Utils.trimDecimals((double) serializationTime / totalInsertionTime, 3));
		resultMetrics.add(Utils.trimDecimals((double) diskWriteTime / totalInsertionTime, 3));

		BufferedReader wordListReader = null;
		Matcher m = null;
		if (inputType.equals(InputType.WORDS))
			wordListReader = new BufferedReader(new InputStreamReader(new GZIPInputStream(
					getClass().getResourceAsStream("/mergedBooks.txt.gz"))));

		System.gc();

		reportInterval = searchLimit / 8;
		startTime = System.currentTimeMillis();
		for (int x = 0; x < searchLimit; x++)
		{
			if (x % reportInterval == 0 && x != 0)
			{
				resultMetrics.add(convertMillisToSeconds(System.currentTimeMillis() - startTime));
				System.out.println(x + " find operations, performed in "
						+ (System.currentTimeMillis() - startTime) + "ms");
			}
			String input = null;
			if (inputType.equals(InputType.RANDOM))
			{
				if (rand.nextBoolean())
					input = Utils.generateRandomString(rand, 3, 12);
				else
					input = insertionStrings.get(rand.nextInt((int) sizeLimit));
			} else if (inputType.equals(InputType.SEQUENTIAL))
				input = Utils.convertToFixedLengthString((int) (x % sizeLimit), 12);
			else if (inputType.equals(InputType.WORDS))
			{
				while (m == null || !m.find())
				{
					m = tokenPat.matcher(wordListReader.readLine());
				}
				input = m.group(1);
			}

			tree.contains(input);
		}
		resultMetrics.add(convertMillisToSeconds(System.currentTimeMillis() - startTime));
		System.out.println(searchLimit + " find operations, performed in "
				+ (System.currentTimeMillis() - startTime) + "ms");

		if (wordListReader != null)
		{
			wordListReader.close();
			m = null;
			wordListReader = new BufferedReader(new InputStreamReader(new GZIPInputStream(
					getClass().getResourceAsStream("/mergedBooks.txt.gz"))));
		}

		System.gc();

		reportInterval = prefixSearchLimit / 8;
		startTime = System.currentTimeMillis();
		long iterationCount = 0;
		for (int x = 0; x < prefixSearchLimit; x++)
		{
			if (x % reportInterval == 0 && x != 0)
			{
				resultMetrics.add(convertMillisToSeconds(System.currentTimeMillis() - startTime));
				System.out.println(x + " prefix search operations, " + iterationCount
						+ " elements returned, performed in "
						+ (System.currentTimeMillis() - startTime) + "ms");
			}
			String input = null;
			if (inputType.equals(InputType.RANDOM))
			{
				if (rand.nextDouble() < 0.8)
					input = Utils.generateRandomString(rand, 5, 9);
				else
				{
					while (input == null || input.length() < 6)
						input = insertionStrings.get(rand.nextInt((int) sizeLimit));
				}
			} else if (inputType.equals(InputType.SEQUENTIAL))
				input = Utils.convertToFixedLengthString((int) (x % (sizeLimit / 100)), 10);
			else if (inputType.equals(InputType.WORDS))
			{
				while (input == null || input.length() < 3)
				{
					while (m == null || !m.find())
					{
						m = tokenPat.matcher(wordListReader.readLine());
					}
					input = m.group(1).substring(0, m.group(1).length() - 1);
				}
			}
			Iterator<String> iter = tree.iterator(input, input.substring(0, input.length() - 1)
					+ (char) (input.charAt(input.length() - 1) + 1));
			while (iter.hasNext())
			{
				iter.next();
				iterationCount++;
			}
		}
		resultMetrics.add(convertMillisToSeconds(System.currentTimeMillis() - startTime));
		resultMetrics.add((double) iterationCount);
		System.out.println(prefixSearchLimit + " prefix search operations, " + iterationCount
				+ " elements returned, performed in " + (System.currentTimeMillis() - startTime)
				+ "ms");

		if (wordListReader != null)
			wordListReader.close();

		// printSpaceStats(tree);

		int removalLimit = (int) (sizeLimit * 0.75);
		ArrayList<Integer> removeIndices = new ArrayList<Integer>(removalLimit + 2);
		if (inputType.equals(InputType.RANDOM))
		{
			HashSet<Integer> insertedIndices = new HashSet<Integer>();
			while (removeIndices.size() < removalLimit)
			{
				int newIndex = rand.nextInt((int) sizeLimit);
				if (!insertedIndices.contains(newIndex))
				{
					removeIndices.add(newIndex);
					insertedIndices.add(newIndex);
				}
			}
		}

		System.gc();

		reportInterval = removalLimit / 8;
		startTime = System.currentTimeMillis();
		for (int x = 0; x < removalLimit; x++)
		{
			if (x % reportInterval == 0 && x != 0)
			{
				resultMetrics.add(convertMillisToSeconds(System.currentTimeMillis() - startTime));
				System.out.println(x + " remove operations, performed in "
						+ (System.currentTimeMillis() - startTime) + "ms");
			}
			String input = null;
			if (inputType.equals(InputType.RANDOM))
				input = insertionStrings.get(removeIndices.get(x));
			else if (inputType.equals(InputType.SEQUENTIAL))
				input = Utils.convertToFixedLengthString((int) (x % sizeLimit), 12);
			else if (inputType.equals(InputType.WORDS))
				input = insertionStrings.get(x);

			tree.remove(input);
		}
		resultMetrics.add(convertMillisToSeconds(System.currentTimeMillis() - startTime));
		System.out.println(removalLimit + " remove operations, performed in "
				+ (System.currentTimeMillis() - startTime) + "ms");

		return resultMetrics;
	}

	private double convertMillisToSeconds(long millis)
	{
		return Utils.trimDecimals(millis / 1000., 2);
	}

	private class PartitionImplementation
	{
		public String name;
		public Object impl;

		public PartitionImplementation(String name, Object impl)
		{
			this.name = name;
			this.impl = impl;
		}
	}

	@Test
	public void testBTree() throws Exception
	{
		ArrayList<ReportCase> cases = new ArrayList<ReportCase>();
		File tmpDir = new File("target/tmp");

		ArrayList<PartitionImplementation> partitionImpls = new ArrayList<PartitionImplementation>();
		partitionImpls.add(new PartitionImplementation("ArrayList",
				new ExternalizableListSet<String>(new ExternalizableArrayList<String>(), false)));
		partitionImpls.add(new PartitionImplementation("Treap", new Treap<String>()));
		partitionImpls.add(new PartitionImplementation("RBT",
				new SplittableTreeSetAdapter<String>()));
		partitionImpls.add(new PartitionImplementation("LinkedLinear",
				new ExternalizableListSet<String>(new ExternalizableLinkedList<String>(), true)));

		for (PartitionImplementation pi : partitionImpls)
		{
			cases.add(new ReportCase("BTree-Seq-" + pi.name + "-5000",
					new ExternalMemorySplittableSet<String>(tmpDir, 5000, 50000000l,
							(SplittableSet<String>) pi.impl), InputType.SEQUENTIAL));
			cases.add(new ReportCase("BTree-Seq-" + pi.name + "-15000",
					new ExternalMemorySplittableSet<String>(tmpDir, 15000, 50000000l,
							(SplittableSet<String>) pi.impl), InputType.SEQUENTIAL));
			cases.add(new ReportCase("BTree-Seq-" + pi.name + "-25000",
					new ExternalMemorySplittableSet<String>(tmpDir, 25000, 50000000l,
							(SplittableSet<String>) pi.impl), InputType.SEQUENTIAL));
		}

		for (PartitionImplementation pi : partitionImpls)
		{
			cases.add(new ReportCase("BTree-Word-" + pi.name + "-50",
					new ExternalMemorySplittableSet<String>(tmpDir, 50, 50000000l,
							(SplittableSet<String>) pi.impl), InputType.WORDS));
			cases.add(new ReportCase("BTree-Word-" + pi.name + "-100",
					new ExternalMemorySplittableSet<String>(tmpDir, 100, 50000000l,
							(SplittableSet<String>) pi.impl), InputType.WORDS));
			cases.add(new ReportCase("BTree-Word-" + pi.name + "-200",
					new ExternalMemorySplittableSet<String>(tmpDir, 200, 50000000l,
							(SplittableSet<String>) pi.impl), InputType.WORDS));
		}

		for (PartitionImplementation pi : partitionImpls)
		{
			cases.add(new ReportCase("BTree-Rand-" + pi.name + "-40",
					new ExternalMemorySplittableSet<String>(tmpDir, 40, 50000000l,
							(SplittableSet<String>) pi.impl), InputType.RANDOM));
			cases.add(new ReportCase("BTree-Rand-" + pi.name + "-70",
					new ExternalMemorySplittableSet<String>(tmpDir, 70, 50000000l,
							(SplittableSet<String>) pi.impl), InputType.RANDOM));
			cases.add(new ReportCase("BTree-Rand-" + pi.name + "-150",
					new ExternalMemorySplittableSet<String>(tmpDir, 150, 50000000l,
							(SplittableSet<String>) pi.impl), InputType.RANDOM));
		}

		printReport("bPlusTreePerformance", cases);
	}

	@Test
	public void testSkipList() throws Exception
	{
		ArrayList<ReportCase> cases = new ArrayList<ReportCase>();
		File tmpDir = new File("target/tmp");

		ArrayList<PartitionImplementation> partitionImpls = new ArrayList<PartitionImplementation>();
		partitionImpls.add(new PartitionImplementation("ArrayList",
				new ExternalizableListSet<String>(new ExternalizableArrayList<String>(), false)));
		partitionImpls.add(new PartitionImplementation("Treap", new Treap<String>()));
		partitionImpls.add(new PartitionImplementation("RBT",
				new SplittableTreeSetAdapter<String>()));
		partitionImpls.add(new PartitionImplementation("LinkedLinear",
				new ExternalizableListSet<String>(new ExternalizableLinkedList<String>(), true)));

		for (PartitionImplementation pi : partitionImpls)
		{
			cases.add(new ReportCase("SkipList-Seq-" + pi.name + "-3000",
					new ExternalMemorySkipList<String>(tmpDir, 1. / 3000., 50000000l,
							(SplittableSet<String>) pi.impl), InputType.SEQUENTIAL));
			cases.add(new ReportCase("SkipList-Seq-" + pi.name + "-6000",
					new ExternalMemorySkipList<String>(tmpDir, 1. / 6000., 50000000l,
							(SplittableSet<String>) pi.impl), InputType.SEQUENTIAL));
			cases.add(new ReportCase("SkipList-Seq-" + pi.name + "-12000",
					new ExternalMemorySkipList<String>(tmpDir, 1. / 12000., 50000000l,
							(SplittableSet<String>) pi.impl), InputType.SEQUENTIAL));
		}

		for (PartitionImplementation pi : partitionImpls)
		{
			cases.add(new ReportCase("SkipList-Word-" + pi.name + "-50",
					new ExternalMemorySkipList<String>(tmpDir, 1. / 50., 50000000l,
							(SplittableSet<String>) pi.impl), InputType.WORDS));
			cases.add(new ReportCase("SkipList-Word-" + pi.name + "-100",
					new ExternalMemorySkipList<String>(tmpDir, 1. / 100., 50000000l,
							(SplittableSet<String>) pi.impl), InputType.WORDS));
			cases.add(new ReportCase("SkipList-Word-" + pi.name + "-200",
					new ExternalMemorySkipList<String>(tmpDir, 1. / 200., 50000000l,
							(SplittableSet<String>) pi.impl), InputType.WORDS));
		}

		for (PartitionImplementation pi : partitionImpls)
		{
			cases.add(new ReportCase("SkipList-Rand-" + pi.name + "-25",
					new ExternalMemorySkipList<String>(tmpDir, 1. / 25., 50000000l,
							(SplittableSet<String>) pi.impl), InputType.RANDOM));
			cases.add(new ReportCase("SkipList-Rand-" + pi.name + "-50",
					new ExternalMemorySkipList<String>(tmpDir, 1. / 50., 50000000l,
							(SplittableSet<String>) pi.impl), InputType.RANDOM));
			cases.add(new ReportCase("SkipList-Rand-" + pi.name + "-100",
					new ExternalMemorySkipList<String>(tmpDir, 1. / 100., 50000000l,
							(SplittableSet<String>) pi.impl), InputType.RANDOM));
		}

		printReport("skipListPerformance", cases);
	}

	@Test
	public void testTrie() throws Exception
	{
		ArrayList<ReportCase> cases = new ArrayList<ReportCase>();
		File tmpDir = new File("target/tmp");

		ArrayList<PartitionImplementation> partitionImpls = new ArrayList<PartitionImplementation>();
		partitionImpls.add(new PartitionImplementation("EvenSplits",
				new ExternalMemoryTrie<String>(tmpDir, 50, 50000000l, 0)));
		partitionImpls.add(new PartitionImplementation("4MinDepth", new ExternalMemoryTrie<String>(
				tmpDir, 50, 50000000l, 4)));

		for (PartitionImplementation pi : partitionImpls)
		{
			((ExternalMemoryTrie<String>) pi.impl).setMaxSetSize(3000);
			cases.add(new ReportCase("PatTrie-Seq-" + pi.name + "-3000",
					new ExternalMemoryTrie<String>(tmpDir, (ExternalMemoryTrie<String>) pi.impl),
					InputType.SEQUENTIAL));
			((ExternalMemoryTrie<String>) pi.impl).setMaxSetSize(6000);
			cases.add(new ReportCase("PatTrie-Seq-" + pi.name + "-6000",
					new ExternalMemoryTrie<String>(tmpDir, (ExternalMemoryTrie<String>) pi.impl),
					InputType.SEQUENTIAL));
			((ExternalMemoryTrie<String>) pi.impl).setMaxSetSize(12000);
			cases.add(new ReportCase("PatTrie-Seq-" + pi.name + "-12000",
					new ExternalMemoryTrie<String>(tmpDir, (ExternalMemoryTrie<String>) pi.impl),
					InputType.SEQUENTIAL));
		}

		for (PartitionImplementation pi : partitionImpls)
		{
			((ExternalMemoryTrie<String>) pi.impl).setMaxSetSize(50);
			cases.add(new ReportCase("PatTrie-Word-" + pi.name + "-50",
					new ExternalMemoryTrie<String>(tmpDir, (ExternalMemoryTrie<String>) pi.impl),
					InputType.WORDS));
			((ExternalMemoryTrie<String>) pi.impl).setMaxSetSize(100);
			cases.add(new ReportCase("PatTrie-Word-" + pi.name + "-100",
					new ExternalMemoryTrie<String>(tmpDir, (ExternalMemoryTrie<String>) pi.impl),
					InputType.WORDS));
			((ExternalMemoryTrie<String>) pi.impl).setMaxSetSize(200);
			cases.add(new ReportCase("PatTrie-Word-" + pi.name + "-200",
					new ExternalMemoryTrie<String>(tmpDir, (ExternalMemoryTrie<String>) pi.impl),
					InputType.WORDS));
		}

		for (PartitionImplementation pi : partitionImpls)
		{
			((ExternalMemoryTrie<String>) pi.impl).setMaxSetSize(40);
			cases.add(new ReportCase("PatTrie-Rand-" + pi.name + "-40",
					new ExternalMemoryTrie<String>(tmpDir, (ExternalMemoryTrie<String>) pi.impl),
					InputType.RANDOM));
			((ExternalMemoryTrie<String>) pi.impl).setMaxSetSize(80);
			cases.add(new ReportCase("PatTrie-Rand-" + pi.name + "-80",
					new ExternalMemoryTrie<String>(tmpDir, (ExternalMemoryTrie<String>) pi.impl),
					InputType.RANDOM));
			((ExternalMemoryTrie<String>) pi.impl).setMaxSetSize(160);
			cases.add(new ReportCase("PatTrie-Rand-" + pi.name + "-160",
					new ExternalMemoryTrie<String>(tmpDir, (ExternalMemoryTrie<String>) pi.impl),
					InputType.RANDOM));
		}

		printReport("binaryPatPerformance", cases);
	}

	@Test
	public void testNullSet() throws Exception
	{
		ArrayList<ReportCase> cases = new ArrayList<ReportCase>();
		cases.add(new ReportCase("NullSet-Word", new NullSet<String>(), InputType.WORDS));
		cases.add(new ReportCase("NullSet-Seq", new NullSet<String>(), InputType.SEQUENTIAL));
		cases.add(new ReportCase("NullSet-Rand", new NullSet<String>(), InputType.RANDOM));

		printReport("nullSetPerformance", cases);
	}

	@Test
	public void testCompression() throws Exception
	{
		ArrayList<PartitionImplementation> partitionImpls = new ArrayList<PartitionImplementation>();
		partitionImpls.add(new PartitionImplementation("CompressSnappy",
				new ExternalMemoryObjectCache<>(new File("target/tmp"), 50000000l,
						CompressType.SNAPPY)));
		partitionImpls.add(new PartitionImplementation("CompressGzip",
				new ExternalMemoryObjectCache<>(new File("target/tmp"), 50000000l,
						CompressType.GZIP)));
		partitionImpls.add(new PartitionImplementation("CompressNone",
				new ExternalMemoryObjectCache<>(new File("target/tmp"), 50000000l,
						CompressType.NONE)));

		ArrayList<ReportCase> cases = new ArrayList<ReportCase>();

		for (PartitionImplementation pi : partitionImpls)
		{
			cases.add(new ReportCase("BTree-Rand-" + pi.name + "-ArrayList-70",
					new ExternalMemorySplittableSet<String>((ExternalMemoryObjectCache<?>) pi.impl,
							70, new ExternalizableListSet<String>(
									new ExternalizableArrayList<String>(), false)),
					InputType.RANDOM));
			cases.add(new ReportCase("BTree-Rand-" + pi.name + "-150",
					new ExternalMemorySplittableSet<String>((ExternalMemoryObjectCache<?>) pi.impl,
							150, new ExternalizableListSet<String>(
									new ExternalizableArrayList<String>(), false)),
					InputType.RANDOM));
		}

		printReport("bTreeCompressionPerformance", cases);
	}

	@Test
	public void testDirSpam() throws Exception
	{
		File dir = new File("target/dirSpam");
		Utils.deleteRecursively(dir);
		dir.mkdirs();
		long startTime = System.currentTimeMillis();
		for (int x = 0; x < 10000000; x++)
		{
			if (x % 10000 == 0)
				System.out.println(x + " create rate "
						+ ((System.currentTimeMillis() - startTime) / (double) x));
			new File(dir, x + "").createNewFile();
		}
		Utils.deleteRecursively(dir);
	}
}
