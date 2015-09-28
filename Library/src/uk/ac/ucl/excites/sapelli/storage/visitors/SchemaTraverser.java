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

package uk.ac.ucl.excites.sapelli.storage.visitors;

import java.util.Collections;
import java.util.Set;
import java.util.Stack;

import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.model.ValueSetColumn;
import uk.ac.ucl.excites.sapelli.storage.util.ColumnPointer;

/**
 * TODO
 * 
 * @author mstevens
 */
public abstract class SchemaTraverser implements ColumnVisitor 
{

	private final Stack<ValueSetColumn<?>> parentStack = new Stack<ValueSetColumn<?>>();
	
	public void traverse(Schema schema)
	{
		traverse(schema, Collections.<Column<?>> emptySet());
	}
	
	public void traverse(Schema schema, Set<? extends Column<?>> skipColumns)
	{
		parentStack.clear();
		schema.accept(this, skipColumns);
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.visitors.ColumnVisitor#enter(uk.ac.ucl.excites.sapelli.storage.model.ValueSetColumn)
	 */
	@Override
	public void enter(ValueSetColumn<?> recordCol)
	{
		parentStack.push(recordCol);
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.visitors.ColumnVisitor#leave(uk.ac.ucl.excites.sapelli.storage.model.ValueSetColumn)
	 */
	@Override
	public void leave(ValueSetColumn<?> recordCol)
	{
		parentStack.pop();
	}
	
	/**
	 * @return
	 */
	protected <C extends Column<?>> ColumnPointer<C> getColumnPointer(C currentColumn)
	{
		return new ColumnPointer<C>(parentStack, currentColumn); // checks will be performed to ensure the currentColumn is really a child of the parent(s)
	}
	
}