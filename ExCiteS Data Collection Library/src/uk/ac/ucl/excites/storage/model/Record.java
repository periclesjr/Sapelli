/**
 * 
 */
package uk.ac.ucl.excites.storage.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import uk.ac.ucl.excites.storage.io.BitInputStream;
import uk.ac.ucl.excites.storage.io.BitOutputStream;
import uk.ac.ucl.excites.transmission.Transmission;
import uk.ac.ucl.excites.util.StringUtils;
import uk.ac.ucl.excites.util.XMLUtils;

/**
 * @author mstevens
 *
 */
public class Record
{
	
	static public final String TAG_RECORD = "Record";
	
	static public final String ATTRIBUTE_FORM_SCHEMA_ID = "schema-id";
	static public final String ATTRIBUTE_FORM_SCHEMA_VERSION = "schema-version";
	
	protected Schema schema;
	protected Object[] values;
	protected Transmission transmission = null;
	
	public Record(Schema schema)
	{
		if(schema == null)
			throw new NullPointerException("Schema cannot be null!");
		if(!schema.isSealed())
			throw new IllegalStateException("Schema must be sealed before records based on it can be created!");
		this.schema = schema;
		values = new Object[schema.getColumns().size()];
	}
	
	/**
	 * @return the schema
	 */
	public Schema getSchema()
	{
		return schema;
	}
	
	public Transmission getTransmission()
	{
		return transmission;
	}
	
	public void setTransmission(Transmission transmission)
	{
		this.transmission = transmission;
	}
	
	protected void setValue(Column<?> column, Object value)
	{
		setValue(schema.getColumnIndex(column), value);
	}

	protected void setValue(String columnName, Object value)
	{
		setValue(schema.getColumnIndex(columnName), value);
	}
	
	protected void setValue(int columnIndex, Object value)
	{
		values[columnIndex] = value;
	}

	protected Object getValue(Column<?> column)
	{
		return getValue(schema.getColumnIndex(column));
	}
	
	protected Object getValue(String columnName)
	{
		return getValue(schema.getColumnIndex(columnName));
	}
	
	protected Object getValue(int columnIndex)
	{
		return values[columnIndex];
	}
	
	/**
	 * Sets values, provided as strings (which can be empty or null for the optional columns only), in order of the columns
	 * 
	 * @param values
	 * @throws Exception
	 */
	public void setParsedValues(String... values) throws Exception
	{
		if(values == null || values.length == 0)
			throw new IllegalArgumentException("No values provided.");
		if(values.length != schema.getColumns().size())
			throw new IllegalArgumentException("Mismatch between number of values provided (" + values.length + ") and the number of columns in the schema (" + schema.getColumns().size() + "). Please remember to put null for empty optional columns.");
		int c = 0;
		for(String v : values)
		{
			schema.getColumn(c).parseAndStoreValue(this, v);
			c++;
		}
	}
	
	public boolean isFilled()
	{
		for(Column<?> c : schema.getColumns())
		{
			Object v = getValue(c);
			if(v == null && !c.isOptional())
				return false; //null value in non-optional column	
		}		
		return true;
	}
	
	public void writeToBitStream(BitOutputStream bitStream, Set<Column<?>> skipColumns) throws IOException
	{
		try
		{	//write fields:
			for(Column<?> c : schema.getColumns())
				if(!skipColumns.contains(c))
					c.retrieveAndWriteValue(this, bitStream);
		}
		catch(Exception e)
		{
			throw new IOException("Error on attempting to write record", e);
		}
	}
	
	public void readFromBitStream(BitInputStream bitStream, Set<Column<?>> skipColumns) throws IOException
	{
		try
		{	//read fields:
			for(Column<?> c : schema.getColumns())
				if(!skipColumns.contains(c))
					c.readAndStoreValue(this, bitStream); 
		}
		catch(Exception e)
		{
			throw new IOException("Error on attempting to read record", e);
		}
	}
	
	/**
	 * Gets the size of this record in number of bits
	 * 
	 * @return
	 */
	public int getSize()
	{
		BitOutputStream out = null;
		try
		{
			out = new BitOutputStream(new ByteArrayOutputStream());
			this.writeToBitStream(out, new HashSet<Column<?>>());
			return out.getNumberOfBitsWritten();
		}
		catch(IOException e)
		{
			System.err.println("Error upon calculating record size: " + e.getLocalizedMessage());
			e.printStackTrace(System.err);
			return -1;
		}
		finally
		{
			if(out != null)
				try
				{
					out.close();
				}
				catch(IOException ignore) {}
		}
	}
	
	@Override
	public String toString()
	{
		StringBuffer bff = new StringBuffer();
		for(Column<?> c : schema.getColumns())
			bff.append(c.getName() + ": " + c.retrieveValueAsString(this) + "|");
		return bff.toString();
	}
	
	public String toXML(int tabs)
	{
		//TODO transmission/sent
		StringBuilder bldr = new StringBuilder();
		bldr.append(StringUtils.addTabsFront("<" + TAG_RECORD + " " + ATTRIBUTE_FORM_SCHEMA_ID + "=\"" + schema.getID() + "\" " + ATTRIBUTE_FORM_SCHEMA_VERSION + "=\"" + schema.getVersion() + "\">\n", tabs));
		for(Column<?> c : schema.getColumns())
		{
			String columnName = XMLUtils.escapeCharacters(c.getName());
			String valueStr = c.retrieveValueAsString(this);
			if(valueStr != null)
				bldr.append(StringUtils.addTabsFront("<" + columnName + ">" + XMLUtils.escapeCharacters(valueStr) + "</" + columnName + ">\n", tabs + 1));
			else
			{
				bldr.append(StringUtils.addTabsFront(XMLUtils.comment(columnName + " is null") + "\n", tabs + 1));
			}
		}
		bldr.append(StringUtils.addTabsFront("</" + TAG_RECORD + ">", tabs));
		return bldr.toString();
	}
	
}