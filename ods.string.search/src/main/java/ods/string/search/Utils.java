package ods.string.search;

import java.io.File;
import java.util.Random;

public class Utils
{
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

	public static long getDirectorySpaceUsage(File dir)
	{
		long totalSpaceUsage = 0;
		for (File f : dir.listFiles())
		{
			totalSpaceUsage += f.length();
		}
		return totalSpaceUsage;
	}

	public static double trimDecimals(double baseVal, int decimalsToKeep)
	{
		double result = Math.round(baseVal * Math.pow(10, decimalsToKeep));
		return result / Math.pow(10, decimalsToKeep);
	}

	public static String generateRandomString(Random rand, int minLength, int maxLength)
	{
		int inputLength = rand.nextInt(maxLength - minLength + 1) + minLength;
		StringBuilder input = new StringBuilder(inputLength);
		for (int y = 0; y < inputLength; y++)
			input.append((char) (rand.nextInt(10) + '0'));
		return input.toString();
	}

	public static String convertToFixedLengthString(int value, int stringLength)
	{
		StringBuilder result = new StringBuilder(stringLength);
		String s = String.valueOf(value);
		for (int x = 0; x < stringLength - s.length(); x++)
			result.append("0");
		result.append(s);
		return result.toString();
	}
}
