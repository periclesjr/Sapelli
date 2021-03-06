/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2016 University College London - ExCiteS group
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

package uk.ac.ucl.excites.sapelli.collector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.db.CollectorSQLRecordStoreUpgrader;
import uk.ac.ucl.excites.sapelli.collector.db.ProjectRecordStore;
import uk.ac.ucl.excites.sapelli.collector.db.ProjectStore;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.model.CollectorAttachment;
import uk.ac.ucl.excites.sapelli.collector.model.Form;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.collector.model.ProjectDescriptor;
import uk.ac.ucl.excites.sapelli.collector.transmission.SendSchedule;
import uk.ac.ucl.excites.sapelli.collector.util.CollectorAttachmentUtils;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreCreator;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreOperation;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreOperationWithReturn;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreOperationWithReturnNoException;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreSetter;
import uk.ac.ucl.excites.sapelli.shared.db.exceptions.DBException;
import uk.ac.ucl.excites.sapelli.shared.io.StreamHelpers;
import uk.ac.ucl.excites.sapelli.storage.db.sql.upgrades.Beta17UpgradeStep;
import uk.ac.ucl.excites.sapelli.storage.model.Model;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.util.UnknownModelException;
import uk.ac.ucl.excites.sapelli.transmission.TransmissionClient;
import uk.ac.ucl.excites.sapelli.transmission.model.Correspondent;
import uk.ac.ucl.excites.sapelli.transmission.model.Payload;

/**
 * @author mstevens
 *
 */
public abstract class CollectorClient extends TransmissionClient
{
	
	// STATICS-------------------------------------------------------
	/**
	 * Version used in all Sapelli Collector v2.0 pre-releases up to and including Beta 16:
	 */
	static public final int COLLECTOR_RECORDSTORE_V2 = 2;
	
	/**
	 * Version used from Sapelli Collector v2.0 Beta 17.
	 * 
	 * @see Beta17UpgradeStep
	 * @see CollectorSQLRecordStoreUpgrader
	 */
	static public final int COLLECTOR_RECORDSTORE_V3 = 3;
	
	static public final int CURRENT_COLLECTOR_RECORDSTORE_VERSION = COLLECTOR_RECORDSTORE_V3;
	
	/**
	 * Flag indicating that a Schema has been defined at the Collector layer of the Sapelli Library.
	 * 
	 * Note: flag bits 11, 12 & 13 are reserved for future Collector layer usage
	 */
	static private final int SCHEMA_FLAG_COLLECTOR_LAYER =		1 << 10;
	
	/**
	 * Flags used on "internal" Collector layer Schemata.
	 */
	static public final int SCHEMA_FLAGS_COLLECTOR_INTERNAL = 	SCHEMA_FLAG_COLLECTOR_LAYER;
	
	/**
	 * Flags used on Schemata for all Collector data records.
	 */
	static public final int SCHEMA_FLAGS_COLLECTOR_DATA = 		SCHEMA_FLAG_COLLECTOR_LAYER | SCHEMA_FLAG_EXPORTABLE | SCHEMA_FLAG_TRANSMITTABLE;
	
	/**
	 * Flags used on Schemata for automatically-generated ("auxiliary") Collector data records.
	 */
	static public final int SCHEMA_FLAGS_COLLECTOR_AUX_DATA = 	SCHEMA_FLAGS_COLLECTOR_DATA;
	
	/**
	 * Flags used on Schemata for user-generated Collector data records.
	 */
	static public final int SCHEMA_FLAGS_COLLECTOR_USER_DATA = 	SCHEMA_FLAGS_COLLECTOR_DATA | SCHEMA_FLAG_KEEP_HISTORY;
	
	/**
	 * ID for the reserved Collector Management Model ({@link ProjectRecordStore#COLLECTOR_MANAGEMENT_MODEL})
	 */
	static public final long COLLECTOR_MANAGEMENT_MODEL_ID = TRANSMISSION_MANAGEMENT_MODEL_ID + 1; // = 1
	
	// Add tableName prefixes & reserved model (in that order!):
	static
	{
		AddTableNamePrefix(SCHEMA_FLAG_COLLECTOR_LAYER, "Collector_");
		AddTableNamePrefix(SCHEMA_FLAGS_COLLECTOR_DATA, "Data_");
		AddReservedModel(ProjectRecordStore.COLLECTOR_MANAGEMENT_MODEL);
	}
		
	/**
	 * Returns the modelID to use for the {@link Model} of the given {@link Project} or {@link ProjectDescriptor}.  
	 * 
	 * @param projDescr
	 * @return unsigned 56 bit integer
	 * @throws IllegalArgumentException in case of a clash with a reserved Model
	 */
	static public long GetModelID(ProjectDescriptor projDescr) throws IllegalArgumentException
	{
		long modelID =	((((long) projDescr.getFingerPrint()) & 0xffffffffl) << Project.PROJECT_ID_SIZE) +	// Project finger print takes up first 32 bits
						projDescr.getID();	
		if(GetReservedModel(modelID) != null)
			throw new IllegalArgumentException("Model ID computed for Project \"" + projDescr.toString(false) + "\" clashes with reserved model ID (" + modelID + ")!");
		return modelID;
	}
	
	/**
	 * @param modelID
	 * @return project ID (24 bit unsigned int)
	 */
	static public int GetProjectID(long modelID)
	{
		return (int) (modelID % (1 << Project.PROJECT_ID_SIZE));
	}
	
	/**
	 * @param modelID
	 * @return project fingerprint (32 bit signed int)
	 */
	static public int GetProjectFingerPrint(long modelID)
	{
		return (int) (modelID >> Project.PROJECT_ID_SIZE);
	}
	
	static protected final byte MODEL_SERIALISATION_KIND_COMPRESSED_COLLECTOR_PROJECT_XML = MODEL_SERIALISATION_KIND_RESERVED + 1;
	
	// DYNAMICS------------------------------------------------------
	public final StoreHandle<ProjectStore> projectStoreHandle = new StoreHandle<ProjectStore>(this, new StoreCreator<ProjectStore>()
	{
		@Override
		public void createAndSetStore(StoreSetter<ProjectStore> setter) throws DBException
		{
			createAndSetProjectStore(setter);
		}
	});
	
	/**
	 * Creates a new ProjectStore instance
	 * 
	 * @param setter
	 * @throws DBException
	 */
	protected abstract void createAndSetProjectStore(StoreSetter<ProjectStore> setter) throws DBException;
	
	/**
	 * @return a {@link FileStorageProvider} instance, or {@code null} if there is none
	 */
	public abstract FileStorageProvider getFileStorageProvider();
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.StorageClient#serialiseClientModel(uk.ac.ucl.excites.sapelli.storage.model.Model, java.io.OutputStream)
	 */
	@Override
	protected void serialiseClientModel(Model model, final OutputStream out) throws IOException, UnknownModelException
	{
		final Project project = getProject(model.id);
		if(project != null)
		{
			// Write "kind" byte:
			out.write(MODEL_SERIALISATION_KIND_COMPRESSED_COLLECTOR_PROJECT_XML);
			// Serialise project and write compressed result to the OutputStream:
			projectStoreHandle.executeNoDBEx(new StoreOperation<ProjectStore, IOException>()
			{
				@Override
				public void execute(ProjectStore store) throws IOException
				{
					OutputStream cOut = null;
					try
					{
						cOut = compress(out);
						store.serialise(project, cOut);
					}
					finally
					{
						StreamHelpers.SilentFlushAndClose(cOut);
					}
				}
			});
		}
		else
			throw new UnknownModelException(model.id, model.getName());
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.StorageClient#deserialiseClientModel(byte, java.io.InputStream)
	 */
	@Override
	protected Model deserialiseClientModel(byte kind, final InputStream in) throws Exception
	{
		if(kind == MODEL_SERIALISATION_KIND_COMPRESSED_COLLECTOR_PROJECT_XML)
		{
			Project project = projectStoreHandle.executeWithReturn(new StoreOperationWithReturn<ProjectStore, Project, Exception>()
			{
				@Override
				public Project execute(ProjectStore store) throws IOException
				{
					InputStream dcIn = null;
					try
					{
						dcIn = decompress(in);
						return store.deserialise(dcIn);
					}
					finally
					{
						StreamHelpers.SilentClose(dcIn);
					}
				}
			});
			if(project != null)
				return project.getModel();
		}
		// else:
		return null;
	}
	
	/**
	 * @param model
	 * @return the project corresponding to the given model, or {@code null} if the model was {@code null}, if no such project was found, or if no projectStore is available
	 */
	public Project getProject(Model model)
	{
		if(model == null)
			return null;
		else
			return getProject(model.id);
	}
	
	/**
	 * @param modelID
	 * @return the project corresponding to the given modelID, or {@code null} if no such project was found or if no projectStore is available
	 */
	public Project getProject(final long modelID)
	{
		return projectStoreHandle.executeWithReturnNoDBEx(new StoreOperationWithReturnNoException<ProjectStore, Project>()
		{
			@Override
			public Project execute(ProjectStore store)
			{
				return store.retrieveProject(GetProjectID(modelID), GetProjectFingerPrint(modelID));
			}
		});
	}
	
	/**
	 * @param schema the {@link Schema} of the {@link Form} being requested
	 * @return the {@link Form} that is backed by the given {@link Schema}, or {@code null} if no such Form is found
	 */
	public Form getForm(Schema schema)
	{
		if(schema == null)
			throw new NullPointerException("Schema cannot be null");
		Project project = getProject(schema.getModelID());
		return project != null ?
			project.getForm(schema) : // returns null if no matching Form is found
			null;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.StorageClient#getClientModel(long)
	 */
	@Override
	protected Model getClientModel(long modelID)
	{
		Project project = getProject(modelID);
		if(project != null)
			return project.getModel();
		else
			return null;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.StorageClient#getSchemaV1(int, int)
	 */
	@Override
	public Schema getSchemaV1(final int schemaID, final int schemaVersion) throws UnknownModelException
	{
		try
		{
			return projectStoreHandle.executeWithReturn(new StoreOperationWithReturn<ProjectStore, Schema, Exception>()
			{
				@Override
				public Schema execute(ProjectStore store) throws Exception
				{
					Project project = projectStoreHandle.getStore(this).retrieveV1Project(schemaID, schemaVersion); // can throw NPE or DBException
					// return schema of the first (and assumed only) form:
					return project.getForm(0).getSchema(); // can throw NPE
				}
			});
		}
		catch(Exception e)
		{	// regardless of whether it is an NPE, DBException or another Exception:
			throw new UnknownModelException(schemaID, schemaVersion);
		}
	}

	/**
	 * Note:
	 * 	Currently we only return MediaFiles associated with MediaFields. In the
	 * 	future there may be other types of CollectorAttachments.
	 *  
	 * @see uk.ac.ucl.excites.sapelli.storage.StorageClient#getRecordAttachments(uk.ac.ucl.excites.sapelli.storage.model.Record)
	 */
	@Override
	public List<? extends CollectorAttachment<?>> getRecordAttachments(Record record)
	{
		if(record != null)
		{
			Project project = getProject(record.getSchema().model);
			FileStorageProvider fsp = getFileStorageProvider();
		
			if(project != null && fsp != null)
				return CollectorAttachmentUtils.getMediaFiles(project, record, fsp, false);
		}
		//else:
		return Collections.<CollectorAttachment<?>> emptyList();
	}

	@Override
	public Payload createCustomPayload(int nonBuiltinType)
	{
		return null; // for now there are no Sapelli Collector-specific transmission payloads
	}

	@Override
	public List<Correspondent> getReceiversFor(final Schema schema)
	{
		try
		{
			// Get project:
			final Project project = getProject(schema.getModelID()); 
			if(project == null)
				throw new NullPointerException("No matching project found!");
			
			// Get schedules (and their receivers) for the project:
			final List<Correspondent> receivers = new ArrayList<Correspondent>();
			projectStoreHandle.executeNoEx(new StoreHandle.StoreOperationNoException<ProjectStore>()
			{
				@Override
				public void execute(ProjectStore pStore)
				{
					for(SendSchedule schedule : pStore.retrieveSendSchedulesForProject(project))
						if(SendSchedule.hasValidReceiver(schedule))
							receivers.add(schedule.getReceiver());
				}
			});
			
			// Return list:
			return receivers;
		}
		catch(Exception e)
		{
			logError("Error getting receivers for " + schema.toString(), e);
			return Collections.<Correspondent> emptyList();
		}
	}

}
