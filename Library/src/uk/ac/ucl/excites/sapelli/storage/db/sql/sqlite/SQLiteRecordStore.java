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

package uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import uk.ac.ucl.excites.sapelli.shared.db.StoreBackupper;
import uk.ac.ucl.excites.sapelli.shared.db.exceptions.DBException;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.shared.util.CollectionUtils;
import uk.ac.ucl.excites.sapelli.shared.util.ExceptionHelpers;
import uk.ac.ucl.excites.sapelli.shared.util.Objects;
import uk.ac.ucl.excites.sapelli.shared.util.StringUtils;
import uk.ac.ucl.excites.sapelli.shared.util.TimeUtils;
import uk.ac.ucl.excites.sapelli.shared.util.TransactionalStringBuilder;
import uk.ac.ucl.excites.sapelli.storage.StorageClient;
import uk.ac.ucl.excites.sapelli.storage.db.exceptions.DBConstraintException;
import uk.ac.ucl.excites.sapelli.storage.db.exceptions.DBPrimaryKeyException;
import uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.types.SQLiteBlobColumn;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.types.SQLiteBooleanColumn;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.types.SQLiteDoubleColumn;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.types.SQLiteIntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.types.SQLiteStringColumn;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.ColumnSet;
import uk.ac.ucl.excites.sapelli.storage.model.ListColumn;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.RecordValueSet;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.model.ValueSet;
import uk.ac.ucl.excites.sapelli.storage.model.columns.BooleanColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.ByteArrayColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.FloatColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.IntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.StringColumn;
import uk.ac.ucl.excites.sapelli.storage.model.indexes.AutoIncrementingPrimaryKey;
import uk.ac.ucl.excites.sapelli.storage.model.indexes.Index;
import uk.ac.ucl.excites.sapelli.storage.model.indexes.PrimaryKey;
import uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.sources.Source;
import uk.ac.ucl.excites.sapelli.storage.types.TimeStamp;
import uk.ac.ucl.excites.sapelli.storage.types.TimeStampColumn;
import uk.ac.ucl.excites.sapelli.storage.util.ColumnPointer;
import uk.ac.ucl.excites.sapelli.storage.util.InvalidValueException;

/**
 * Abstract {@link SQLRecordStore} implementation which is backed by a SQLite database.
 * 
 * @author mstevens
 */
public abstract class SQLiteRecordStore extends SQLRecordStore<SQLiteRecordStore, SQLiteRecordStore.SQLiteTable, SQLiteRecordStore.SQLiteColumn<?, ?>>
{
	
	// Statics----------------------------------------------
	static public final String DATABASE_FILE_EXTENSION = "sqlite3";
	static public final String PARAM_PLACEHOLDER = "?";
	
	static public final String NULL_STRING = "NULL";
	static public final char QUOTE_CHAR = '\'';
	static public final String QUOTE_ESCAPE_STRING = "''";
	
	/**
	 * @see http://catalogue.pearsoned.co.uk/samplechapter/067232685X.pdf
	 */
	static public final Pattern identifierPattern = Pattern.compile("[a-zA-Z_]+[0-9a-zA-Z_]*");
	
	static public String GetDBFileName(String baseName)
	{
		return baseName + DATABASE_NAME_SUFFIX + "." + DATABASE_FILE_EXTENSION;
	}
	
	static private final boolean LOG_QUALIFIED_QUERIES = false;
	
	/**
	 * Test method
	 */
	/*static public void testIdentifierPattern()
	{
		String[] identifiersToTest =
				new String[] {	"mytable", "my_field", "xyz123", "_table14343", "_1col", "a", // all valid
								"my table", "my-field", "my.table", "123xyz", "_abc?col", "2" }; // all invalid
		for(String identifier : identifiersToTest)
			System.out.println(identifier + "\t" + identifierPattern.matcher(identifier).matches());
	}*/
	
	// Dynamics---------------------------------------------
	private final SQLiteTableFactory factory;
	
	/**
	 * @param client
	 */
	public SQLiteRecordStore(StorageClient client)
	{
		super(client, PARAM_PLACEHOLDER);
		factory = new SQLiteTableFactory();
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore#doInitialise()
	 */
	@Override
	protected void doInitialise() throws DBException
	{
		addProtectedTable("sqlite_master");
		addProtectedTable("sqlite_sequence");
		
		super.doInitialise(); // !!!
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore#getTableFactory()
	 */
	@Override
	protected TableFactory<SQLiteTable> getTableFactory()
	{
		return factory;
	}

	/**
	 * In SQlite basic transactions (those controlled with BEGIN...COMMIT/ROLLBACK) cannot
	 * be nested (for that one needs to use the SAVEPOINT and RELEASE commands, which we won't
	 * use here). However, for flexibility reasons we will pretend that it is possible (i.e. we
	 * don't throw an exception if a request arrives to open a 2nd, 3rd, etc. transaction).
	 * 
	 * @see <a href="http://sqlite.org/lang_transaction.html">http://sqlite.org/lang_transaction.html</a>
	 * 
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#doStartTransaction()
	 */
	@Override
	protected void doStartTransaction() throws DBException
	{
		if(!isInTransaction()) // only the first/outer transaction is real, all nested/inner transactions are simulated
			try
			{
				executeSQL("BEGIN TRANSACTION;");
			}
			catch(Exception ex)
			{
				throw new DBException("Could not open SQLite transaction", ex);
			}
	}

	@Override
	protected void doCommitTransaction() throws DBException
	{
		if(numberOfOpenTransactions() == 1) // higher numbers indicate nested transactions which are simulated
			try
			{
				executeSQL("COMMIT TRANSACTION;");
			}
			catch(Exception ex)
			{
				throw new DBException("Could not commit SQLite transaction", ex);
			}
	}

	@Override
	protected void doRollbackTransaction()
	{
		if(numberOfOpenTransactions() == 1) // higher numbers indicate nested transactions which are simulated
			try
			{
				executeSQL("ROLLBACK TRANSACTION;");
			}
			catch(DBException ex)
			{
				client.logError("Could not roll-back SQLite transaction: " + ExceptionHelpers.getMessageAndCause(ex));
			}
	}
	
	/**
	 * 
	 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore#sanitiseIdentifier(java.lang.String)
	 * @see https://www.sqlite.org/lang_keywords.html
	 * @see http://catalogue.pearsoned.co.uk/samplechapter/067232685X.pdf
	 */
	@Override
	protected String sanitiseIdentifier(String identifier)
	{
		if(identifierPattern.matcher(identifier).matches())
			return identifier;
		else
			return '"' + identifier + '"';
	}

	@Override
	protected boolean doesTableExist(String unsanitisedTableName)
	{
		SQLiteCursor cursor = null;
		try
		{
			cursor = executeQuery(	"SELECT name FROM sqlite_master WHERE type='table' AND name=?;",
									Collections.<SQLiteColumn<?, ?>> singletonList(new SQLiteStringColumn<String>(this, "name", null, null)),
									Collections.<String> singletonList(unsanitisedTableName));
			return cursor != null && cursor.hasRow();
		}
		catch(DBException e)
		{
			client.logError("Error in doesTableExist()", e);
			return false;
		}
		finally
		{
			if(cursor != null)
				cursor.close();
		}
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore#getAllTableNames()
	 */
	@Override
	protected List<String> getAllTableNames()
	{
		SQLiteCursor cursor = null;
		try
		{
			SQLiteStringColumn<String> nameCol = new SQLiteStringColumn<String>(this, "name", null, null);
			cursor = executeQuery(	"SELECT name FROM sqlite_master WHERE type='table'",
									Collections.<SQLiteColumn<?, ?>> emptyList(),
									Collections.<String> emptyList());
			List<String> tableNames = new ArrayList<String>();
			if(cursor != null)
				while(cursor.moveToNext())
					CollectionUtils.addIgnoreNull(tableNames, nameCol.getValueOrNull(cursor, 0));
			return tableNames;
		}
		catch(DBException e)
		{
			client.logError("Error in getAllTableNames()", e);
			return Collections.<String> emptyList();
		}
		finally
		{
			if(cursor != null)
				cursor.close();
		}
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#hasFullIndexSupport()
	 */
	@Override
	public boolean hasFullIndexSupport()
	{
		return true;
	}
	
	@Override
	protected String getNullString()
	{
		return NULL_STRING;
	}

	@Override
	protected char getQuoteChar()
	{
		return QUOTE_CHAR;
	}

	@Override
	protected String getQuoteEscapeString()
	{
		return QUOTE_ESCAPE_STRING;
	}
	
	/**
	 * @param sql
	 * @param paramCols list of SQLiteColumns which the parameters (?s) in the sql correspond to
	 * @param sapArguments list of SapType object which are the values to be bound to the parameters
	 * @return an cursor to iterate over the results
	 * @throws DBException
	 */
	protected abstract SQLiteCursor executeQuery(String sql, List<SQLiteColumn<?, ?>> paramCols, List<? extends Object> sapArguments) throws DBException;
	
	@SuppressWarnings("unused")
	protected String getQueryLogMessage(String sql, List<SQLiteColumn<?, ?>> paramCols, List<? extends Object> sapArguments) throws DBException
	{
		String[] quotedArgStrings = new String[paramCols.size()];
		for(int p = 0; p < quotedArgStrings.length; p++)
			quotedArgStrings[p] = paramCols.get(p).sapelliObjectToLiteral(sapArguments.get(p), true);
		
		if(LOG_QUALIFIED_QUERIES && sql.indexOf(PARAM_PLACEHOLDER) != -1)
		{
			TransactionalStringBuilder bldr = new TransactionalStringBuilder("\n - ");
			bldr.append("Executing query...");
			bldr.append("Generic:   " + sql);
			try
			{
				bldr.append("Qualified: " + StringUtils.replaceWithValues(sql, PARAM_PLACEHOLDER, quotedArgStrings));
			}
			catch(Exception e)
			{
				bldr.append("Failed to generate qualified query with arguments: " + StringUtils.join(quotedArgStrings, ", "));
			}
			return bldr.toString();
		}
		else
			return "Executing query: " + sql + (sapArguments.isEmpty() ? "" : " [Arguments: " + StringUtils.join(quotedArgStrings, ", ") + "]");
	}

	@Override
	protected void doBackup(StoreBackupper backuper, File destinationFolder) throws DBException
	{
		File currentDB = getDatabaseFile();
		if(currentDB != null && currentDB.exists() && destinationFolder.canWrite())
		{
			// Get destination file:
			File backupDB;
			if(backuper.isLabelFilesAsBackup())
			{	// File name format: [original_name_w/o_extension]_Backup_[timestamp].[original_extension]
				String extension = FileHelpers.getFileExtension(currentDB);
				backupDB = new File(destinationFolder,
									FileHelpers.trimFileExtensionAndDot(currentDB.getName()) + BACKUP_SUFFIX + TimeUtils.getTimestampForFileName() + "." + (extension.isEmpty() ? DATABASE_FILE_EXTENSION : extension));
			}
			else
				// Using original file name
				backupDB = new File(destinationFolder, currentDB.getName());
			// Perform the actual back-up:
			try
			{
				doBackup(backupDB);
			}
			catch(Exception e)
			{
				throw new DBException("Failed to back-up SQLite database to: " + backupDB.getAbsolutePath(), e);
			}
		}
		else
			throw new DBException("Failed to back-up SQLite database");
	}
	
	/**
	 * Back-up by means of file copy.
	 * May be overridden.
	 * 
	 * @param destinationFile
	 * @throws Exception
	 */
	protected void doBackup(File destinationFile) throws Exception
	{
		FileUtils.copyFile(getDatabaseFile(), destinationFile);
	}
	
	/**
	 * @return
	 */
	protected abstract File getDatabaseFile();
	
	/**
	 * @param sql
	 * @param paramCols - may be null
	 * @return
	 * @throws DBException
	 */
	protected abstract SQLiteStatement generateStatement(String sql, List<SQLiteColumn<?, ?>> paramCols) throws DBException;
	
	/**
	 * 
	 * @author mstevens
	 */
	public class SQLiteTable extends SQLRecordStore<SQLiteRecordStore, SQLiteRecordStore.SQLiteTable, SQLiteRecordStore.SQLiteColumn<?, ?>>.SQLTable
	{

		private final StatementHandle ROWIDStatementHandle = new StatementHandle(/*SelectROWIDHelper.class*/)
		{
			@Override
			protected StatementHelper getHelper()
			{
				return new SelectROWIDHelper(SQLiteTable.this);
			}
		};
		private final StatementHandle insertStatementHandle = new StatementHandle(/*RecordInsertHelper.class*/)
		{
			@Override
			protected StatementHelper getHelper()
			{
				return new RecordInsertHelper(SQLiteTable.this);
			}
		};
		private final StatementHandle updateStatementHandle = new StatementHandle(/*RecordUpdateHelper.class*/)
		{
			@Override
			protected StatementHelper getHelper()
			{
				return new RecordUpdateHelper(SQLiteTable.this);
			}
		};
		private final StatementHandle deleteStatementHandle = new StatementHandle(/*RecordDeleteHelper.class*/)
		{
			@Override
			protected StatementHelper getHelper()
			{
				return new RecordDeleteHelper(SQLiteTable.this);
			}
		};
		private final StatementHandle countStatementHandle = new StatementHandle(/*RecordCountHelper.class*/)
		{
			@Override
			protected StatementHelper getHelper()
			{
				return new RecordCountHelper(SQLiteTable.this);
			}
		};
		
		/**
		 * An explicitly defined column (i.e. corresponding to a Schema column), which acts as an alias for the ROWID column.
		 * This happens if there a single INTEGER column is the PRIMARY KEY (possibly AUTOINCREMENTing).
		 * 
		 * @see https://www.sqlite.org/lang_createtable.html#rowid
		 * @see https://www.sqlite.org/autoinc.html
		 */
		private SQLiteIntegerColumn<?> rowidAliasColumn;

		public SQLiteTable(Schema schema)
		{
			super(schema);
		}
		
		@Override
		protected SQLRecordStore<SQLiteRecordStore, SQLiteTable, SQLiteColumn<?, ?>>.TableCreationHelper getTableCreationHelper()
		{
			return new SQLiteTableCreationHelper(this);
		}
		
		/**
		 * Run a statement pertaining to a single record which results in a single long value.
		 * 
		 * @param recordOrReference
		 * @param statement
		 * @return the long value or null if there was no matching record
		 * @throws DBException
		 */
		protected synchronized Long executeLongQuery(RecordValueSet<?> recordOrReference, StatementHandle handle) throws DBException
		{
			// Check if table itself exists in db:
			if(!isInDB())
				return null;
			
			// Check if all PK (sub)columns have a value:
			//	if there is (complete) PK we can assume this record doesn't exist in the db (and we wouldn't be able to find it if it did):
			if(!recordOrReference.isReferenceable() /*also checks autoIncrPK*/)
				return null;
			
			// Get statement:
			SQLiteStatement statement = handle.getStatement();
			
			// Bind parameters:
			statement.retrieveAndBindAll(recordOrReference);
			
			//	Execute:
			return statement.executeLongQuery();
		}
		
		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#isRecordInDB(uk.ac.ucl.excites.sapelli.storage.model.RecordValueSet)
		 */
		@Override
		public boolean isRecordInDB(RecordValueSet<?> recordOrReference) throws DBException
		{
			return getROWID(recordOrReference) != null;
		}
		
		public Long getROWID(RecordValueSet<?> recordOrReference) throws DBException
		{
			//	Execute:
			return executeLongQuery(recordOrReference, ROWIDStatementHandle);
		}
		
		/**
		 * @see https://www.sqlite.org/lang_createtable.html#rowid
		 * @see https://www.sqlite.org/autoinc.html
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#insert(uk.ac.ucl.excites.sapelli.storage.model.Record)
		 */
		@Override
		public synchronized void insert(Record record) throws DBPrimaryKeyException, DBConstraintException, DBException
		{
			// Get/recycle statement...
			SQLiteStatement insertStatement = insertStatementHandle.getStatement();

			// Bind parameters:
			insertStatement.retrieveAndBindAll(record);

			// Check if the value of the ROWID is bound:
			Long boundROWID = rowidAliasColumn != null ? rowidAliasColumn.retrieve(record) : null;
			
			if(boundROWID == null && schema.getAutoIncrementingPrimaryKeyColumn() != null)
				// If the is an "auto-incrementing" primary key column (which is always the ROWID alias as well),
				//	and its value isn't bound yet, then SQLite will determine the value. But we must be careful
				//	because the value may not fit in the IntegerColumn. If it doesn't we must be able to undo
				//	the insertion. Hence we start a transaction:
				startTransaction();
			
			// Execute:
			long rowID = insertStatement.executeInsert();
			
			// Perform various checks & set autoIncr PK value if needed:
			if(boundROWID != null)
			{
				// Check if rowID matches excepted (i.e. bound) value:
				if(rowID != boundROWID.longValue())
					throw new DBException(insertStatement.formatMessageWithSQL("Execution of INSERT statement (%s) failed (Unexpected ROWID; expected: " + boundROWID + "; last inserted ROWID: " + rowID + ")"));
				
				// If needed, check whether INSERT really happened:
				if(insertStatement.mustLastInsertBeVerified() && !Objects.equals(boundROWID, getROWID(record))) // if insert failed getROWID(record) will return null
					throw new DBException(insertStatement.formatMessageWithSQL("Execution of INSERT statement (%s) failed (Unexpected ROWID; expected: " + boundROWID + "; last inserted ROWID: " + rowID + "; found matching record with ROWID: " + getROWID(record) + ")"));
			}
			else
			{
				// If needed, check whether INSERT really happened:
				if(insertStatement.mustLastInsertBeVerified() && !isRecordInDB(record))
					throw new DBException(insertStatement.formatMessageWithSQL("Execution of INSERT statement (%s) failed: record not found (last inserted ROWID: " + rowID + ")"));
				
				// If needed, set auto-incrementing PK value:
				if(schema.getAutoIncrementingPrimaryKeyColumn() != null)
				{
					try
					{
						schema.getAutoIncrementingPrimaryKeyColumn().storeValue(record, Long.valueOf(rowID)); // value will be validated
					}
					catch(InvalidValueException ive)
					{	// this means the PK value chosen by SQLite does not fit in the bounds of the corresponding Sapelli column 
						rollbackTransactions(); // Undo the INSERT!
						throw new DBPrimaryKeyException(insertStatement.formatMessageWithSQL("Execution of INSERT statement (%s) failed: auto-incrementing PK value invalid"), ive);
					}
					// AutoIncr value was valid, commit transaction:
					commitTransaction();
				}
			}
		}

		/**
		 * Note:
		 * 	Currently the detection of _actual_ changes to the record does *not* work.
		 * 	If the UPDATE statement's WHERE clause matches an existing row that row will (at least in SQLite) be considered
		 * 	as changed/affected (i.e. this method will return true) even if the actual values remained unchanged.
		 * 	The only obvious way to fix this is to generate UPDATE statements in which the WHERE clause checks whether
		 * 	values _need_ to be updated. E.g. "UPDATE table SET col1 = "newVal1", col2 = "newVal2" WHERE id = X AND (col1 IS NOT 'newVal1' OR col2 IS NOT 'newVal2');"
		 * 	We could implement such a solution in RecordUpdateHelper but will wait until we are certain this feature will be required.
		 * @see See: <a href="http://stackoverflow.com/questions/26372449">http://stackoverflow.com/questions/26372449</a>
		 * TODO implement solution to detect actual record value changes
		 * 
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#update(uk.ac.ucl.excites.sapelli.storage.model.Record)
		 */
		@Override
		public synchronized boolean update(Record record) throws DBConstraintException, DBException
		{
			// Get/recycle statement... 
			SQLiteStatement updateStatement = updateStatementHandle.getStatement();
			
			// Bind parameters:
			updateStatement.retrieveAndBindAll(record);
			
			// Execute:
			return updateStatement.executeUpdate() == 1;
		}
		
		public synchronized void upsert(Record record) throws DBException
		{
			// TODO first read http://stackoverflow.com/questions/3634984/insert-if-not-exists-else-update 
			// and http://stackoverflow.com/questions/418898/sqlite-upsert-not-insert-or-replace
		}

		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#delete(uk.ac.ucl.excites.sapelli.storage.model.RecordValueSet)
		 */
		@Override
		public synchronized boolean delete(RecordValueSet<?> recordOrReference) throws DBException
		{
			// Get/recycle statement... 
			SQLiteStatement deleteStatement = deleteStatementHandle.getStatement();

			// Bind parameters:
			deleteStatement.retrieveAndBindAll(recordOrReference);
			
			// Execute:
			return deleteStatement.executeDelete() == 1;
		}
		
		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#delete(uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery)
		 */
		public synchronized int delete(RecordsQuery query) throws DBException
		{
			RecordsDeleteHelper deleteHelper = new RecordsDeleteHelper(this, query);
			SQLiteStatement deleteByQStatement = generateStatement(deleteHelper.getQuery(), deleteHelper.getParameterColumns());
			
			// Bind parameters:
			deleteByQStatement.bindAll(deleteHelper.getSapArguments());
			
			// Execute:
			int affected = deleteByQStatement.executeDelete();
			
			// Close statement:
			deleteByQStatement.close();
			
			// Return number of affected rows:
			return affected;
		}

		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#executeRecordSelection(uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.RecordValueSetSelectHelper)
		 */
		@Override
		protected <R extends RecordValueSet<?>> List<R> executeRecordSelection(RecordValueSetSelectHelper<R> recordValueSetSelectHelper) throws DBException
		{
			SQLiteCursor cursor = null;
			try
			{
				// Execute query (also binds parameters) to get cursor:
				cursor = executeQuery(recordValueSetSelectHelper.getQuery(), recordValueSetSelectHelper.getParameterColumns(), recordValueSetSelectHelper.getSapArguments());
				// Deal with cursor:
				if(cursor == null || !cursor.hasRow())
					// No results:
					return Collections.<R> emptyList();
				else
				{	// Process cursor rows and create corresponding records:
					List<R> result = new ArrayList<R>();
					while(cursor.moveToNext())
					{
						R recordOrReference = recordValueSetSelectHelper.projection.createRecordValueSet();
						int i = 0;
						for(SQLiteColumn<?, ?> sqliteCol : recordValueSetSelectHelper.projection.getProjectionColumns())
							sqliteCol.store(recordOrReference, cursor, i++);
						result.add(recordOrReference);
					}
					return result;
				}
			}
			finally
			{
				if(cursor != null)
					cursor.close(); // !!!
			}
		}
		
		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable#getRecordCount()
		 */
		@Override
		public synchronized long getRecordCount() throws DBException
		{
			return countStatementHandle.getStatement().executeLongQuery();
		}
		
		@Override
		public synchronized void release()
		{
			ROWIDStatementHandle.close();
			insertStatementHandle.close();
			updateStatementHandle.close();
			deleteStatementHandle.close();
			countStatementHandle.close();
		}
		
		/**
		 * Helper class to instantiate and hold on to SQLiteStatements
		 * 
		 * @author mstevens
		 */
		//@SuppressWarnings("rawtypes")
		protected abstract class StatementHandle implements Closeable
		{
			
			//private final Class<? extends SQLRecordStore.StatementHelper> helperClass;
			private SQLiteStatement statement;
			
			/*
			 * @param helperClass Class of a StatementHelper subtype which (a) is contained in SQLiteRecordStore or SQLRecordStore, and (b) which has a constructor that takes only a SQL(ite)Table and creates a parameterised StatementHelper instance, if {@code null} is passed the getHelper() method must be overridden instead
			 */
			/*public StatementHandle(Class<? extends SQLRecordStore.StatementHelper> helperClass)
			{
				this.helperClass = helperClass;
			}*/
			
			//@SuppressWarnings("unchecked")
			protected abstract StatementHelper getHelper();
			//{
			//	return ClassHelpers.callFittingConstructor(helperClass, /*(a) containing SRS object:*/ SQLiteRecordStore.this, /*(b) table:*/ SQLiteTable.this);
			//}
			
			public SQLiteStatement getStatement() throws DBException
			{
				if(statement == null)
				{
					StatementHelper helper = getHelper();
					statement = generateStatement(helper.getQuery(), helper.getParameterColumns());
				}
				else
					statement.clearAllBindings(); // clear bindings for reuse
				// Return:
				return statement;
			}

			@Override
			public void close()
			{
				if(statement != null)
				{
					statement.close();
					statement = null;
				}
			}
			
		}
		
	}
	
	/**
	 * @author mstevens
	 *
	 * @param <SQLType>
	 * @param <SapType>
	 */
	public abstract class SQLiteColumn<SQLType, SapType> extends SQLRecordStore<SQLiteRecordStore, SQLiteRecordStore.SQLiteTable, SQLiteRecordStore.SQLiteColumn<?, ?>>.SQLColumn<SQLType, SapType>
	{

		/**
		 * @param name may be null only if sourceColumnPointer is not
		 * @param type
		 * @param sourceColumnPointer
		 * @param mapping - may be null in case SQLType = SapType
		 */
		public SQLiteColumn(String name, String type, ColumnPointer<? extends Column<SapType>> sourceColumnPointer, TypeMapping<SQLType, SapType> mapping)
		{
			super(name, type, sourceColumnPointer, mapping);
		}
		
		/**
		 * @param statement
		 * @param paramIdx
		 * @param recordOrReference
		 * @throws DBException
		 */
		public void retrieveAndBind(SQLiteStatement statement, int paramIdx, RecordValueSet<?> recordOrReference) throws DBException
		{
			bind(statement, paramIdx, retrieve(recordOrReference));
		}
		
		/**
		 * @param statement
		 * @param paramIdx
		 * @param sapValue
		 * @throws DBException
		 */
		public void bindSapelliObject(SQLiteStatement statement, int paramIdx, Object sapValue) throws DBException
		{
			bind(statement, paramIdx, sapelliOjectToSQL(sapValue));
		}
		
		/**
		 * @param statement
		 * @param paramIdx
		 * @param value
		 * @throws DBException
		 */
		public void bind(SQLiteStatement statement, int paramIdx, SQLType value) throws DBException
		{
			if(value != null)
				bindNonNull(statement, paramIdx, value);
			else
				statement.bindNull(paramIdx);
		}
		
		/**
		 * @param statement
		 * @param paramIdx
		 * @param value
		 * @throws DBException
		 */
		protected abstract void bindNonNull(SQLiteStatement statement, int paramIdx, SQLType value) throws DBException;
		
		/**
		 * @param recordOrReference
		 * @param cursor
		 * @param columnIdx
		 * @throws DBException
		 */
		public void store(RecordValueSet<?> recordOrReference, SQLiteCursor cursor, int columnIdx) throws DBException
		{
			store(recordOrReference, getValueOrNull(cursor, columnIdx));
		}
		
		/**
		 * @param cursor
		 * @param columnIdx
		 * @return
		 * @throws DBException
		 */
		public SQLType getValueOrNull(SQLiteCursor cursor, int columnIdx) throws DBException
		{
			if(cursor.isNull(columnIdx))
				return null;
			return getValue(cursor, columnIdx);
		}
		
		/**
		 * @param cursor
		 * @param columnIdx
		 * @return
		 * @throws DBException
		 */
		protected abstract SQLType getValue(SQLiteCursor cursor, int columnIdx) throws DBException;
		
	}
	
	/**
	 * 
	 * @author mstevens
	 */
	protected class SQLiteTableFactory extends BasicTableFactory
	{
		
		@Override
		protected SQLiteTable createTable(Schema schema) throws DBException
		{
			return new SQLiteTable(schema);
		}

		/**
		 * Here we detect if the table has a primary key column which serves as an implicit alias for the ROWID column.
		 * 
		 * From the SQLite documentation:
		 * 	'[...] if a rowid table has a primary key that consists of a single column and the declared type of that column is "INTEGER"
		 * 	in any mixture of upper and lower case, then the column becomes an alias for the rowid'

		 * @see https://www.sqlite.org/lang_createtable.html#rowid
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.BasicTableFactory#postProcessiong(uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.SQLTable)
		 */
		@Override
		protected void postProcessiong(SQLiteTable table) throws DBException
		{
			PrimaryKey pk = table.schema.getPrimaryKey();
			if(!pk.isMultiColumn())
			{
				SQLiteColumn<?, ?> pkSQLiteColumn = table.getSQLColumn(pk.getColumns(false).get(0));
				if(pkSQLiteColumn.type.equalsIgnoreCase(SQLiteIntegerColumn.SQLITE_DATA_TYPE))
					table.rowidAliasColumn = (SQLiteIntegerColumn<?>) pkSQLiteColumn;
			}
		}

		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.BasicTableFactory#addBoolColForAllOptionalValueSetCol(uk.ac.ucl.excites.sapelli.storage.util.ColumnPointer, uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.TypeMapping)
		 */
		@Override
		protected <VS extends ValueSet<CS>, CS extends ColumnSet> void addBoolColForAllOptionalValueSetCol(ColumnPointer<? extends Column<VS>> sourceColumnPointer, TypeMapping<Boolean, VS> mapping)
		{
			table.addColumn(
				new SQLiteBooleanColumn<VS>(SQLiteRecordStore.this, sourceColumnPointer, mapping)
				{
					@Override
					public boolean isBoolColForAllOptionalValueSetCol()
					{
						return true;
					}
				});
		}
		
		/* (non-Javadoc)
		 * @see uk.ac.ucl.excites.sapelli.storage.visitors.ColumnVisitor#visit(uk.ac.ucl.excites.sapelli.storage.model.columns.BooleanColumn)
		 * @see Section 1.1 in http://www.sqlite.org/datatype3.html
		 */
		@Override
		public void visit(BooleanColumn boolCol)
		{
			table.addColumn(new SQLiteBooleanColumn.Simple(SQLiteRecordStore.this, getColumnPointer(boolCol)));
		}
		
		@Override
		public void visit(final TimeStampColumn timeStampCol)
		{
			table.addColumn(new SQLiteStringColumn<TimeStamp>(SQLiteRecordStore.this, getColumnPointer(timeStampCol), new TypeMapping<String, TimeStamp>()
			{

				@Override
				public String toSQLType(TimeStamp value)
				{
					return timeStampCol.toString(value);
				}

				@Override
				public TimeStamp toSapelliType(String value)
				{
					return timeStampCol.parse(value);
				}
				
			}));
		}
		
		@Override
		public void visit(ByteArrayColumn byteArrayCol)
		{
			table.addColumn(new SQLiteBlobColumn<byte[]>(SQLiteRecordStore.this, getColumnPointer(byteArrayCol), null));
		}
		
		@Override
		public void visit(StringColumn stringCol)
		{
			table.addColumn(new SQLiteStringColumn<String>(SQLiteRecordStore.this, getColumnPointer(stringCol), null));
		}
		
		@Override
		public void visit(IntegerColumn intCol)
		{
			table.addColumn(new SQLiteIntegerColumn<Long>(SQLiteRecordStore.this, getColumnPointer(intCol), null));
		}
		
		@Override
		public void visit(FloatColumn floatCol)
		{
			table.addColumn(new SQLiteDoubleColumn<Double>(SQLiteRecordStore.this, getColumnPointer(floatCol), null));
		}
		
		/**
		 * We store the values of ListColumns as either String or BLOBs, based on the {@code useStringBasedColumn} parameter.
		 * (Later we may change this by solution based on normalisation.)
		 * 
		 * @see uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.BasicTableFactory#visitListColumn(uk.ac.ucl.excites.sapelli.storage.model.ListColumn, boolean)
		 */
		@Override
		public <L extends List<T>, T> void visitListColumn(final ListColumn<L, T> listCol, boolean useStringBasedColumn)
		{
			if(useStringBasedColumn)
			{	// use SQLiteStringColumn
				table.addColumn(new SQLiteStringColumn<L>(SQLiteRecordStore.this, getColumnPointer(listCol), new TypeMapping<String, L>()
				{
					@Override
					public String toSQLType(L value)
					{
						try
						{
							return listCol.toString(value, true);
						}
						catch(Exception e)
						{
							client.logError("Error upon converting from List to String", e);
							return null;
						}
					}
	
					@Override
					public L toSapelliType(String value)
					{
						try
						{
							return listCol.parse(value, true);
						}
						catch(Exception e)
						{
							client.logError("Error upon converting from String to List", e);
							return null;
						}
					}
				}));
			}
			else
			{	// use SQLiteBlobColumn:
				table.addColumn(new SQLiteBlobColumn<L>(SQLiteRecordStore.this, getColumnPointer(listCol), new TypeMapping<byte[], L>()
				{
					@Override
					public byte[] toSQLType(L value)
					{
						try
						{
							return listCol.toBytes(value, true);
						}
						catch(Exception e)
						{
							client.logError("Error upon converting from List to byte[]", e);
							return null;
						}
					}
	
					@Override
					public L toSapelliType(byte[] value)
					{
						try
						{
							return listCol.fromBytes(value, true);
						}
						catch(Exception e)
						{
							client.logError("Error upon converting from byte[] to List", e);
							return null;
						}
					}
				}));
			}
		}

	}
	
	/**
	 * 
	 * @author mstevens
	 * @see http://www.sqlite.org/lang_createtable.html
	 */
	protected class SQLiteTableCreationHelper extends TableCreationHelper
	{

		public SQLiteTableCreationHelper(SQLiteTable table)
		{
			super(table);
		}

		@Override
		protected String getColumnConstraint(SQLiteColumn<?, ?> sqlCol, List<Index> indexesToProcess)
		{
			ColumnPointer<?> sourceCP = sqlCol.sourceColumnPointer;
			
			TransactionalStringBuilder bldr = new TransactionalStringBuilder(SPACE);
		
			// Primary key & unique indexes:
			Iterator<Index> idxIter = indexesToProcess.iterator();
			while(idxIter.hasNext()) // we use an iterator instead of a for-each loop to allow save remove of items during iteration
			{
				Index idx = idxIter.next();
				if(idx.containsColumn(sourceCP.getColumn()) && !idx.isMultiColumn())
				{	// the sourceColumn is indexed (on its own) ...
					bldr.openTransaction();
					// Check which kind of index it is:
					if(idx == table.schema.getPrimaryKey())
					{	// the sourceColumn is the primary key
						bldr.append("PRIMARY KEY");
						// ASC/DESC?
						// conflict-clause?
						if(idx instanceof AutoIncrementingPrimaryKey)
							bldr.append("AUTOINCREMENT");
					}
					else if(idx.isUnique())
					{	//Regular single-column, unique index (unnamed)
						bldr.append("UNIQUE");
						// conflict-clause?
					}
					// else : index will have to be created separately (outside of column & table definition)
					
					if(!bldr.isCurrentTransactionEmpty())
					{
						bldr.commitTransaction();
						// We are done processing this index:
						idxIter.remove();
					}
					else
						bldr.rollbackTransaction();
				}
			}

			// Optionality:
			boolean optional = false;
			ColumnPointer<?> cp = sourceCP;
			while(cp != null)
				cp = (optional = cp.getColumn().optional) ? null : cp.getParentPointer();
			//	If the (sub)column is not optional, and neither is one of its parents, then the DB column should not accept null values:
			if(!optional)
				bldr.append("NOT NULL");
			// TODO Reassess this: it could be a bad idea because it means we cannot persistently store incomplete records (which was no problem with DB4O).
			
			// TODO Default value? Currently only handled at Java level.
			
			// foreign-key-clause?
			/*if(sourceColum instanceof ForeignKeyColumn)
			{
				// ...
			}*/
			
			return bldr.toString();
		}

		@Override
		protected void addTableConstraints(List<Index> indexesToProcess)
		{
			// Primary key & unique indexes:
			Iterator<Index> idxIter = indexesToProcess.iterator();
			while(idxIter.hasNext()) // we use an iterator instead of a for-each loop to allow save remove of items during iteration
			{
				Index idx = idxIter.next();
				TransactionalStringBuilder bldr = new TransactionalStringBuilder(SPACE);
				// Check which kind of index it is:
				if(idx == table.schema.getPrimaryKey())
					// index is the primary key:
					bldr.append("PRIMARY KEY");
				else if(idx.isUnique())
					// index is a regular, unique index:
					bldr.append("UNIQUE");
				else
					// index is a not the primary key, nor unique, these cannot be created as part of the SQLite table definition
					continue; // will be created separately from the table
				
				// Continue generating constraint definition for primary key or unique index:
				bldr.append("(");
				bldr.openTransaction(", ");
				// List indexed columns:
				for(Column<?> idxCol : idx.getColumns(false))
					// idxCol may be a composite (like a ForeignKeyColumn), so loop over each SQLiteColumn that represents part of it:
					for(SQLiteColumn<?, ?> idxSCol : table.getSQLColumns(idxCol))
						bldr.append(idxSCol.sanitisedName);
				bldr.commitTransaction(false);
				bldr.append(")", false);
				// conflict-clause?
				
				// Add constraint:
				tableConstraints.add(bldr.toString());
				
				// We are done processing this index:
				idxIter.remove(); // !!!
			}
			
			// foreign-key-clause?
		}
		
	}
	
	/**
	 * A {@link SelectHelper} class for the execution of SELECT ROWID queries.
	 * 
	 * @author mstevens
	 */
	private class SelectROWIDHelper extends SelectHelper<SelectProjection>
	{
		
		/**
		 * @param table
		 */
		public SelectROWIDHelper(SQLiteTable table)
		{
			super(	table,
					new SelectProjection()
					{
						@Override
						public String getProjectionString()
						{
							return "ROWID";
						}
					},
					new RecordsQuery(Source.From(table.schema), table.schema.getBlankPKConstraints()));
		}
		
	}
	
}
