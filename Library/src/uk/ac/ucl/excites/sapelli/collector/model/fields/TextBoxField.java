/**
 * 
 */
package uk.ac.ucl.excites.sapelli.collector.model.fields;

import uk.ac.ucl.excites.sapelli.collector.control.Controller;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.Form;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.TextBoxUI;
import uk.ac.ucl.excites.sapelli.shared.util.StringUtils;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.columns.StringColumn;

/**
 * @author Julia
 * 
 */
public class TextBoxField extends Field
{

	static public final String ID_PREFIX = "txt";

	// Defaults
	public static final int DEFAULT_MIN_LENGTH = 0; // minimum length of 0 if not set
	public static final int DEFAULT_MAX_LENGTH = 100; // maximum length of 100 if not set
	public static final boolean DEFAULT_MULTILINE = false; // single-line by default
	public static final String DEFAULT_INITIAL_VALUE = ""; // empty String is the default/initial initialValue

	// Dynamics
	private int maxLength;
	private int minLength;
	private boolean multiline;
	private String initialValue;
	
	
	/**
	 * @param form
	 * @param id
	 * @parap label
	 */
	public TextBoxField(Form form, String id, String label)
	{
		super(form, (id == null || id.isEmpty() ? ID_PREFIX + (label.trim().isEmpty() ? form.getFields().size() : StringUtils.replaceWhitespace(label.trim(), "_")) : id), label);
		this.maxLength = DEFAULT_MAX_LENGTH;
		this.minLength = DEFAULT_MIN_LENGTH;
		this.multiline = DEFAULT_MULTILINE;
		this.initialValue = DEFAULT_INITIAL_VALUE;
	}

	/**
	 * @return the maxLength
	 */
	public int getMaxLength()
	{
		return maxLength;
	}

	/**
	 * @return the minLength
	 */
	public int getMinLength()
	{
		return minLength;
	}

	/**
	 * @return the multiline
	 */
	public boolean isMultiline()
	{
		return multiline;
	}

	/**
	 * @return the initValue
	 */
	public String getInitialValue()
	{
		return initialValue;
	}

	/* (non-Javadoc)
	 * 
	 * @see uk.ac.ucl.excites.collector.project.model.Field#createColumn()
	 */
	@Override
	protected Column<?> createColumn()
	{
		return StringColumn.ForCharacterCount(id, optional != Optionalness.NEVER, getMaxLength());
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.model.Field#enter(uk.ac.ucl.excites.sapelli.collector.control.Controller, boolean)
	 */
	@Override
	public boolean enter(Controller controller, boolean withPage)
	{
		return true;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.model.Field#createUI(uk.ac.ucl.excites.sapelli.collector.ui.CollectorUI)
	 */
	@Override
	public <V> TextBoxUI<V> createUI(CollectorUI<V> collectorUI)
	{
		return collectorUI.createTextFieldUI(this);
	}

	public void setMaxLength(int maxLength)
	{
		this.maxLength = maxLength;
	}

	public void setMinLength(int minLength)
	{
		this.minLength = minLength;

	}

	public void setMultiline(boolean multiline)
	{
		this.multiline = multiline;
	}

	public void setInitialValue(String initValue)
	{
		this.initialValue = initValue;
	}

}