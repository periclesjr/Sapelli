package uk.ac.ucl.excites.transmission.sms;

/**
 * @author julia, mstevens
 *
 */
public class SMSAgent
{
	
	//Dynamics
	private String phoneNumber;
	
	public SMSAgent(String phoneNumber)
	{
		if(phoneNumber == null || phoneNumber.isEmpty())
			throw new IllegalArgumentException("Invalid phone number.");
		this.phoneNumber = phoneNumber;
	}
	
	/**
	 * @return the phoneNumber
	 */
	public String getPhoneNumber()
	{
		return phoneNumber;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if(o instanceof SMSAgent)
			return phoneNumber.equals(((SMSAgent) o).phoneNumber);
		else
			return false;
	}

}