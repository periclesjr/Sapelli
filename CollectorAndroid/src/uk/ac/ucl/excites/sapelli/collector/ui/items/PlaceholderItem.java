package uk.ac.ucl.excites.sapelli.collector.ui.items;

import android.content.Context;
import android.view.View;

public class PlaceholderItem extends Item
{
	
	public PlaceholderItem()
	{
		this(null);
	}

	public PlaceholderItem(Long id)
	{
		super(id);
		visible = false; //!!!
	}
	
	@Override
	public void setVisibility(boolean visible)
	{
		//do nothing (placeholder can never be visible)
	}

	@Override
	protected View createView(Context context)
	{
		return new View(context);
	}

}