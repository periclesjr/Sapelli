/**
 * 
 */
package uk.ac.ucl.excites.sapelli.collector.model.fields;

import uk.ac.ucl.excites.sapelli.collector.control.Controller;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.Form;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorUI;
import uk.ac.ucl.excites.sapelli.collector.ui.FieldUI;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.util.StringUtils;

/**
 * @author mstevens
 *
 */
public class LabelField extends Field
{

	static public final String ID_PREFIX = "lbl";
	
	/**
	 * @param form
	 * @param text
	 */
	public LabelField(Form form, String text)
	{
		this(form, null, text);
	}
	
	/**
	 * @param form
	 * @param id
	 * @param text
	 */
	public LabelField(Form form, String id, String text)
	{	
		super(form, (id == null || id.isEmpty() ? ID_PREFIX + (text.trim().isEmpty() ? form.getFields().size() : StringUtils.replaceWhitespace(text.trim(), "_")) : id), text);
		this.noColumn = true;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.collector.project.model.Field#createColumn()
	 */
	@Override
	protected Column<?> createColumn()
	{
		return null;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.collector.project.model.Field#enter(uk.ac.ucl.excites.collector.project.ui.Controller)
	 */
	@Override
	public boolean enter(Controller controller)
	{
		return false;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.collector.project.model.Field#createUI(uk.ac.ucl.excites.collector.project.ui.CollectorUI)
	 */
	@Override
	public FieldUI createUI(CollectorUI collectorUI)
	{
		return collectorUI.createLabelUI(this);
	}
	
	public void setNoColumn(boolean noColumn)
	{
		throw new UnsupportedOperationException("setNoColumn is unsupported on LabelFields since they never have columns.");
	}
	
}