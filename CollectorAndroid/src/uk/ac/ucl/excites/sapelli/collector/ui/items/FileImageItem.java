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

package uk.ac.ucl.excites.sapelli.collector.ui.items;

import java.io.File;
import java.io.FileInputStream;

import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import android.annotation.SuppressLint;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGBuilder;
import com.larvalabs.svgandroid.SVGDrawable;
//import com.caverock.androidsvg.SVG;

/**
 * An ImageItem subclass for images stored as files
 * 
 * @author mstevens
 */
public class FileImageItem extends ImageItem
{
	
	static private final String TAG = "FileImageItem";

	private File file;
	private static final String FILE_PREFIX = "file://";
	
	public FileImageItem(File file)
	{
		this(null, file);
	}
	
	@SuppressLint("DefaultLocale")
	public FileImageItem(Integer id, File file)
	{
		super(
				id,
				FileHelpers.getFileExtension(file.getName()).toLowerCase().startsWith("svg"), //will also work for svgz files
				FileHelpers.getFileExtension(file.getName()).toLowerCase().endsWith("gif") //if ends with "gif" then treat as animation
				); 
		this.file = file;
	}
	
	@Override
	protected void setImage(ImageView view)
	{
		try
		{
			if(!isVectorBased())
			{	// Raster image (PNG, JPG, GIF, ...):
				view.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));
			}
			else
			{	// Vector image (SVG or SVGZ):
				
				// Using svg-android lib:
				SVG svg = new SVGBuilder().readFromInputStream(new FileInputStream(file)).build();
				view.setImageDrawable(new SVGDrawable(svg));
				
				// Using AndroidSVG lib:
				//SVG svg = SVG.getFromInputStream(new FileInputStream(file));
		        //view.setImageDrawable(new PictureDrawable(svg.renderToPicture()));
			}
		}
		catch(Exception e)
		{
			Log.e(TAG, "Could not load image from file", e);
		}
	}
	
	@Override
	protected void setAnim(WebView view)
	{
		try
		{
			String url = FILE_PREFIX + file.getAbsolutePath();
			String html = "<html><body><img src=\"" + url + "\" width=\"100%\"/></body></html>"; //fit image to window
			view.loadDataWithBaseURL(null,html, "text/html","UTF-8", null);
			
			view.setVerticalScrollBarEnabled(false); //disable scrollbars
			view.setHorizontalScrollBarEnabled(false);

			view.setOnTouchListener(new View.OnTouchListener() {
			    @SuppressLint("ClickableViewAccessibility")
				@Override
			    public boolean onTouch(View v, MotionEvent event) {
			    	//override normal WebView touch behaviour and pass click up to parent RelativeLayout

			    	RelativeLayout container = (RelativeLayout)v.getParent(); //find RelativeLayout by traversing up the view hierarchy
			    	AdapterView<?> grid = (AdapterView<?>)container.getParent(); //find AdapterView that holds it
			    	
			    	//Force the AdapterView's performItemClick on the Item corresponding to the animation
			    	return grid.performItemClick(
			    			container,
			    			grid.getPositionForView(container),
			    			grid.getItemIdAtPosition(grid.getPositionForView(container))
			    			);	
			    }  
			});
		}
		catch(Exception e)
		{
			Log.e(TAG, "Could not load image from path "+FILE_PREFIX + file.getAbsolutePath(), e);
		}
	}
	
	

}