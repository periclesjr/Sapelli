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

package uk.ac.ucl.excites.sapelli.collector.loading;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.FileUtils;

import uk.ac.ucl.excites.sapelli.collector.io.FileStorageException;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.loading.tasks.LoadingTask;
import uk.ac.ucl.excites.sapelli.collector.loading.tasks.LoadingTaskExecutor;
import uk.ac.ucl.excites.sapelli.collector.loading.tasks.TTSSynthesisTask;
import uk.ac.ucl.excites.sapelli.collector.loading.xml.ProjectParser;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.shared.io.Unzipper;

/**
 * Loader for .sapelli (or .excites or .sap) files, which are actually just renamed ZIP files
 * 
 * @author mstevens, Michalis Vitos
 * 
 */
public class ProjectLoader implements LoadingTaskExecutor
{
	
	// STATICS
	static public final String[] SAPELLI_FILE_EXTENSIONS = { "sap", "sapelli", "excites", "zip" };
	static public final String PROJECT_FILE = "PROJECT.xml";

	/**
	 * Checks if the given file has a support sapelli file extension
	 * 
	 * @param file
	 * @return
	 */
	static public boolean HasSapelliFileExtension(File file)
	{
		String path = file.getAbsolutePath().toLowerCase();
		for(String extention : ProjectLoader.SAPELLI_FILE_EXTENSIONS)
			if(path.endsWith("." + extention))
				return true;
		return false;
	}
	
	/**
	 * @param folderPath path to folder in which the PROJECT.xml file resides
	 * @return a project instance or null in case something went wrong
	 */
	static public Project ParseProject(String folderPath)
	{
		try
		{
			return new ProjectParser().parseProject(new File(folderPath + File.separator + PROJECT_FILE));
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			return null;
		}
	}
	
	// DYNAMICS
	private final ProjectLoaderCallback callback;
	private final FileStorageProvider fileStorageProvider;
	private final File tempFolder;
	private final ProjectParser parser;

	/**
	 * @param fileStorageProvider
	 * @throws FileStorageException
	 */
	public ProjectLoader(FileStorageProvider fileStorageProvider) throws FileStorageException
	{
		this(null, fileStorageProvider); // no callback used
	}
	
	/**
	 * @param callback
	 * @param fileStorageProvider
	 * @throws FileStorageException
	 */
	public ProjectLoader(ProjectLoaderCallback callback, FileStorageProvider fileStorageProvider) throws FileStorageException
	{
		this.callback = callback;
		this.fileStorageProvider = fileStorageProvider;
		
		// Get/create the temp folder:
		tempFolder = fileStorageProvider.getTempFolder(true);
		
		// Create the project folder
		this.parser = new ProjectParser();
	}

	/**
	 * Extract the given sapelli file (provided as a File object) and parses the PROJECT.xml; returns the resulting Project object.
	 * 
	 * @param sapelliFile
	 * @return the loaded Project
	 * @throws Exception
	 */
	public Project load(File sapelliFile) throws Exception
	{
		if(sapelliFile == null || !sapelliFile.exists() || sapelliFile.length() == 0)
			throw new IllegalArgumentException("Invalid Sapelli file");
		return load(new FileInputStream(sapelliFile));
	}
	
	/**
	 * Extract the given sapelli file (provided as an InputStream) and parses the PROJECT.xml; returns the resulting Project object.
	 * 
	 * @param sapelliFileStream
	 * @return the loaded Project
	 * @throws Exception
	 */
	public Project load(InputStream sapelliFileStream) throws Exception
	{
		Project project = null;
		File extractFolder = new File(tempFolder.getAbsolutePath() + File.separator + System.currentTimeMillis());
		try
		{
			// STEP 1 - Extract the content of the Sapelli file to a new subfolder of the temp folder:
			try
			{
				FileHelpers.createFolder(extractFolder);
				Unzipper.unzip(sapelliFileStream, extractFolder);
			}
			catch(Exception e)
			{
				throw new Exception("Error on extracting contents of Sapelli file.", e);
			}
			
			// STEP 2 - Parse PROJECT.xml:
			try
			{	
				project = parser.parseProject(new File(extractFolder.getAbsolutePath() + File.separator + PROJECT_FILE));
			}
			catch(Exception e)
			{
				throw new Exception("Error on parsing " + PROJECT_FILE, e);
			}
			
			// STEP 3 - Check if project is acceptable:
			checkProject(project); // throws IllegalArgumentException if something is wrong

			// STEP 4 - Create move extracted files to project folder:
			try
			{
				FileHelpers.moveDirectory(extractFolder, fileStorageProvider.getProjectInstallationFolder(project, true));
			}
			catch(Exception e)
			{
				throw new Exception("Error on moving extracted files to project folder.", e);
			}
			
			// STEP 5 - Post-processing of loader tasks:
			for(LoadingTask task : parser.getLoadingTasks())
			{
				try
				{
					task.execute(this);
				}
				catch(Exception e)
				{
					throw new Exception("Error on executing loader task", e);
				}
			}
		}
		catch(Exception e)
		{
			// 	delete temp folder:
			FileUtils.deleteQuietly(extractFolder);
			//	re-throw Exception:
			throw e;
		}
		
		// Return project object:
		return project;
	}
	
	/**
	 * @param project
	 * @throws IllegalArgumentException when the project is not acceptable
	 */
	protected void checkProject(Project project) throws IllegalArgumentException
	{
		if(callback != null)
			callback.checkProject(project); // throws IllegalArgumentException if something is wrong
	}

	/**
	 * To be overridden by subclass!
	 * 
	 * @see uk.ac.ucl.excites.sapelli.collector.loading.tasks.LoadingTaskExecutor#execute(uk.ac.ucl.excites.sapelli.collector.loading.tasks.TTSSynthesisTask)
	 */
	@Override
	public void execute(TTSSynthesisTask ttsTask)
	{
		System.err.println("TTSSysthesis not supported!");
	}
	
	/**
	 * Parses the PROJECT.xml present in the given sapelli file (provided as a File object), without extracting the contents to storage and without executing load tasks; returns the resulting Project object.
	 * 
	 * @param sapelliFile
	 * @return the loaded Project
	 * @throws Exception
	 */
	public Project loadParseOnly(File sapelliFile) throws Exception
	{
		if(sapelliFile == null || !sapelliFile.exists() || sapelliFile.length() == 0)
			throw new IllegalArgumentException("Invalid Sapelli file");
		return loadParseOnly(new FileInputStream(sapelliFile));
	}

	/**
	 * Parses the PROJECT.xml present in the given sapelli file (provided as an InputStream), without extracting the contents to storage and without executing load tasks; returns the resulting Project object.
	 * 
	 * @param sapelliFileStream
	 * @return the loaded Project
	 * @throws Exception
	 */
	public Project loadParseOnly(InputStream sapelliFileStream) throws Exception
	{
		try
		{	// Parse PROJECT.xml:
			Project project = parser.parseProject(Unzipper.getInputStreamForFileInZip(sapelliFileStream, PROJECT_FILE));
			// Check if project is acceptable:
			checkProject(project); // throws IllegalArgumentException if something is wrong
			// all OK:
			return project;
		}
		catch(Exception e)
		{
			throw new Exception("Error on parsing " + PROJECT_FILE, e);
		}
	}
	
	public List<String> getParserWarnings()
	{
		return parser.getWarnings();
	}
	
}