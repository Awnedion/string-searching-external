package ods.string.search;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import ods.string.search.partition.EMPrefixSearchableSet;
import ods.string.search.partition.ExternalMemoryObjectCache;
import ods.string.search.partition.ExternalMemorySplittableSet;
import ods.string.search.partition.splitsets.ExternalizableArrayList;
import ods.string.search.partition.splitsets.ExternalizableListSet;
import ods.string.search.partition.splitsets.ExternalizableMemoryObject;

import org.junit.Test;

public class PerformanceReportITCase
{
	private enum InputType
	{
		SEQUENTIAL, RANDOM
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
		int insertLimit = 1200000;
		int searchLimit = 1600000;
		int prefixLimit = 1000000;
		int removeLimit = (int) (insertLimit * 0.75);

		BufferedWriter writer = new BufferedWriter(new FileWriter("target/" + reportName + ".tsv"));
		writeReportHeader(insertLimit, searchLimit, prefixLimit, removeLimit, writer);

		ArrayList<ArrayList<Double>> caseAvgs = new ArrayList<ArrayList<Double>>();
		for (ReportCase rCase : cases)
		{
			ArrayList<ArrayList<Double>> rawAttempts = new ArrayList<ArrayList<Double>>();
			for (int x = 0; x < 3; x++)
			{
				File curStorageDir = new File("target/" + rCase.name + "-" + x);
				Utils.deleteRecursively(curStorageDir);

				EMPrefixSearchableSet<String> curImpl = rCase.implementation
						.createNewStructure(curStorageDir);
				Random rand = new Random(x);
				ArrayList<Double> metrics = fillTreeRandomly(curImpl, insertLimit, searchLimit,
						prefixLimit, rand, rCase.inputType);
				curImpl.close();
				writeResultMetrics(writer, rCase.name + "-rawIteration" + x, metrics);

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
	{// TODO replace reflections with interface call

		ArrayList<Double> resultMetrics = new ArrayList<Double>(50);
		ArrayList<String> insertionStrings = new ArrayList<String>((int) (sizeLimit + 2));
		if (inputType.equals(InputType.RANDOM))
		{
			HashSet<String> insertedStrings = new HashSet<String>();
			while (insertionStrings.size() < sizeLimit)
			{
				String newInsert = Utils.generateRandomString(rand, 3, 12);
				if (!insertedStrings.contains(newInsert))
				{
					insertedStrings.add(newInsert);
					insertionStrings.add(newInsert);
				}
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
			if (inputType.equals(InputType.RANDOM))
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

			tree.contains(input);
		}
		resultMetrics.add(convertMillisToSeconds(System.currentTimeMillis() - startTime));
		System.out.println(searchLimit + " find operations, performed in "
				+ (System.currentTimeMillis() - startTime) + "ms");

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
				input = Utils.convertToFixedLengthString((int) (x % sizeLimit), 10);
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

	@Test
	public void testBTree() throws Exception
	{
		ArrayList<ReportCase> cases = new ArrayList<ReportCase>();
		cases.add(new ReportCase("BTree-Seq-ArrayList-70", new ExternalMemorySplittableSet<String>(
				new File("target/tmp"), 7500, 50000000l, new ExternalizableListSet<String>(
						new ExternalizableArrayList<String>(), false)), InputType.SEQUENTIAL));
		cases.add(new ReportCase("BTree-Rand-ArrayList-70",
				new ExternalMemorySplittableSet<String>(new File("target/tmp"), 70, 50000000l,
						new ExternalizableListSet<String>(new ExternalizableArrayList<String>(),
								false)), InputType.RANDOM));

		printReport("bPlusTreePerformance", cases);
	}
}
