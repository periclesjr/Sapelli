/**
 * 
 */
package uk.ac.ucl.excites.transmission.sms.text;

import java.util.Set;

import uk.ac.ucl.excites.storage.model.Column;
import uk.ac.ucl.excites.storage.model.Schema;
import uk.ac.ucl.excites.transmission.ModelProvider;
import uk.ac.ucl.excites.transmission.Settings;
import uk.ac.ucl.excites.transmission.sms.SMSAgent;
import uk.ac.ucl.excites.transmission.sms.SMSTransmission;
import uk.ac.ucl.excites.transmission.util.TransmissionCapacityExceededException;

/**
 * @author mstevens
 *
 */
public class TextSMSTransmission extends SMSTransmission
{
	
	/**
	 * To be called on the sending side.
	 * 
	 * @param schema
	 * @param id
	 * @param receiver
	 * @param settings
	 */
	public TextSMSTransmission(Schema schema, int id, SMSAgent receiver, Settings settings)
	{
		super(schema, null, id, receiver, settings);
	}

	/**
	 * To be called on the sending side.
	 * 
	 * @param schema
	 * @param columnsToFactorOut
	 * @param id
	 * @param receiver
	 * @param settings
	 */
	public TextSMSTransmission(Schema schema, Set<Column<?>> columnsToFactorOut, int id, SMSAgent receiver, Settings settings)
	{
		super(schema, columnsToFactorOut, id, receiver, settings);
	}

	/**
	 * To be called on the receiving side.
	 * 
	 * @param modelProvider
	 * @param settings
	 */
	public TextSMSTransmission(ModelProvider modelProvider)
	{
		super(modelProvider);
	}
	
	@Override
	protected void serialiseAndSplit(byte[] data) throws TransmissionCapacityExceededException
	{
		//TODO<
	}

	@Override
	protected byte[] mergeAndDeserialise()
	{
		// TODO
		return null;
	}

}