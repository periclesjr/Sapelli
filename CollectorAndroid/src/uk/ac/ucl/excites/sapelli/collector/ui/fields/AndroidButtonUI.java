/**
 * 
 */
package uk.ac.ucl.excites.sapelli.collector.ui.fields;

import uk.ac.ucl.excites.sapelli.collector.control.CollectorController;
import uk.ac.ucl.excites.sapelli.collector.model.CollectorRecord;
import uk.ac.ucl.excites.sapelli.collector.model.fields.ButtonField;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/**
 * Android version of ButtonUI
 * 
 * @author mstevens
 */
public class AndroidButtonUI extends ButtonUI<View> implements OnClickListener
{
	
	private Button button;
	
	public AndroidButtonUI(ButtonField buttonField, CollectorController controller, CollectorView collectorView)
	{
		super(buttonField, controller, collectorView);
	}

	@Override
	public void onClick(View v)
	{
		buttonPressed();
	}

	@Override
	public Button getPlatformView(boolean onPage, CollectorRecord record)
	{
		if(button == null)
		{
			button = new Button(((CollectorView) collectorUI).getContext());
			button.setText(field.getLabel());
			button.setOnClickListener(this);
		}
		return button;
	}

}