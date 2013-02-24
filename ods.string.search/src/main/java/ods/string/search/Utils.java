package ods.string.search;

import java.io.File;

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
}
