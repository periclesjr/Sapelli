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

package uk.ac.ucl.excites.sapelli.collector.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import android.util.Log;
import uk.ac.ucl.excites.sapelli.collector.R;
import uk.ac.ucl.excites.sapelli.collector.activities.BaseActivity;
import uk.ac.ucl.excites.sapelli.collector.db.ProjectStore;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.Form;
import uk.ac.ucl.excites.sapelli.collector.model.MediaFile;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.collector.model.ProjectDescriptor;
import uk.ac.ucl.excites.sapelli.collector.model.fields.MediaField;
import uk.ac.ucl.excites.sapelli.collector.transmission.SchedulingHelpers;
import uk.ac.ucl.excites.sapelli.collector.transmission.SendSchedule;
import uk.ac.ucl.excites.sapelli.collector.util.AsyncTaskWithWaitingDialog;
import uk.ac.ucl.excites.sapelli.collector.util.ProjectRunHelpers;
import uk.ac.ucl.excites.sapelli.shared.util.ExceptionHelpers;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.sources.Source;

/**
 * A collection of async tasks to deal with loading, removed projects and handling their data.
 * 
 * @author mstevens
 */
public final class ProjectTasks
{
	
	private ProjectTasks() { /* should never be instantiated */ }
	
	/**
	 * Runs asynchronous queries to find the records and media files associated with a given Project
	 * 
	 * @param owner
	 * @param project
	 * @param callback
	 */
	static public void RunProjectDataQueries(final BaseActivity owner, final Project project, final ProjectDataCallback callback)
	{
		if(project == null)
			return;
		new RecordsTasks.QueryTask(owner, new RecordsTasks.QueryCallback()
		{
			@SuppressWarnings("unchecked")
			@Override
			public void querySuccess(final List<Record> records)
			{
				// Run media scan:
				new MediaFilesQueryTask(owner, project, new MediaFilesQueryCallback()
				{
					
					@Override
					public void mediaQuerySuccess(List<MediaFile> mediaFiles)
					{
						callback.projectDataQuerySuccess(records, mediaFiles);
					}
					
					@Override
					public void mediaQueryFailure(Exception reason)
					{
						callback.projectDataQueryFailure(reason);
					}
				}).execute(records);
			}
			
			@Override
			public void queryFailure(Exception reason)
			{
				callback.projectDataQueryFailure(reason);
			}
		}).execute(new RecordsQuery(Source.From(project.getModel())));
	}
	
	static public interface ProjectDataCallback
	{
		
		public void projectDataQuerySuccess(List<Record> records, List<MediaFile> mediaFiles);
		
		public void projectDataQueryFailure(Exception reason);
		
	}
	
	/**
	 * Finds media files ("attachments") associated with a given set of records created using a given project or projects. 
	 * 
	 * @author mstevens
	 */
	static public class MediaFilesQueryTask extends AsyncTaskWithWaitingDialog<BaseActivity, List<Record>, List<MediaFile>>
	{

		private final MediaFilesQueryCallback callback;
		private final List<Project> projects;
		private Exception failure = null;
		
		/**
		 * @param owner
		 * @param project project used to create the records to find media attachments for
		 * @param callback
		 */
		public MediaFilesQueryTask(BaseActivity owner, Project project, MediaFilesQueryCallback callback)
		{
			this(owner, Collections.singletonList(project), callback);
		}
		
		/**
		 * @param owner
		 * @param projects projects used to create the records to find media attachments for
		 * @param callback
		 */
		public MediaFilesQueryTask(BaseActivity owner, List<Project> projects, MediaFilesQueryCallback callback)
		{
			super(owner, owner.getString(R.string.mediaScanning));
			this.projects = projects;
			this.callback = callback;
		}

		@Override
		@SafeVarargs
		protected final List<MediaFile> runInBackground(List<Record>... params)
		{
			List<Record> records = params[0];
			try
			{
				return getMediaFiles(projects, records, getContext().getFileStorageProvider()); 
			}
			catch(Exception e)
			{
				Log.e(getClass().getName(), ExceptionHelpers.getMessageAndCause(e), e);
				failure = e;
				return Collections.<MediaFile> emptyList();
			}
		}
		
		@Override
		protected void onPostExecute(List<MediaFile> result)
		{
			super.onPostExecute(result); // dismiss dialog
			if(failure != null)
				callback.mediaQueryFailure(failure);
			else
				callback.mediaQuerySuccess(result);
		}
		
	}
	
	public interface MediaFilesQueryCallback
	{
		
		public void mediaQuerySuccess(List<MediaFile> mediaFiles);
		
		public void mediaQueryFailure(Exception reason);
		
	}
	
	/**
	 * @param project
	 * @param record
	 * @param fileSP
	 * @return
	 * @throws Exception
	 */
	static public List<MediaFile> getMediaFiles(Project project, Record record, FileStorageProvider fileSP) throws Exception
	{
		return getMediaFiles(Collections.singletonList(project), Collections.singletonList(record), fileSP);
	}
	
	/**
	 * @param projects
	 * @param records
	 * @param fileSP
	 * @return
	 * @throws Exception
	 */
	static public List<MediaFile> getMediaFiles(List<Project> projects, List<Record> records, FileStorageProvider fileSP) throws Exception
	{
		// Populate schema->form map:
		final Map<Schema, Form> schema2Form = new HashMap<Schema, Form>();
		for(Project project : projects)
			for(Form form : project.getForms())
				schema2Form.put(form.getSchema(), form);
		
		// Group records by form:
		Map<Form, List<Record>> recordsByForm = new HashMap<Form, List<Record>>();
		for(Record r : records)
		{
			Form form = schema2Form.get(r.getSchema());
			if(form == null)
				continue;
			List<Record> formRecs;
			if(!recordsByForm.containsKey(form))
				recordsByForm.put(form, formRecs = new ArrayList<Record>());
			else
				formRecs = recordsByForm.get(form);
			formRecs.add(r);
		}
		// Scan for attachments:
		final List<MediaFile> attachments = new ArrayList<MediaFile>();
		for(Form form : recordsByForm.keySet())
			for(Record record : recordsByForm.get(form))
				for(Field field : form.getFields())
					if(field instanceof MediaField)
					{
						MediaField mf = (MediaField) field;
						for(int i = 0; i < mf.getAttachmentCount(record); i++)
						{
							MediaFile attachment = mf.getAttachment(fileSP, record, i);
							if(attachment.file.exists())
								attachments.add(attachment);
						}
					}
		return attachments;
	}
	
	static private abstract class ProjectStoreTask<I, O> extends AsyncTaskWithWaitingDialog<BaseActivity, I, O>
	{
		
		protected final ProjectStore projectStore;
		
		@SuppressWarnings("unused")
		public ProjectStoreTask(BaseActivity owner, ProjectStore projectStore)
		{
			this(owner, projectStore, null);
		}
		
		public ProjectStoreTask(BaseActivity owner, ProjectStore projectStore, String waitingMsg)
		{
			super(owner, waitingMsg);
			this.projectStore = projectStore;
		}
		
	}
	
	/**
	 * @author mstevens
	 */
	static public class ReloadProjectTask extends ProjectStoreTask<ProjectDescriptor, Project>
	{
		
		private ReloadProjectCallback callback;
		
		public ReloadProjectTask(BaseActivity owner, ProjectStore projectStore, ReloadProjectCallback callback)
		{
			super(owner, projectStore, owner.getString(R.string.projectLoading));
			this.callback = callback;
		}

		@Override
		protected Project runInBackground(ProjectDescriptor... params)
		{
			return projectStore.retrieveProject(params[0]);
		}

		@Override
		protected void onPostExecute(Project project)
		{
			super.onPostExecute(project); // dismiss dialog
			if(callback != null && project != null) // project may be null if task was cancelled
				callback.projectReloaded(project);
		}

	}
	
	public interface ReloadProjectCallback
	{
		
		public void projectReloaded(Project project);
		
	}
	
	/**
	 * @author mstevens
	 */
	static public class RemoveProjectTask extends ProjectStoreTask<ProjectDescriptor, Void>
	{
	
		private final RemoveProjectCallback callback;
		
		public RemoveProjectTask(BaseActivity owner, ProjectStore projectStore, RemoveProjectCallback callback)
		{
			super(owner, projectStore, owner.getString(R.string.projectRemoving));
			this.callback = callback;
		}

		@Override
		protected Void runInBackground(ProjectDescriptor... params)
		{
			ProjectDescriptor projDescr = params[0];
			if(projDescr != null)
			{
				// Cancel data sending alarms:
				for(SendSchedule schedule : projectStore.retrieveSendSchedulesForProject(projDescr))
					SchedulingHelpers.Cancel(getContext().getApplicationContext(), schedule);
				
				// Remove project from store:
				projectStore.delete(projDescr);
				
				BaseActivity owner = getContext();
				if(owner != null)
				{
					// Remove installation folder:
					FileUtils.deleteQuietly(owner.getFileStorageProvider().getProjectInstallationFolder(projDescr, false));
					
					// Remove shortcut:
					ProjectRunHelpers.removeShortcut(owner, projDescr);
				
					// Remove as active project
					if(owner.getPreferences().getActiveProjectSignature().equals(projDescr.getSignatureString()))
						owner.getPreferences().clearActiveProjectSignature();
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result)
		{
			super.onPostExecute(result); // dismiss dialog
			if(callback != null)
				callback.projectRemoved();
		}

	}
	
	public interface RemoveProjectCallback
	{
		
		public void projectRemoved();
		
	}
	
}
