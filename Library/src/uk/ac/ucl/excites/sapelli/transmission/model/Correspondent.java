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

package uk.ac.ucl.excites.sapelli.transmission.model;

import uk.ac.ucl.excites.sapelli.shared.util.IntegerRangeMapping;
import uk.ac.ucl.excites.sapelli.transmission.Transmission;

/**
 * 
 * @author benelliott
 */
public class Correspondent
{
	
	static public final int CORRESPONDENT_ID_SIZE = 24; // bits
	static public final IntegerRangeMapping CORRESPONDENT_ID_FIELD = IntegerRangeMapping.ForSize(0, CORRESPONDENT_ID_SIZE); // unsigned(!) 24 bit integer
	
	static public final int CORRESPONDENT_NAME_MAX_LENGTH_BYTES = 32; // TODO 32 chars?
	static public final int CORRESPONDENT_ADDRESS_MAX_LENGTH_BYTES = 128; // TODO 128 chars? UTF8?
	static public final int CORRESPONDENT_ENCRYPTION_KEY_MAX_LENGTH_BYTES = 32; //TODO 256 bit?
	
	private String name; // name
	private Transmission.Type transmissionType;
	private String address; // phone number (for SMS) or URL (for HTTP)
	private String key; // encryption key TODO ??
	
	public Correspondent(String id, Transmission.Type transmissionType, String address)
	{
		this.name = id;
		this.transmissionType = transmissionType;
		this.address = address;
	}

	/**
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * @param id the name to set
	 */
	public void setName(String name)
	{
		this.name = name;
	}

	/**
	 * @return the transmissionType
	 */
	public Transmission.Type getTransmissionType()
	{
		return transmissionType;
	}

	/**
	 * @param transmissionType the transmissionType to set
	 */
	public void setTransmissionType(Transmission.Type transmissionType)
	{
		this.transmissionType = transmissionType;
	}

	/**
	 * @return the address
	 */
	public String getAddress()
	{
		return address;
	}

	/**
	 * @param address the address to set
	 */
	public void setAddress(String address)
	{
		this.address = address;
	}

	/**
	 * @return the key
	 */
	public String getKey()
	{
		return key;
	}

	/**
	 * @param key the key to set
	 */
	public void setKey(String key)
	{
		this.key = key;
	}
}