package ods.string.search;

import java.io.File;
import java.util.Random;

public class Utils
{
	/**
	 * Given a file or directory, this method will recursively delete everything within it.
	 * 
	 * @return True if the file/diretory is deleted successfully, false otherwise.
	 */
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

	/**
	 * Returns the total space consumption of the specified directory in bytes.
	 */
	public static long getDirectorySpaceUsage(File dir)
	{
		long totalSpaceUsage = 0;
		for (File f : dir.listFiles())
		{
			totalSpaceUsage += f.length();
		}
		return totalSpaceUsage;
	}

	/**
	 * Trims the specified decimal number to the specified number of decimal places by rounding to
	 * the nearest and returns it.
	 * 
	 * @param baseVal
	 *            The number whose decimals should be trimmed.
	 * @param decimalsToKeep
	 *            The number of decimal places to keep.
	 */
	public static double trimDecimals(double baseVal, int decimalsToKeep)
	{
		double result = Math.round(baseVal * Math.pow(10, decimalsToKeep));
		return result / Math.pow(10, decimalsToKeep);
	}

	/**
	 * Returns a random string with a random length.
	 * 
	 * @param rand
	 *            The random number generator to use.
	 * @param minLength
	 *            The minimum length of string to return.
	 * @param maxLength
	 *            The maximum length of string to return.
	 */
	public static String generateRandomString(Random rand, int minLength, int maxLength)
	{
		int inputLength = rand.nextInt(maxLength - minLength + 1) + minLength;
		StringBuilder input = new StringBuilder(inputLength);
		for (int y = 0; y < inputLength; y++)
			input.append((char) (rand.nextInt(10) + '0'));
		return input.toString();
	}

	/**
	 * Converts an integer into a fixed length string. If the integer is shorter than the specified
	 * length, it will be prepended with zeroes.
	 * 
	 * @param value
	 *            The integer to convert into a string.
	 * @param stringLength
	 *            The length the final string should be.
	 */
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
