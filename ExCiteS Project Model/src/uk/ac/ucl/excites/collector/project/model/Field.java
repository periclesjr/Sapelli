package uk.ac.ucl.excites.collector.project.model;

import uk.ac.ucl.excites.collector.project.ui.FieldView;
import uk.ac.ucl.excites.storage.model.Column;

/**
 * @author mstevens
 *
 */
public abstract class Field
{
	
	//Statics----------------------------------------------
	
	//Defaults:
	static public final boolean DEFAULT_ENABLED = true;
	static public final boolean DEFAULT_OPTIONAL = true;
	static public final boolean DEFAULT_NO_COLUMN = false;
	
	//Dynamics---------------------------------------------
	protected String id;
	protected Field jump;
	protected boolean enabled = DEFAULT_ENABLED;
	protected boolean optional = DEFAULT_OPTIONAL;
	protected boolean noColumn = DEFAULT_NO_COLUMN;
	
	public Field(String id)
	{
		if(id == null || id.isEmpty())
			throw new NullPointerException("ID cannot be null or empty.");
		this.id = id.trim();
	}
	
	/**
	 * @return the id
	 */
	public String getID()
	{
		return id;
	}
	
	/**
	 * @return the noColumn
	 */
	public boolean isNoColumn()
	{
		return noColumn;
	}

	/**
	 * @param noColumn the noColumn to set
	 */
	public void setNoColumn(boolean noColumn)
	{
		this.noColumn = noColumn;
	}

	/**
	 * @return the optional
	 */
	public boolean isOptional()
	{
		return optional;
	}

	/**
	 * @param optional the optional to set
	 */
	public void setOptional(boolean optional)
	{
		this.optional = optional;
	}

	public void setJump(Field target)
	{
		this.jump = target;
	}
	
	public Field getJump()
	{
		return jump;
	}
	
	/**
	 * @return the enabled
	 */
	public boolean isEnabled()
	{
		return enabled;
	}
	
	public void disable()
	{
		enabled = false;
	}
	
	public void enable()
	{
		enabled = true;
	}
	
	/**
	 * Returns a new Column object capable of storing values for this field
	 * It is assumed that the field.id is used as the column name.
	 * 
	 * @return
	 */
	protected abstract Column<?> createColumn();
	
	/**
	 * Meant to be overridden in (some) subclasses
	 * 
	 * @return the root Field of this Field
	 */
	public Field getRoot()
	{
		return this;
	}
	
	/**
	 * Meant to be overridden in (some) subclasses
	 * 
	 * @return whether or not this is a root Field
	 */
	public boolean isRoot()
	{
		return true;
	}
	
	public abstract void setIn(FieldView fv);
	
}
