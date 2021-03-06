/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2014 University College London - ExCiteS group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.relay.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * File I/O helpers
 * 
 * @author mstevens, Michalis Vitos
 * 
 */
public final class FileHelpers
{
	public static final String TAG = "FileHelpers";

	private FileHelpers()
	{
	} // no-one should instantiate this class

	static public boolean isValidFileName(String filename)
	{
		if(filename.contains("*"))
			return false;
		if(filename.contains("?"))
			return false;
		if(filename.contains("<"))
			return false;
		if(filename.contains(">"))
			return false;
		if(filename.contains(":"))
			return false;
		if(filename.contains("\""))
			return false;
		if(filename.contains("\\"))
			return false;
		if(filename.contains("/"))
			return false;
		if(filename.contains("|"))
			return false;
		if(filename.contains("\n"))
			return false;
		if(filename.contains("\t"))
			return false;
		return true;
	}

	static public String makeValidFileName(String filename)
	{
		if(filename != null)
		{
			filename = filename.replace('*', '+');
			filename = filename.replace('?', '_');
			filename = filename.replace('<', '(');
			filename = filename.replace('>', ')');
			filename = filename.replace(':', '-');
			filename = filename.replace('"', '\'');
			filename = filename.replace('\\', '_');
			filename = filename.replace('/', '_');
			filename = filename.replace('|', ';');
			filename = filename.replace('\n', '_');
			filename = filename.replace('\t', '_');
		}
		return filename;
	}

	/**
	 * Method to Copy a file
	 * 
	 * @param srcFilepath
	 * @param dstFilepath
	 * @throws IOException
	 */
	public static void copyFile(String srcFilepath, String dstFilepath)
	{
		copyFile(new File(srcFilepath), new File(dstFilepath));
	}
	
	public static void copyFile(File srcFile, File dstFile)
	{
		try
		{
			// Get the parent directory
			File parentDir = new File(dstFile.getParentFile().getAbsolutePath());
			parentDir.mkdirs();

			if(!dstFile.exists())
			{
				dstFile.createNewFile();
			}

			InputStream in = new FileInputStream(srcFile);
			OutputStream out = new FileOutputStream(dstFile);

			// Transfer bytes from in to out
			byte[] buf = new byte[1024];
			int len;
			while((len = in.read(buf)) > 0)
			{
				out.write(buf, 0, len);
			}
			in.close();
			out.close();
		}
		catch(IOException e)
		{
			System.err.println("FileIO error: " + e.getLocalizedMessage());
			e.printStackTrace();
		}		
	}

	/**
	 * Delete a file
	 * 
	 * @param filePath
	 * @return whether the file was deleted or not
	 */
	public static boolean deleteFile(String filePath)
	{
		return (new File(filePath)).delete();
	}

	/**
	 * Move a file
	 * 
	 * @param srcFilepath
	 * @param dstFilepath
	 */
	public static void moveFile(String srcFilepath, String dstFilepath)
	{
		try
		{
			File from = new File(srcFilepath);
			File to = new File(dstFilepath);
			if(!from.equals(to))
				throw new IllegalArgumentException("Source and destination files must be different.");
			if(!from.renameTo(to))
			{
			      copyFile(from, to);
			      if(!from.delete())
			    	  throw new IOException("Unable to delete " + from);
			}
		}
		catch(IOException e)
		{
			System.err.println("FileIO error: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Attempts to create the necessary (containing) folder(s) for a given path
	 * 
	 * @param folderPath
	 * @return success (whether the directory exists now)
	 */
	public static boolean createFolder(String folderPath)
	{
		return createFolder(new File(folderPath));
	}
	
	/**
	 * Attempts to create the necessary (containing) folder(s) for a given path
	 * 
	 * @param folderPath
	 * @return success (whether the directory exists now)
	 */
	public static boolean createFolder(File folder)
	{
		if(!folder.exists() || !folder.isDirectory())
			return folder.mkdirs();
		return true;
	}

}
