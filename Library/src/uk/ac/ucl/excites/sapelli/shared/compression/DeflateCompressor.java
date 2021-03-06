/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2016 University College London - ExCiteS group
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

package uk.ac.ucl.excites.sapelli.shared.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import uk.ac.ucl.excites.sapelli.shared.compression.CompressorFactory.Compression;

/**
 * DEFLATE compressor.
 * Uses Java SE's implementation (java.util.zip).
 * 
 * @author mstevens
 * @see <a href="http://en.wikipedia.org/wiki/DEFLATE">http://en.wikipedia.org/wiki/DEFLATE</a>
 * @see <a href="http://docs.oracle.com/javase/6/docs/api/java/util/zip/package-summary.html">http://docs.oracle.com/javase/6/docs/api/java/util/zip/package-summary.html</a>
 */
public class DeflateCompressor extends Compressor
{
	
	static public final boolean DEFAULT_HEADERLESS = true;
	
	private final boolean headerless;
	
	/**
	 * 
	 */
	public DeflateCompressor()
	{
		this(DEFAULT_HEADERLESS);
	}
	
	/**
	 * @param headerless
	 */
	public DeflateCompressor(boolean headerless)
	{
		this.headerless = headerless;
	}
	
	@Override
	protected OutputStream _getOutputStream(OutputStream sink, long uncompressedSizeBytes) throws IOException
	{
		return new DeflaterOutputStream(sink, new Deflater(Deflater.BEST_COMPRESSION, headerless)); // best compression & no header)
	}

	@Override
	public InputStream getInputStream(InputStream source) throws IOException
	{
		return new InflaterInputStream(source, new Inflater(headerless));
	}

	@Override
	public Compression getMode()
	{
		return Compression.DEFLATE;
	}

}
