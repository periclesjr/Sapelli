/**
 * 
 */
package uk.ac.ucl.excites.sapelli.collector;

import java.util.Collections;
import java.util.Set;

import uk.ac.ucl.excites.sapelli.collector.db.ProjectStore;
import uk.ac.ucl.excites.sapelli.collector.model.Form;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.transmission.Settings;
import uk.ac.ucl.excites.sapelli.transmission.TransmissionClient;

/**
 * @author mstevens
 *
 */
public class SapelliCollectorClient implements TransmissionClient
{
	
	// STATICS-------------------------------------------------------
	/**
	 * @param project
	 * @return
	 */
	static public long GetModelID(Project project)
	{
		return GetModelID(project.getID(), project.hashCode());
	}
	
	/**
	 * @param projectID unsigned(!) 24 bit integer
	 * @param projectHash signed 32 bit integer
	 * @return
	 */
	static public long GetModelID(int projectID, int projectHash)
	{
		return	((((long) projectHash) & 0xffffffffL) << Project.PROJECT_ID_SIZE) +	// Project hash takes up first 32 bits
				projectID;															// Project id takes up next 24 bits
	}
	
	static public int GetProjectID(long modelID)
	{
		return (int) (modelID % (1 << Project.PROJECT_ID_SIZE));
	}
	
	static public int GetProjectHash(long modelID)
	{
		return (int) (modelID >> Project.PROJECT_ID_SIZE);
	}
	
	static public short GetSchemaNumber(Form form)
	{
		return form.getPosition();
	}
	
	// DYNAMICS------------------------------------------------------
	private ProjectStore projectStore;
	
	public SapelliCollectorClient(ProjectStore projectStore)
	{
		this.projectStore = projectStore;
	}
	
	@Override
	public short getNumberOfSchemataInModel(long modelID)
	{
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public Schema getSchema(long schemaID)
	{
//		Project p = projectStore.retrieveProject(GetProjectHash(schemaID));
//		if(p != null)
//			return p.getForm(GetFormIndex(schemaID)).getSchema();
//		else
			return null;
	}
	
	@Override
	public Schema getSchemaV1(int schemaID, int schemaVersion)
	{
//		Project p = projectStore.retrieveV1Project(schemaID, schemaVersion);
//		if(p != null)
//			return p.getForm(0).getSchema(); // return schema of the first (and assumed only) form
//		else
			return null;
	}
	
	public Form getForm(Schema schema)
	{
//		Project proj = projectStore.retrieveProject(GetProjectHash(schema.getID()));
//		if(proj != null)
//			return proj.getForm(GetFormIndex(schema.getID()));
//		else
			return null;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.transmission.TransmissionClient#getSettingsFor(uk.ac.ucl.excites.storage.model.Schema)
	 */
	@Override
	public Settings getSettingsFor(Schema schema)
	{
		/*TODO FIX THIS
		 * This is buggy/hacky! Because schema's can be shared by multiple forms (and no schema ID/version duplicates are allowed)
		 * we cannot safely determine transmission settings based on the schema id/version.
		 */
//		List<Form> forms = dao.retrieveForms(schema.getID(), schema.getVersion());
//		if(!forms.isEmpty())
//		{
//			if(forms.get(0)/*HACK!*/.getProject() != null)
//				return forms.get(0).getProject().getTransmissionSettings();
//			else
//				return null;
//		}
//		else
//		{
//			return null;
//		}
		return null;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.transmission.TransmissionClient#getFactoredOutColumnsFor(uk.ac.ucl.excites.storage.model.Schema)
	 */
	@Override
	public Set<Column<?>> getFactoredOutColumnsFor(Schema schema)
	{
		return Collections.<Column<?>> singleton(Form.COLUMN_DEVICE_ID);
	}

	@Override
	public void recordInserted(Record record)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void recordUpdated(Record record)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void recordDeleted(Record record)
	{
		// TODO Auto-generated method stub
		
	}

}
