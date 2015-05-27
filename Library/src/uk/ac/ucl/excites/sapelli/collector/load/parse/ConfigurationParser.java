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

package uk.ac.ucl.excites.sapelli.collector.load.parse;

import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.shared.util.xml.SubtreeParser;
import uk.ac.ucl.excites.sapelli.shared.util.xml.XMLAttributes;

/**
 * @author mstevens
 * 
 */
public class ConfigurationParser extends SubtreeParser<ProjectParser>
{

	// STATICS--------------------------------------------------------

	// TAGS
	static private final String TAG_CONFIGURATION = "Configuration";
	static private final String TAG_LOGGING = "Logging";
	/**
	 * @deprecated
	 */
	static private final String TAG_TRANSMISSION = "Transmission";

	// ATTRIBUTES
	static private final String ATTRIBUTE_ENABLED = "enabled";

	// DYNAMICS-------------------------------------------------------
	private Project project;

	public ConfigurationParser(ProjectParser projectParser)
	{
		super(projectParser, TAG_CONFIGURATION);
		this.project = projectParser.getProject();
	}

	@Override
	public void parseStartElement(String uri, String localName, String qName, XMLAttributes attributes) throws Exception
	{
		// <Configuration>
		if(qName.equals(TAG_CONFIGURATION))
		{
			activate();
		}
		// children of <Configuration>
		else if(isActive())
		{
			// <Logging>
			if(qName.equals(TAG_LOGGING))
			{
				project.setLogging(attributes.getBoolean(ATTRIBUTE_ENABLED, Project.DEFAULT_LOGGING));
			}
			// <Transmission> (deprecated)
			else if(qName.equals(TAG_TRANSMISSION))
			{
				addWarning("Use of the <" + TAG_TRANSMISSION + "> tag has been deprecated and it is being ignore. Use the UI to configure transmission settings.");
			}

			// Add future configuration elements here...
			
			// <?> in <Configuration>
			else
			{
				addWarning("Ignored unrecognised or invalidly placed element <" + qName + "> occuring within <" + TAG_CONFIGURATION + ">.");
			}
		}
		// <?> outside of <Configuration> (shouldn't happen)
		else
		{
			throw new IllegalArgumentException("ConfigurationParser only deals with elements that are equal to, or contained within <" + TAG_CONFIGURATION + ">.");
		}
	}

	@Override
	public void parseEndElement(String uri, String localName, String qName) throws Exception
	{
		// add tings here...
		
		// </Configuration>
		/*else */if(qName.equals(TAG_CONFIGURATION))
		{
			deactivate();
		}
	}
	
	@Override
	protected void reset()
	{
		// does nothing (this SubTreeParser is single use anyway)
	}

	@Override
	protected boolean isSingleUse()
	{
		return true; //only 1 <Configuration> element per Project
	}

}
