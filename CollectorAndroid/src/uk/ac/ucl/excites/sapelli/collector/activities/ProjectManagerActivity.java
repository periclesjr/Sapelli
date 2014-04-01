package uk.ac.ucl.excites.sapelli.collector.activities;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import uk.ac.ucl.excites.sapelli.collector.BuildInfo;
import uk.ac.ucl.excites.sapelli.collector.CollectorApp;
import uk.ac.ucl.excites.sapelli.collector.R;
import uk.ac.ucl.excites.sapelli.collector.SapelliCollectorClient;
import uk.ac.ucl.excites.sapelli.collector.db.ProjectStore;
import uk.ac.ucl.excites.sapelli.collector.io.ProjectLoader;
import uk.ac.ucl.excites.sapelli.collector.io.ProjectLoaderClient;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.collector.util.DeviceID;
import uk.ac.ucl.excites.sapelli.collector.util.DuplicateException;
import uk.ac.ucl.excites.sapelli.collector.util.qrcode.IntentIntegrator;
import uk.ac.ucl.excites.sapelli.collector.util.qrcode.IntentResult;
import uk.ac.ucl.excites.sapelli.collector.xml.ProjectParser;
import uk.ac.ucl.excites.sapelli.shared.db.StoreClient;
import uk.ac.ucl.excites.sapelli.shared.util.StringUtils;
import uk.ac.ucl.excites.sapelli.shared.util.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.storage.eximport.ExImportHelper;
import uk.ac.ucl.excites.sapelli.storage.eximport.ExImportHelper.Format;
import uk.ac.ucl.excites.sapelli.storage.eximport.ExportResult;
import uk.ac.ucl.excites.sapelli.storage.eximport.xml.XMLRecordsImporter;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.transmission.Settings;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.ipaulpro.afilechooser.utils.FileUtils;

/**
 * @author Julia, Michalis Vitos, mstevens
 * 
 */
public class ProjectManagerActivity extends BaseActivity implements ProjectLoaderClient, StoreClient, DeviceID.InitialisationCallback, MenuItem.OnMenuItemClickListener
{

	// STATICS--------------------------------------------------------
	static private final String TAG = "ProjectManagerActivity";
	
	static private final String XML_FILE_EXTENSION = "xml";

	static private final String DEMO_PROJECT = "demo.excites";
	
	// SHORTCUT INTENT ACTIONS
	private static final String DEFAULT_INSTALL_SHORTCUT_ACTION = "com.android.launcher.action.INSTALL_SHORTCUT";
	private static final String DEFAULT_UNINSTALL_SHORTCUT_ACTION = "com.android.launcher.action.UNINSTALL_SHORTCUT";
	private static final String SAPELLI_LAUNCHER_INSTALL_SHORTCUT_ACTION = "uk.ac.ucl.excites.sapelli.launcher.INSTALL_SHORTCUT";
	private static final String SAPELLI_LAUNCHER_UNINSTALL_SHORTCUT_ACTION = "uk.ac.ucl.excites.sapelli.launcher.UNINSTALL_SHORTCUT";

	// SHORTCUT INTENT PARAMETER FOR SAPELLI LAUNCHER
	private static final String SAPELLI_LAUNCHER_SHORTCUT_ICON_PATH = "uk.ac.ucl.excites.sapelli.launcher.shortcut.ICON_PATH";

	public static final int RETURN_BROWSE_FOR_PROJECT_LOAD = 1;
	public static final int RETURN_BROWSE_FOR_RECORD_IMPORT = 2;

	// DYNAMICS-------------------------------------------------------
	private ProjectStore projectStore;

	// UI
	private EditText enterURL;
	private ListView projectList;
	private Button runBtn;
	private Button removeBtn;
	private MenuItem senderSettingsItem;
	private MenuItem exportRecordsItem;
	private MenuItem importRecordsItem;
	private MenuItem copyDBItem;
	private MenuItem createShortcutItem;
	private MenuItem removeShortcutItem;
	private Dialog encryptionDialog;
	private TextView infoLbl;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		app = (CollectorApp) getApplication();

		// Check if we can access read/write to the Sapelli folder (created on the SD card or internal mass storage if there is no physical SD card):
		try
		{
			app.getSapelliFolder(); //throws IllegalStateException if not accessible or not create-able
		}
		catch(IllegalStateException ise)
		{	// Inform the user and close the application
			showErrorDialog(getString(R.string.app_name) + " needs write access to the external/mass storage in order to function. Please insert an SD card and restart the application.", true);
			return;
		}

		// Get ProjectStore instance:
		try
		{
			projectStore = app.getProjectStore(this);
		}
		catch(Exception e)
		{
			showErrorDialog("Could not open ProjectStore: " + e.getLocalizedMessage(), true);
			return;
		}
		
		if(BuildInfo.DEMO_BUILD)
			return;
		//else ...
		// Only if not in demo mode:			
		
		// Set-up UI...
		setTitle(getString(R.string.app_name) + ' ' + getString(R.string.project_manager));
		// Hide soft keyboard on create
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		setContentView(R.layout.activity_projectmanager);
		// Get View Elements
		enterURL = (EditText) findViewById(R.id.EnterURL);
		projectList = (ListView) findViewById(R.id.ProjectsList);
		runBtn = (Button) findViewById(R.id.RunProjectButton);
		removeBtn = (Button) findViewById(R.id.RemoveProjectButton);
		infoLbl = (TextView) findViewById(R.id.info);

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

/*		// Check the Preferences
		if(DataSenderPreferences.getTimeSchedule(this) == 1)
		{
			DataSenderPreferences.printPreferences(this);
			Toast.makeText(this, "Please configure the Data Sender.", Toast.LENGTH_LONG).show();

			Intent settingsActivity = new Intent(this, DataSenderPreferences.class);
			startActivity(settingsActivity);
		}
*/

		// TODO Re-enable the service at same point
		// Start the DataSenderService
		// if(DataSenderPreferences.getSenderEnabled(this)) //TODO make this optional
		// {
		// ServiceChecker.startService(this);
		// }
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		
		// Initialise DeviceID:
		DeviceID.Initialise(this, this); // will post a callback upon completion (success/failure)

		// Check database connection:
		if(projectStore == null)
		{
			showErrorDialog("Could not establish database connection", true);
			return;
		}
		
		// And finally...
		if(BuildInfo.DEMO_BUILD)
			demoMode();
		else
			populateProjectList(); 	// Update project list
	}
	
	@Override
	public void initialisationSuccess(DeviceID deviceID)
	{
		infoLbl.setText(getString(R.string.app_name) + " " + BuildInfo.getVersionInfo() + ".\n" +
						BuildInfo.getBuildInfo() + ".\n\n" +
						getString(R.string.by_ucl_excites) + "\n\n" +
						"Device ID (CRC32): " + deviceID.getIDAsCRC32Hash() + '.');
	}

	@Override
	public void initialisationFailure(DeviceID deviceID)
	{
		deviceID.printInfo();
		showErrorDialog("Sapelli was unable to generate a unique identifier for your device.", true);
	}

	private void demoMode()
	{
		try
		{
			List<Project> projects = projectStore.retrieveProjects();
			Project p = null;
			if(projects.isEmpty())
			{	// Use /mnt/sdcard/Sapelli/ as the basePath:
				ProjectLoader loader = new ProjectLoader(this, app.getProjectFolderPath(), app.getTempFolderPath());
				p = loader.load(this.getAssets().open(DEMO_PROJECT, AssetManager.ACCESS_RANDOM));
				storeProject(p);
			}
			else
				p = projects.get(0); // Assumption: there is only one stored project in demo mode
			// Run the project
			startActivity(getProjectRunIntent(p));
		}
		catch(Exception e)
		{
			Log.e(TAG, "Error loading/storing/launching demo project", e);
			showErrorDialog("Could not load demo project.", true);
		}
	}

	@Override
	protected void onDestroy()
	{
		// clean up:
		app.discardStoreUsage(projectStore, this); // signal that the activity no longer needs the DAO
		// super:
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.projectpicker, menu);

		// Set click listeners (the android:onClick attribute in the XML only works on Android >= v3.0)
		senderSettingsItem = menu.findItem(R.id.sender_settings_menuitem);
		if(senderSettingsItem != null)
			senderSettingsItem.setOnMenuItemClickListener(this);
		exportRecordsItem = menu.findItem(R.id.export_records_menuitem);
		if(exportRecordsItem != null)
			exportRecordsItem.setOnMenuItemClickListener(this);
		importRecordsItem = menu.findItem(R.id.import_records_menuitem);
		if(importRecordsItem != null)
			importRecordsItem.setOnMenuItemClickListener(this);
		copyDBItem = menu.findItem(R.id.copy_db_menuitem);
		if(copyDBItem != null)
			copyDBItem.setOnMenuItemClickListener(this);
		createShortcutItem = menu.findItem(R.id.create_shortcut);
		if(createShortcutItem != null)
			createShortcutItem.setOnMenuItemClickListener(this);
		removeShortcutItem = menu.findItem(R.id.remove_shortcut);
		if(removeShortcutItem != null)
			removeShortcutItem.setOnMenuItemClickListener(this);
		return true;
	}

	@Override
	public boolean onMenuItemClick(MenuItem item)
	{
		/*if(item == senderSettingsItem)
			return openSenderSettings(item);
		else*/ if(item == exportRecordsItem)
			return exportRecords(item);
		else if(item == importRecordsItem)
			return importRecords(item);
		else if(item == copyDBItem)
			return copyDBtoSD(item);
		else if(item == createShortcutItem)
		{
			createShortcut();
			return true;
		}
		else if(item == removeShortcutItem)
		{
			removeShortcut();
			return true;
		}
		return false;
	}

/*	public boolean openSenderSettings(MenuItem item)
	{
		startActivity(new Intent(getBaseContext(), DataSenderPreferences.class));
		return true;
	}
*/

	public boolean exportRecords(MenuItem item)
	{
		final Project selectedProject = getSelectedProject(false);
		
		/* TODO show dialog with following options:
		 * 
		 * Source selection (spinner):
		 * 	- all records of forms of currently selected project [default, but only shown if selectedProject != null]
		 *  - all records of any forms/schema [default if selectedProject == null]
		 * 
		 * Date range:
		 *  - [optional after TIME_X --> checkbox + date/time picket
		 *  - [optional] before TIME_Y --> checkbox + date/time picket
		 * 
		 * Output format (spinner)
		 *  - XML [default]
		 *  - CSV
		 *  
		 *  If XML selected above, show spinner for composite mode selection:
		 *   - As String
		 *   - As flat tags [default]
		 *   - As nested tags
		 * 
		 * OK + CANCEL buttons
		 */
		
		final ExImportHelper.Format exportFormat = Format.XML; //TODO determine from dialog input
		
		/* Query for records based on dialog input */
		final List<Record> exportSelection = null; //TODO
		
		/* Generate name for selection, based on dialog input */
		final String exportSelectionDescription = "selection"; //TODO
		
		/* Export (asynchronous) */
		new Thread(new Runnable()
		{
			public void run()
			{
				ExportResult result = null;
				try
				{
					result = ExImportHelper.exportRecords(new File(app.getDumpFolderPath()), exportSelection, exportSelectionDescription, exportFormat);
				}
				catch(final Exception e)
				{
					ProjectManagerActivity.this.runOnUiThread(new Runnable()
					{
						public void run()
						{
							ProjectManagerActivity.this.showErrorDialog("Error (" + e.toString() + " during record export: " + e.getMessage(), false);
						}
					});
					return;
				}
				if(result != null)
				{
					final ExportResult res = result; 
					ProjectManagerActivity.this.runOnUiThread(new Runnable()
					{
						public void run()
						{
							ProjectManagerActivity.this.showInfoDialog("Data export", "Successfully exported " + res.getNumberedOfExportedRecords() + " records to: " + res.getDestination() + "."); //TODO multilang
						}
					});
				}
			}
		});
		
		return true;
	}

	public boolean importRecords(MenuItem item)
	{
		// Use the GET_CONTENT intent from the utility class
		Intent target = FileUtils.createGetContentIntent();
		// Create the chooser Intent
		Intent intent = Intent.createChooser(target, "Choose an XML file");
		try
		{
			startActivityForResult(intent, RETURN_BROWSE_FOR_RECORD_IMPORT);
		}
		catch(ActivityNotFoundException e){}

		return true;
	}

	public boolean copyDBtoSD(MenuItem item)
	{
		try
		{
			app.backupStores();
		}
		catch(Exception e)
		{
			showErrorDialog("Failed to backup database(s): " + e.getLocalizedMessage(), false);
		}
		return true;
	}
	
	public void browse(View view)
	{
		// Use the GET_CONTENT intent from the utility class
		Intent target = FileUtils.createGetContentIntent();
		// Create the chooser Intent
		Intent intent = Intent.createChooser(target, "Choose a sapelli file");
		try
		{
			startActivityForResult(intent, RETURN_BROWSE_FOR_PROJECT_LOAD);
		}
		catch(ActivityNotFoundException e){}
	}

	/**
	 * Retrieve all parsed projects from db and populate list
	 */
	public void populateProjectList()
	{
		projectList.setAdapter(new ArrayAdapter<Project>(this, R.layout.project_list_item, android.R.id.text1, new ArrayList<Project>(projectStore.retrieveProjects())));
		if(!projectList.getAdapter().isEmpty())
		{
			runBtn.setEnabled(true);
			removeBtn.setEnabled(true);
			projectList.setItemChecked(0, true); // check first project in the list
		}
		else
		{
			runBtn.setEnabled(false);
			removeBtn.setEnabled(false);
		}
	}

	@SuppressWarnings("unchecked")
	protected void selectProjectInList(Project project)
	{
		projectList.setItemChecked(((ArrayAdapter<Project>) projectList.getAdapter()).getPosition(project), true);
	}

	@SuppressWarnings("unchecked")
	protected Project getSelectedProject(boolean errorIfNull)
	{
		if(projectList.getCheckedItemPosition() == -1)
		{
			if(errorIfNull)
				showErrorDialog("Please select a project", false); // TODO multilang
			return null;
		}
		return ((ArrayAdapter<Project>) projectList.getAdapter()).getItem(projectList.getCheckedItemPosition());
	}

	public void runProject(View view)
	{
		Project p = getSelectedProject(true);
		if(p != null)
			startActivity(getProjectRunIntent(p));
	}

	private void removeProject()
	{
		Project p = getSelectedProject(false);
		if(p == null)
			return;
		removeShortcut(p);
		projectStore.delete(p);
		populateProjectList();

		// TODO Re-enable the service at same point
		// Restart the DataSenderService to stop monitoring the deleted project
		// ServiceChecker.restartActiveDataSender(this);
	}

	public void loadFile(View view)
	{
		// Define variables
		String path = enterURL.getText().toString().trim();
		if(path.isEmpty())
		{
			showErrorDialog("Please select an XML or Sapelli file", false);
			return;
		}
		enterURL.setText(""); // clear field
		
		Project project = null;
		try
		{
			// Download Sapelli file if path is a URL
			if(Pattern.matches(Patterns.WEB_URL.toString(), path)) // We don't check the extension to support "smart"/dynamic URLs
			{	// Start async task to download the file:
				(new DownloadFileFromURL(path, "Project")).execute(); // the task will also call processSapelliFile() and checkProject()
				return;
			}
			else if(path.toLowerCase().endsWith(XML_FILE_EXTENSION))
				project = parseXML(new File(path));
			else
			{
				// Extract & parse a local Sapelli file
				for(String extention : ProjectLoader.SAPELLI_FILE_EXTENSIONS)
				{
					if(path.toLowerCase().endsWith(extention))
					{
						project = processSapelliFile(new File(path));
						break;
					}
				}
			}
		}
		catch(Exception e)
		{
			showErrorDialog("Invalid XML or Sapelli file: " + path + "\nError: " + e.getMessage() + (e.getCause() != null ? "\nCause: " + e.getCause().getMessage() : ""), false);
			return;
		}
		
		//Add project
		if(project != null) // check to be sure
			addProject(project);
		else
			showErrorDialog("Please choose a valid XML or Sapelli file", false);
	}

	private Project parseXML(File xmlFile) throws Exception
	{
		try
		{
			// Use the path where the xml file resides as the basePath (img&snd folders are assumed to be in the same place), no subfolders are created:
			ProjectParser parser = new ProjectParser(xmlFile.getParentFile().getAbsolutePath(), false);
			Project parsedProject = parser.parseProject(xmlFile);
			// Show parser warnings if needed:
			showParserWarnings(parser.getWarnings());
			return parsedProject;
		}
		catch(Exception e)
		{
			Log.e(TAG, "XML file could not be parsed", e);
			throw e;
		}
	}

	private Project processSapelliFile(File sapelliFile) throws Exception
	{
		try
		{
			ProjectLoader loader = new ProjectLoader(this, app.getProjectFolderPath(), app.getTempFolderPath());
			Project loadedProject = loader.load(sapelliFile);
			// Show parser warnings if needed:
			showParserWarnings(loader.getParserWarnings());
			return loadedProject;
		}
		catch(Exception e)
		{
			Log.e(TAG, "Could not load Sapelli file", e);
			throw e;
		}
	}

	private void showParserWarnings(List<String> warnings)
	{
		if(!warnings.isEmpty())
		{ // Show parser warnings:
			String msg = "Parsing issues:\n";
			for(String warning : warnings)
				msg += warning + "\n";
			showWarningDialog(msg);
		}
	}

	private void addProject(final Project project)
	{
		// Check file dependencies
		List<String> invalidFiles = project.checkForInvalidFiles();
		if(!invalidFiles.isEmpty())
			showWarningDialog("The following files could not be found or read in the project path (" + project.getProjectFolderPath() + "): " + StringUtils.join(invalidFiles, ", "));
		
		// Generate documentation
		try
		{
			project.generateDocumentation();
		}
		catch(IOException e)
		{
			showErrorDialog("Could not generate documentation: " + e.getLocalizedMessage(), false);
		}
		
		// Encryption Check
		if(project.getTransmissionSettings().isEncrypt())
			requestEncryptionKey(project);

		// Store the project object:
		storeProject(project);

		// Update project list:
		populateProjectList();
		selectProjectInList(project); // select the new project

		// TODO Re-enable the service at same point
		// Restart the DataSenderService to start monitoring the new project
		// ServiceChecker.restartActiveDataSender(this);
	}

	private void storeProject(Project p)
	{
		try
		{
			projectStore.store(p);
		}
		catch(DuplicateException de)
		{
			showErrorDialog(de.getLocalizedMessage(), false);
			return;
		}
		catch(Exception e) // any other exception
		{
			Log.e(TAG, "Could not store project.", e);
			showErrorDialog("Could not store project: " + e.getLocalizedMessage(), false);
			return;
		}
	}
	
	@Override
	public boolean isDuplicateProject(Project loadProject)
	{
		// TODO Auto-generated method stub
		return false;
	}
	
	private void requestEncryptionKey(final Project project)
	{
		// encryptionDialog = new AlertDialog.Builder(this);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Project Encryption");
		builder.setMessage("This project requires a password in order to encrypt and transmit the data. Please provide a password:");
		
		// Set an EditText view to get user input
		final EditText input = new EditText(this);
		builder.setView(input);

		builder.setPositiveButton("Ok", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				String inputStr = input.getText().toString();
				project.getTransmissionSettings().setPassword(inputStr.isEmpty() ? 	Settings.DEFAULT_PASSWORD /*Set the Default Password*/ :
																					inputStr);
			}
		});
		encryptionDialog = builder.create();
		encryptionDialog.show();
	}

	public void scanQR(View view)
	{
		// Start the Intent to Scan a QR code
		IntentIntegrator integrator = new IntentIntegrator(this);
		integrator.initiateScan(IntentIntegrator.QR_CODE_TYPES);
	}

	private Intent getProjectRunIntent(Project project)
	{
		Intent i = new Intent(getApplicationContext(), CollectorActivity.class);
		i.putExtra(CollectorActivity.INTENT_PARAM_PROJECT_HASH, project.getHash());
		i.setAction(Intent.ACTION_MAIN);
		return i;
	}
	
	/**
	 * Remove a shortcut
	 * 
	 */
	public void createShortcut()
	{
		// Get the selected project
		Project selectedProject = getSelectedProject(true);
		if(selectedProject != null)
			createShortcut(selectedProject);
	}
	
	/**
	 * Create a shortcut
	 * 
	 * @param project
	 */
	public void createShortcut(Project project)
	{
		// Icon image file:
		File shortcutImageFile = project.getImageFile(project.getStartForm().getShortcutImageRelativePath()); // use icon of the startForm
		
		//-----------------------------------------------------
		// Create a shortcut in standard Android Home Launcher
		//-----------------------------------------------------
		Intent androidLauncherIntent = getShortcutCreationIntent(project, false);
		// Get up icon bitmap:
		Drawable iconResource = FileHelpers.isReadableFile(shortcutImageFile) ?	Drawable.createFromPath(shortcutImageFile.getAbsolutePath()) : getResources().getDrawable(R.drawable.ic_excites_grey);
		Bitmap icon = ((BitmapDrawable) iconResource).getBitmap();
		// Resize the icon bitmap according to the default size:
		int maxIconSize = (int) getResources().getDimension(android.R.dimen.app_icon_size); // Get standard system icon size
		if(icon.getWidth() > maxIconSize || icon.getHeight() > maxIconSize)
			icon = Bitmap.createScaledBitmap(icon, maxIconSize, maxIconSize, true); //TODO make this keep aspect ratio?
		// Set up shortcut icon:
		androidLauncherIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
		// Fire the intent:
		sendBroadcast(androidLauncherIntent);
		//-----------------------------------------------------
		
		//-----------------------------------------------------
		// Create an shortcut in the Sapelli Launcher
		//-----------------------------------------------------
		Intent sapelliLauncherIntent = getShortcutCreationIntent(project, true);
		// Set up shortcut icon path:
		sapelliLauncherIntent.putExtra(SAPELLI_LAUNCHER_SHORTCUT_ICON_PATH, FileHelpers.isReadableFile(shortcutImageFile) ? shortcutImageFile.getAbsolutePath() : null); // launcher will use default Sapelli icon when path is null
		// Fire the intent:
		sendBroadcast(sapelliLauncherIntent);		
		//-----------------------------------------------------
	}
	
	private Intent getShortcutCreationIntent(Project projectToRun, boolean sapelliLauncher)
	{
		Intent shortcutCreationIntent = new Intent();
		
		// Action:
		shortcutCreationIntent.setAction(sapelliLauncher ? SAPELLI_LAUNCHER_INSTALL_SHORTCUT_ACTION : DEFAULT_INSTALL_SHORTCUT_ACTION);
		// Shortcut intent:
		shortcutCreationIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, getProjectRunIntent(projectToRun));
		// Shortcut name:
		shortcutCreationIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, projectToRun.toString());
		// 	Do not allow duplicate shortcuts:
		if(!sapelliLauncher)
			shortcutCreationIntent.putExtra("duplicate", false); // only needed for Android Home Launcher (although Sapelli Launcher would just ignore it)
		
		return shortcutCreationIntent;
	}

	/**
	 * Remove a shortcut
	 * 
	 */
	public void removeShortcut()
	{
		// Get the selected project
		Project selectedProject = getSelectedProject(true);
		if(selectedProject != null)
			removeShortcut(selectedProject);
	}
	
	/**
	 * Remove a shortcut
	 * 
	 * @parem project
	 */
	public void removeShortcut(Project project)
	{
		// Remove a shortcut from the standard Android Home Launcher
		sendBroadcast(getShortcutRemovalIntent(project, false));
		// Remove a shortcut from the Sapelli Launcher
		sendBroadcast(getShortcutRemovalIntent(project, true));
	}

	private Intent getShortcutRemovalIntent(Project project, boolean sapelliLauncher)
	{		
		Intent shortcutRemovalIntent = new Intent();
		
		// Action:
		shortcutRemovalIntent.setAction(sapelliLauncher ? SAPELLI_LAUNCHER_UNINSTALL_SHORTCUT_ACTION : DEFAULT_UNINSTALL_SHORTCUT_ACTION);
		// Shortcut intent:
		shortcutRemovalIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, getProjectRunIntent(project));
		// Shortcut name:
		shortcutRemovalIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, project.toString());
		
		return shortcutRemovalIntent;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		Uri uri = null;
		String path = null;

		if(resultCode == Activity.RESULT_OK)
			switch(requestCode)
			{
			// File browse dialog for project loading:
			case RETURN_BROWSE_FOR_PROJECT_LOAD:

				uri = data.getData();

				// Get the File path from the Uri
				path = FileUtils.getPath(this, uri);

				// Alternatively, use FileUtils.getFile(Context, Uri)
				if(path != null && FileUtils.isLocal(path))
				{
					enterURL.setText(path);
					// Move the cursor to the end
					enterURL.setSelection(path.length());
				}

				break;

			// File browse dialog for record importing:
			case RETURN_BROWSE_FOR_RECORD_IMPORT:

				uri = data.getData();

				// Get the File path from the Uri
				path = FileUtils.getPath(this, uri);

				// Alternatively, use FileUtils.getFile(Context, Uri)
				if(path != null && FileUtils.isLocal(path))
				{
					try
					{ // TODO make import & storage async
						// Import:
						XMLRecordsImporter importer = new XMLRecordsImporter(new SapelliCollectorClient(projectStore));
						List<Record> records = importer.importFrom((new File(path)).getAbsoluteFile());

						// Show parser warnings if needed:
						showParserWarnings(importer.getWarnings());

						/*//TEST CODE (export again to compare with imported file):
						RecordsExporter exporter = new RecordsExporter(((CollectorApp) getApplication()).getDumpFolderPath(), dao);
						exporter.export(records);*/
	
						//Store the records:
						//for(Record r : records)
						//	dao.store(r); //TODO avoid duplicates!
						
						//User feedback:
						showInfoDialog("Succesfully imported " + records.size() + " records."); //TODO report skipped duplicates
					}
					catch(Exception e)
					{
						showErrorDialog("Error upon importing records: " + e.getMessage(), false);
					}
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

	/**
	 * Dialog to check whether it is desired to remove project
	 * 
	 * @param view
	 */
	public void removeDialog(View view)
	{
		if(projectList.getCheckedItemPosition() == -1)
			showErrorDialog("Please select a project", false);
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
		super.onPause();
		if(encryptionDialog != null)
			encryptionDialog.dismiss();
	}

	/**
	 * Background Async Task to download file
	 * 
	 * @author Michalis Vitos, mstevens
	 */
	public class DownloadFileFromURL extends AsyncTask<Void, Integer, Boolean>
	{

		static private final String TEMP_FILE_EXTENSION = "tmp";

		// Variables
		private final long startTime;
		private final ProgressDialog progressDialog;
		private final String downloadUrl;
		private final File downloadFolder;
		private final File downloadFile;

		/**
		 * Downloads the file
		 * 
		 * Note: We do not use Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); as the download folder because it does not seem
		 * to be writable on the Xcover.
		 * 
		 * @param downloadUrl
		 * @param filename
		 */
		public DownloadFileFromURL(String downloadUrl, String filename)
		{
			startTime = System.currentTimeMillis();
			this.downloadUrl = downloadUrl;
			// Download file in folder /Downloads/timestamp-filename
			downloadFolder = new File(app.getDownloadFolderPath());
			FileHelpers.createFolder(downloadFolder);
			downloadFile = new File(downloadFolder.getAbsolutePath() + File.separator + (startTime / 1000) + '.' + TEMP_FILE_EXTENSION);

			// instantiate it within the onCreate method
			progressDialog = new ProgressDialog(ProjectManagerActivity.this);
			progressDialog.setMessage("Downloading...");
			progressDialog.setIndeterminate(false);
			progressDialog.setMax(100);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setCancelable(true);
		}

		/**
		 * Show Progress Bar Dialog before starting the downloading
		 * */
		@Override
		protected void onPreExecute()
		{
			super.onPreExecute();

			progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Cancel...", new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					DownloadFileFromURL.this.cancel(true);
					// Delete the downloaded file
					downloadFile.delete();
				}
			});

			progressDialog.show();
		}

		/**
		 * Downloading file in background thread
		 * 
		 * @return
		 * */
		@Override
		protected Boolean doInBackground(Void... voids)
		{
			if(isOnline(ProjectManagerActivity.this))
			{
				int count;
				try
				{
					URL url = new URL(downloadUrl);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setRequestMethod("GET");
					connection.connect();
					// getting file length
					int fileLength = connection.getContentLength();

					// input stream to read file - with 8k buffer
					InputStream input = new BufferedInputStream(url.openStream(), 8192);
					// Output stream to write file
					OutputStream output = new FileOutputStream(downloadFile);

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
					Log.e("Download error: ", e.getMessage(), e);
					return false;
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
			progressDialog.setProgress(progress[0]);
		}

		/**
		 * After completing background task Dismiss the progress dialog and parse the project
		 * **/
		@Override
		protected void onPostExecute(Boolean downloadFinished)
		{
			// Dismiss the dialog after the file was downloaded
			progressDialog.dismiss();
			if(downloadFinished)
			{
				Project project = null;
				
				// Process the file & add the project to the db & list on the screen
				try
				{
					project = processSapelliFile(downloadFile);
					addProject(project); // will show error if project is null
				}
				catch(Exception e)
				{
					showErrorDialog("Invalid Sapelli file: " + downloadUrl + "\nError: " + e.getMessage()
							+ (e.getCause() != null ? "\nCause: " + e.getCause().getMessage() : ""), false);
					return;
				}
				
				// Handle temp file:
				if(project != null)
					downloadFile.renameTo(new File(downloadFolder.getAbsolutePath() + File.separator + project.getName() + "_v" + project.getVersion() + '_'
							+ (startTime / 1000) + ".sapelli"));
				else
					downloadFile.delete();
			}
			else
			{
				showErrorDialog("Download error. Please check if you are connected to the Internet.", false);
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
			return true;
		return false;
	}

}