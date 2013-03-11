package uk.ac.ucl.excites.collector;

import group.pals.android.lib.ui.filechooser.FileChooserActivity;
import group.pals.android.lib.ui.filechooser.io.localfile.LocalFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.regex.Pattern;

import uk.ac.ucl.excites.collector.project.db.DataAccess;
import uk.ac.ucl.excites.collector.project.io.ExCiteSFileLoader;
import uk.ac.ucl.excites.collector.project.model.Project;
import uk.ac.ucl.excites.collector.project.util.DuplicateException;
import uk.ac.ucl.excites.collector.project.xml.ProjectParser;
import uk.ac.ucl.excites.collector.ui.BaseActivity;
import uk.ac.ucl.excites.collector.util.SDCard;
import uk.ac.ucl.excites.collector.util.QRcode.IntentIntegrator;
import uk.ac.ucl.excites.collector.util.QRcode.IntentResult;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.util.Log;
import android.util.Patterns;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

/**
 * @author Julia, Michalis Vitos, mstevens
 * 
 */
public class ProjectPickerActivity extends BaseActivity
{

	// STATICS--------------------------------------------------------
	private static final String TAG = "ProjectPickerActivity";
	private static final String SHORTCUT_PROJECT_NAME = "Shortcut_Project_Name";
	private static final String SHORTCUT_PROJECT_VERSION = "Shortcut_Project_Version";
	private static final String XML_FILE_EXTENSION = "xml";
	private static final String EXCITES_FOLDER = "ExCiteS" + File.separatorChar;
	private static final int BROWSE_FOR_FILE = 1;
	
	// DYNAMICS-------------------------------------------------------
	
	// UI
	private EditText enterURL;
	private ListView projectList;
	
	private String dbPATH;
	private DataAccess dao;
	private List<Project> parsedProjects;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		// Remove title
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		// Hide soft keyboard on create
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		setContentView(R.layout.activity_projectpicker);

		// Database instance (path may be changed)
		// Path is on Internal Storage
		dbPATH = this.getFilesDir().getAbsolutePath();
		// Log.d("ExCiteS_Debug", "Internal Storage path: " + dbPATH);
		dao = DataAccess.getInstance(dbPATH);

		// Get extra info and check if there is a shortcut info there
		Bundle extras = getIntent().getExtras();
		if(extras != null)
		{
			// Get the shortcut name and version
			String projectName = extras.getString(SHORTCUT_PROJECT_NAME);
			int projectVersion = extras.getInt(SHORTCUT_PROJECT_VERSION);

			// Create and run a project
			Project shortcutProject = new Project(projectName, projectVersion, dbPATH);
			runProjectActivity(shortcutProject);
		}

		// Get View Elements
		enterURL = (EditText) findViewById(R.id.EnterURL);
		projectList = (ListView) findViewById(R.id.ProjectsList);

		// get scrolling right
		findViewById(R.id.scrollView).setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				projectList.getParent().requestDisallowInterceptTouchEvent(false);
				return false;
			}
		});
		projectList.setOnTouchListener(new View.OnTouchListener()
		{
			public boolean onTouch(View v, MotionEvent event)
			{
				// Disallow the touch request for parent scroll on touch of child view
				v.getParent().requestDisallowInterceptTouchEvent(true);
				return false;
			}
		});
	}

	public void browse(View view)
	{
		Intent intent = new Intent(getBaseContext(), FileChooserActivity.class);
		// Start from "/sdcard"
		intent.putExtra(FileChooserActivity._Rootpath, (Parcelable) new LocalFile(Environment.getExternalStorageDirectory().getPath()));
		// set file filter for .xml or .excites
		intent.putExtra(FileChooserActivity._RegexFilenameFilter, "^.*\\.(" + XML_FILE_EXTENSION + "|" + ExCiteSFileLoader.EXCITES_FILE_EXTENSION + ")$");
		startActivityForResult(intent, BROWSE_FOR_FILE);
	}

	public void runProject(View view)
	{
		// Check if the user has selected a project from the list
		if(projectList.getCheckedItemPosition() == -1)
		{
			errorDialog("Please select a project", false).show();
			return;
		}
		Project selectedProject = parsedProjects.get(projectList.getCheckedItemPosition());
		runProjectActivity(selectedProject);
	}

	public void runProjectActivity(Project selectedProject)
	{
		Intent i = new Intent(this, CollectorActivity.class);
		i.putExtra(CollectorActivity.PARAMETER_PROJECT_NAME, selectedProject.getName());
		i.putExtra(CollectorActivity.PARAMETER_PROJECT_VERSION, selectedProject.getVersion());
		i.putExtra(CollectorActivity.PARAMETER_DB_FOLDER_PATH, dbPATH);
		startActivity(i);
	}

	public void removeProject()
	{
		dao.deleteProject(parsedProjects.get(projectList.getCheckedItemPosition()));
		populateProjectList();
	}

	public void loadFile(View view)
	{
		// Define variables
		String path = enterURL.getText().toString();
		Project project = null;

		if(path.isEmpty())
		{
			errorDialog("Please select an XML or ExCiteS file", false).show();
			return;
		}
		// Download ExCiteS file if necessary
		else if(Pattern.matches(Patterns.WEB_URL.toString(), path) && path.toLowerCase().endsWith(ExCiteSFileLoader.EXCITES_FILE_EXTENSION))
		{
			// Check if there is an SD Card
			if(SDCard.isExternalStorageWritable())
			{
				// starting the Async Task
				new DownloadFileFromURL(path, "Project.excites").execute();
			}
			else
			{
				// Inform the user and close the application
				errorDialog("ExCiteS needs an SD card in order to function. Please insert one and restart the application.", true).show();
			}

		}
		// Parse a single XML file
		else if(path.toLowerCase().endsWith(XML_FILE_EXTENSION))
		{
			try
			{
				File xmlFile = new File(path);
				// Use the path where the xml file currently is as the basePath (img and snd folders are assumed to be in the same place):
				ProjectParser parser = new ProjectParser(xmlFile.getParentFile().getAbsolutePath());
				project = parser.parseProject(xmlFile);
			}
			catch(Exception e)
			{
				Log.e(TAG, "XML file could not be parsed", e);
				errorDialog("XML file could not be parsed: " + e.getLocalizedMessage(), false).show();
				return;
			}

			checkProject(project, path);
		}
		// Extract & parse an ExCiteS file
		else if(path.toLowerCase().endsWith(ExCiteSFileLoader.EXCITES_FILE_EXTENSION))
		{
			try
			{
				// Check if there is an SD Card
				if(SDCard.isExternalStorageWritable())
				{
					// Use /mnt/sdcard/ExCiteS/ as the basePath:
					ExCiteSFileLoader loader = new ExCiteSFileLoader(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separatorChar
							+ EXCITES_FOLDER);
					project = loader.load(new File(path));
				}
				else
				{
					// Inform the user and close the application
					errorDialog("ExCiteS needs an SD card in order to function. Please insert one and restart the application.", true).show();
				}
			}
			catch(Exception e)
			{
				Log.e(TAG, "Could not load excites file", e);
				errorDialog("Could not load excites file: " + e.getLocalizedMessage(), false).show();
				return;
			}

			checkProject(project, path);
		}
	}

	private void checkProject(Project project, String path)
	{
		// Check if we have a project object:
		if(project == null)
		{
			errorDialog("Invalid xml or excites file: " + path, false).show();
			return;
		}

		// Store the project object:
		try
		{
			dao.store(project);
		}
		catch(DuplicateException de)
		{
			errorDialog("Could not store project: " + de.getLocalizedMessage(), false).show();
			return;
		}

		// Update project list:
		populateProjectList();
	}

	public void scanQR(View view)
	{
		// Start the Intent to Scan a QR code
		IntentIntegrator integrator = new IntentIntegrator(this);
		integrator.initiateScan();
	}

	/**
	 * Create a shortcut
	 * 
	 * @param view
	 */
	public void createShortcut(View view)
	{
		// Check if the user has selected a project from the list
		if(projectList.getCheckedItemPosition() == -1)
		{
			errorDialog("Please select a project", false).show();
			return;
		}

		// Get the selected project
		Project selectedProject = parsedProjects.get(projectList.getCheckedItemPosition());

		// Set the shortcut intent
		Intent projectIntent = new Intent(getApplicationContext(), ProjectPickerActivity.class);
		projectIntent.putExtra(SHORTCUT_PROJECT_NAME, selectedProject.getName());
		projectIntent.putExtra(SHORTCUT_PROJECT_VERSION, selectedProject.getVersion());
		projectIntent.setAction(Intent.ACTION_MAIN);

		// Set up the icon
		// TODO Get an icon from the form for each project
		ShortcutIconResource iconResource = Intent.ShortcutIconResource.fromContext(ProjectPickerActivity.this, R.drawable.ic_launcher);

		// The result we are passing back from this activity
		Intent shortcutIntent = new Intent();
		shortcutIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
		shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, projectIntent);
		shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getShortcutName(selectedProject));
		shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
		// Do not allow duplicate shortcuts
		shortcutIntent.putExtra("duplicate", false);
		sendBroadcast(shortcutIntent);
	}

	/**
	 * Remove a shortcut
	 * 
	 * @param view
	 */
	public void removeShortcut(View view)
	{
		// Check if the user has selected a project from the list
		if(projectList.getCheckedItemPosition() == -1)
		{
			errorDialog("Please select a project", false).show();
			return;
		}

		// Get the selected project
		Project selectedProject = parsedProjects.get(projectList.getCheckedItemPosition());
		
		// Deleting shortcut
		Intent projectIntent = new Intent(getApplicationContext(), ProjectPickerActivity.class);
		projectIntent.setAction(Intent.ACTION_MAIN);

		Intent shortcutIntent = new Intent();
		shortcutIntent.setAction("com.android.launcher.action.UNINSTALL_SHORTCUT");
		shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, projectIntent);
		shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getShortcutName(selectedProject));
		sendBroadcast(shortcutIntent);
	}

	/**
	 * Return a name to be used for the creation / removal of shortcuts
	 * 
	 * @param project
	 * @return
	 */
	public static String getShortcutName(Project project)
	{
		return project.getName() + " v" + project.getVersion();
	}

	// retrieve all parsed projects from db and populate list
	public void populateProjectList()
	{
		parsedProjects = dao.retrieveProjects();
		String[] values = new String[parsedProjects.size()];
		for(int i = 0; i < parsedProjects.size(); i++)
		{
			values[i] = parsedProjects.get(i).getName();
		}
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, android.R.id.text1, values);
		projectList.setAdapter(adapter);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if(resultCode == Activity.RESULT_OK)
		{
			switch(requestCode)
			{
			// FileDialog
			case BROWSE_FOR_FILE:
				// Get the result file path
				// A list of files will always return, if selection mode is single, the list contains one file
				@SuppressWarnings("unchecked")
				List<LocalFile> files = (List<LocalFile>) data.getSerializableExtra(FileChooserActivity._Results);

				for(File f : files)
				{

					String fileSource = f.getAbsoluteFile().toString();
					enterURL.setText(fileSource);
					// Move the cursor to the end
					enterURL.setSelection(fileSource.length());
				}
				break;
			// QR Reader
			case IntentIntegrator.REQUEST_CODE:

				IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
				if(scanResult != null)
				{
					String fileUrl = data.getStringExtra("SCAN_RESULT");
					enterURL.setText(fileUrl);
					// Move the cursor to the end
					enterURL.setSelection(fileUrl.length());
				}
				break;
			}
		}
	}

	// dialog to check whether it is desired to remove project
	public void removeDialog(View view)
	{
		if(projectList.getCheckedItemPosition() == -1)
		{
			AlertDialog NoSelection = errorDialog("Please select a project", false);
			NoSelection.show();
		}
		else
		{
			AlertDialog removeDialogBox = new AlertDialog.Builder(this).setMessage("Are you sure that you want to remove the project?")
					.setPositiveButton("Yes", new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int whichButton)
						{
							removeProject();
						}
					}).setNegativeButton("Cancel", new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int whichButton)
						{
						}
					}).create();
			removeDialogBox.show();
		}
	}

	@Override
	protected void onPause()
	{
		// close database
		super.onPause();
		dao.closeDB();
	}

	@Override
	protected void onResume()
	{
		// open database
		super.onResume();
		dao.openDB();
		// Update project list:
		populateProjectList();
	}

	/**
	 * Background Async Task to download file
	 * 
	 * @author Michalis Vitos
	 * */
	public class DownloadFileFromURL extends AsyncTask<Void, Integer, Boolean>
	{
		// Variables
		private ProgressDialog mProgressDialog;
		private String downloadUrl;
		private File downloadFile;
		private Project project;

		public DownloadFileFromURL(String downloadUrl, String filename)
		{
			this.downloadUrl = downloadUrl;
			// Download file in folder /Download/timestamp-filename
			this.downloadFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator
					+ System.currentTimeMillis() + "-" + filename);

			// instantiate it within the onCreate method
			mProgressDialog = new ProgressDialog(ProjectPickerActivity.this);
			mProgressDialog.setMessage("Downloading...");
			mProgressDialog.setIndeterminate(false);
			mProgressDialog.setMax(100);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.setCancelable(true);

			// mProgressDialog.setButton(0, (char) "asdfsadf", "asdfsadf");

		}

		/**
		 * Show Progress Bar Dialog before starting the downloading
		 * */
		@SuppressWarnings("deprecation")
		@Override
		protected void onPreExecute()
		{
			super.onPreExecute();

			mProgressDialog.setButton("Cancel...", new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					DownloadFileFromURL.this.cancel(true);
					// Delete the downloaded file
					downloadFile.delete();
					return;
				}
			});

			mProgressDialog.show();
		}

		/**
		 * Downloading file in background thread
		 * 
		 * @return
		 * */
		@Override
		protected Boolean doInBackground(Void... voids)
		{
			if(isOnline(ProjectPickerActivity.this))
			{
				int count;
				try
				{
					URL url = new URL(downloadUrl);
					URLConnection conection = url.openConnection();
					conection.connect();
					// getting file length
					int fileLength = conection.getContentLength();

					// input stream to read file - with 8k buffer
					InputStream input = new BufferedInputStream(url.openStream(), 8192);
					// Output stream to write file
					OutputStream output = new FileOutputStream(downloadFile.toString());

					byte data[] = new byte[1024];

					long total = 0;

					while((count = input.read(data)) != -1)
					{
						total += count;
						// Publish the progress....
						publishProgress((int) (total * 100 / fileLength));

						// writing data to file
						output.write(data, 0, count);
					}

					// flushing output
					output.flush();

					// closing streams
					output.close();
					input.close();
				}
				catch(Exception e)
				{
					Log.e("Error: ", e.getMessage(), e);
				}

				return true;
			}
			return false;
		}

		/**
		 * Updating progress bar
		 * */
		protected void onProgressUpdate(Integer... progress)
		{
			mProgressDialog.setProgress(progress[0]);
		}

		/**
		 * After completing background task Dismiss the progress dialog and parse the project
		 * **/
		@Override
		protected void onPostExecute(Boolean downloadFinished)
		{
			// Dismiss the dialog after the file was downloaded
			mProgressDialog.dismiss();

			if(downloadFinished)
			{
				// Parse the project
				try
				{
					// Use /mnt/sdcard/ExCiteS/ as the basePath:
					ExCiteSFileLoader loader = new ExCiteSFileLoader(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separatorChar
							+ EXCITES_FOLDER);
					project = loader.load(downloadFile);

					// Rename the file to something more useful
					if(project != null)
					{
						downloadFile.renameTo(new File(downloadFile.getParentFile().toString() + File.separator + project.getName() + "_v"
								+ project.getVersion() + '_' + (System.currentTimeMillis() / 1000) + ".excites"));
					}

					checkProject(project, downloadFile.toString());
				}
				catch(Exception e)
				{
					Log.e(TAG, "Could not load excites file", e);
					errorDialog("Could not load excites file: " + e.getLocalizedMessage(), false).show();
					return;
				}
			}
			else
			{
				errorDialog("There is no internet connectivity.", false).show();
				// Delete the downloaded file
				downloadFile.delete();
			}
		}
	}

	/**
	 * Check if the device is connected to Internet
	 * 
	 * @param mContext
	 * @return
	 */
	public static boolean isOnline(Context mContext)
	{
		ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if(netInfo != null && netInfo.isConnected())
		{
			return true;
		}
		return false;
	}
}
