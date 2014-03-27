package uk.ac.ucl.excites.sapelli.collector.ui.fields;

import uk.ac.ucl.excites.sapelli.collector.control.CollectorController;
import uk.ac.ucl.excites.sapelli.collector.model.CollectorRecord;
import uk.ac.ucl.excites.sapelli.collector.model.fields.TextBoxField;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorView;
import uk.ac.ucl.excites.sapelli.storage.model.columns.StringColumn;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

/**
 * @author Julia, mstevens
 * 
 */
public class AndroidTextBoxUI extends TextBoxUI<View>
{
	
	static private final LayoutParams FULL_WIDTH = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
	
	private TextBoxView view;

	public AndroidTextBoxUI(TextBoxField textBox, CollectorController controller, CollectorView collectorView)
	{
		super(textBox, controller, collectorView);
	}
	
	@Override
	protected String getValue()
	{
		if(view == null)
			return null; // this shouldn't happen
		return view.getText();
	}
	
	@Override
	public View getPlatformView(boolean onPage, CollectorRecord record)
	{
		if(view == null)
			view = new TextBoxView(((CollectorView) collectorUI).getContext());
		
		// Update view:
		StringColumn col = (StringColumn) field.getColumn();
		if(record.isValueSet(col))
			view.setText(col.retrieveValue(record));
		else
			view.setText(field.getInitialValue());
		
		return view;
	}
	

	@Override
	protected void setValidationError(String errorDescr)
	{
		if(view != null)
			view.setError(errorDescr);
	}

	@Override
	protected void clearValidationError()
	{
		if(view != null)
			view.clearError();
	}
	
	public class TextBoxView extends LinearLayout
	{

		private TextBoxField field;
		private EditText editText;
		private TextView errorMsg;
		
		public TextBoxView(Context context)
		{
			super(context);

			setOrientation(LinearLayout.VERTICAL);

			// Label:
			TextView label = new TextView(context);
			label.setText(field.getLabel());
			label.setLayoutParams(FULL_WIDTH);
			addView(label);

			// Textbox:
			editText = new EditText(context);
			editText.setLayoutParams(FULL_WIDTH);
			if(field.isMultiline() == true)
				editText.setSingleLine(false);
			else
				editText.setSingleLine(true);
			addView(editText);

			// Error msg:
			errorMsg = new TextView(context);
			errorMsg.setLayoutParams(FULL_WIDTH);
			errorMsg.setTextColor(Color.RED);
			errorMsg.setVisibility(GONE);
			addView(errorMsg);

			editText.addTextChangedListener(new TextWatcher()
			{	
				@Override
				final public void afterTextChanged(Editable s)
				{
					isValid(controller.getCurrentRecord());
				}

				@Override
				final public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Don't care */ }

				@Override
				final public void onTextChanged(CharSequence s, int start, int before, int count) { /* Don't care */ }
			});
		}
		
		public String getText()
		{
			return editText.getText().toString();
		}
		
		public void setText(String txt)
		{
			editText.setText(txt);
		}
		
		public void setError(String error)
		{
			errorMsg.setText(error);
			errorMsg.setVisibility(VISIBLE);
		}
		
		public void clearError()
		{
			errorMsg.setText("");
			errorMsg.setVisibility(GONE);
		}
		
	}
	
}