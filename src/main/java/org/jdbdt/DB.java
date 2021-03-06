/*
 * The MIT License
 *
 * Copyright (c) Eduardo R. B. Marques
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.jdbdt;

import java.io.File;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Database handle.
 * 
 * <p>
 * An object of this kind is used to encapsulate access
 * to a database connection.
 * </p>
 * 
 * @see JDBDT#database(Connection)
 * @since 1.0
 *
 */
public final class DB {
  /**
   * Database options.
   *
   * @since 1.0
   */
  public enum Option {
    /**
     * Log assertions (both failed and passed).
     */
    LOG_ASSERTIONS,
    
    /**
     * Log failed assertions (enabled initially by default).
     */
    LOG_ASSERTION_ERRORS,
    
    /**
     * Log database exceptions (enabled initially by default).
     * 
     * @since 1.1
     */
    LOG_DATABASE_EXCEPTIONS,
    
    /**
     * Log database queries. 
     */
    LOG_QUERIES,
    
    /**
     * Log database setup operations.
     */
    LOG_SETUP,
    
    /**
     * Log database snapshots. 
     */
    LOG_SNAPSHOTS,
    
    /**
     * Reuse statements (enabled initially by default).
     */
    REUSE_STATEMENTS,
    
    /**
     * Batch updates (enabled initially by default).
     * 
     * This should provide better performance for row insertion when using
     * {@link JDBDT#insert(DataSet)} and {@link JDBDT#populate(DataSet)}.
     * The option has no effect though if the database driver does not support batch
     * updates (as indicated by {@link DatabaseMetaData#supportsBatchUpdates()}.
     */
    BATCH_UPDATES,
    
    /**
     * Handle column names in case-sensitive manner.
     * This is required if you wish to use double-quotes with column 
     * names, by default JDBDT will handle column names in case-insensitive 
     * manner. This support may be fragile for some database engines.
     * 
     * @since 1.1
     */
    CASE_SENSITIVE_COLUMN_NAMES;
  }

  /**
   * Connection.
   */
  private final Connection connection;

  /**
   * Trace options.
   */
  private final EnumSet<Option> optionSet = EnumSet.noneOf(Option.class);

  /**
   * Flag indicating if batch updates are supported.
   */
  private final boolean batchUpdateSupport;

  /**
   * Flag indicating if save points are supported.
   */
  private final boolean savepointSupport;

  /**
   * Log to use. 
   */
  private Log log = null;

  /**
   * Statement pool.
   */
  private Map<String, WrappedStatement> pool;

  /**
   * Savepoint (non-null if set).
   */
  private Savepoint savepoint;

  /**
   * Maximum operations for batch updates.
   */
  private int maxBatchUpdateSize = 0;

  /**
   * Default value for maximum batch update size (if batch updates enabled).
   * @see #setMaximumBatchUpdateSize(int)
   * @see #getMaximumBatchUpdateSize()
   * @see DB.Option#BATCH_UPDATES
   */
  public static final int DEFAULT_MAX_BATCH_UPDATE_SIZE = 1000;

  /**
   * Constructor.
   * @param connection Database connection.
   */
  public DB(Connection connection) {
    try {
      this.connection = connection;
      DatabaseMetaData dbMetaData = connection.getMetaData();
      log = Log.create(System.err);
      enable(Option.REUSE_STATEMENTS, 
          Option.LOG_ASSERTION_ERRORS,
          Option.LOG_DATABASE_EXCEPTIONS);

      batchUpdateSupport = dbMetaData.supportsBatchUpdates();
      savepointSupport = dbMetaData.supportsSavepoints();

      if (batchUpdateSupport) {
        maxBatchUpdateSize = DEFAULT_MAX_BATCH_UPDATE_SIZE;
        enable(Option.BATCH_UPDATES);
      } else {
        maxBatchUpdateSize = 0;
      }
    } catch (SQLException e) {
      throw new DBExecutionException(e);
    }
  }

  /**
   * Enable options.
   * @param options Options to enable.
   */
  @SafeVarargs
  public final void enable(Option... options) {
    for (Option o : options) {
      optionSet.add(o);
    }
  }

  /**
   * Set maximum size for batch updates.
   * @param size The size to set.
   * @see #getMaximumBatchUpdateSize()
   * @see #DEFAULT_MAX_BATCH_UPDATE_SIZE
   * @see DB.Option#BATCH_UPDATES
   */
  public void setMaximumBatchUpdateSize(int size) {
    if (!batchUpdateSupport) {
      throw new InvalidOperationException("Batch updates not allowed by database driver.");
    }
    if (! isEnabled(DB.Option.BATCH_UPDATES)) {
      throw new InvalidOperationException(DB.Option.BATCH_UPDATES + " option is not enabled.");
    }
    if (size < 1) {
      throw new InvalidOperationException("Invalid batch update size: " + size);
    }
    maxBatchUpdateSize = size;
  }

  /**
   * Get current setting for maximum batch update size.
   * @return The value set, which will be 0 if batch updates are not supported
   * by the database driver.
   * @see #setMaximumBatchUpdateSize(int)
   * @see #DEFAULT_MAX_BATCH_UPDATE_SIZE
   * @see DB.Option#BATCH_UPDATES
   */
  public int getMaximumBatchUpdateSize() {
    return batchUpdateSupport ? maxBatchUpdateSize : 0;
  }

  /** 
   * Enable all logging options.
   */
  public void enableFullLogging() {
    enable(DB.Option.LOG_ASSERTION_ERRORS,
        DB.Option.LOG_ASSERTIONS,
        DB.Option.LOG_SETUP,
        DB.Option.LOG_QUERIES,
        DB.Option.LOG_SNAPSHOTS,
        DB.Option.LOG_DATABASE_EXCEPTIONS);
  }

  /**
   * Disable options.
   * @param options Options to enable.
   * @see #enable(Option...)
   * @see #isEnabled(Option)
   */
  @SafeVarargs
  public final void disable(Option... options) {
    for (Option o : options) {
      optionSet.remove(o);
    }
  }


  /**
   * Check if option is enabled.
   * @param o Option.
   * @return <code>true</code> if <code>o</code> is enabled.
   * @see #enable(Option...)
   * @see #disable(Option...)
   */
  public boolean isEnabled(Option o) {
    return optionSet.contains(o);
  }

  /**
   * Get connection.
   * @return The connection associated to this instance.
   */
  public Connection getConnection() {
    return connection;
  }

  /**
   * Redirect log output to a stream.
   * The log set at creation time
   * writes to <code>System.err</code>.
   * @param out Output stream.
   */
  public void setLog(PrintStream out) {
    this.log = Log.create(out);
  }

  /**
   * Set output file for log output.
   * 
   * <p>
   * The output file will be GZIP-compressed if it has a <code>.gz</code> extension.
   * </p>
   * 
   * <p>Note that the log output set at creation time
   * is <code>System.err</code>.</p>
   * @param outputFile Logging instance.
   */
  public void setLog(File outputFile) {
    this.log = Log.create(outputFile);
  }

  /**
   * Set auto-commit mode for the underlying database connection.
   *
   * <p>
   * A call to this method is equivalent to <code>getConnection().setAutoCommit(enable)</code>.
   * with the sole difference than any thrown {@link java.sql.SQLException} 
   * (a checked exception) is "wrapped" a
   * {@link DBExecutionException} (an unchecked exception).
   * </p>
   *
   * @param enable Enable/disable auto-commit setting.
   * @throws DBExecutionException if a database exception occurs.
   * 
   * @see #getAutoCommit()
   * @see java.sql.Connection#getAutoCommit()
   * @see java.sql.Connection#setAutoCommit(boolean)
   * @since 1.3
   */
  public void setAutoCommit(boolean enable) throws DBExecutionException {
    try {
      getConnection().setAutoCommit(enable);
    } 
    catch (SQLException e) {
      throw new DBExecutionException(e);
    }
  }

  /**
   * Get auto-commit mode for the underlying database connection. 
   *
   * <p>
   * A call to this method is equivalent to <code>getConnection().getAutoCommit()</code>.
   * with the sole difference than any thrown {@link java.sql.SQLException} 
   * (a checked exception) is "wrapped" a
   * {@link DBExecutionException} (an unchecked exception).
   * </p>
   *
   * @return Auto-commit mode for the database connection.
   * @throws DBExecutionException if a database exception occurs.
   * 
   * @see #setAutoCommit(boolean)
   * @see java.sql.Connection#getAutoCommit()
   * @see java.sql.Connection#setAutoCommit(boolean)
   * @since 1.3
   */
  public boolean getAutoCommit() throws DBExecutionException {
    try {
      return getConnection().getAutoCommit();
    } 
    catch (SQLException e) {
      throw new DBExecutionException(e);
    }
  }

  /**
   * Compile a SQL statement.
   * @param sql SQL code.
   * @return Wrapper for prepared statement.
   * @throws SQLException If there is a error preparing the statement.
   */
  WrappedStatement compile(String sql) throws SQLException {
    WrappedStatement ws;
    if (! isEnabled(Option.REUSE_STATEMENTS)) {
      ws = new WrappedStatement(connection.prepareStatement(sql), false);
    }
    else {
      if (pool == null) {
        pool = new IdentityHashMap<>();
      }
      String sqlI = sql.intern();
      ws = pool.get(sqlI);
      if (ws == null) {
        ws =  new WrappedStatement(connection.prepareStatement(sql), true);
        pool.put(sqlI, ws);
      }
    }
    return ws;
  }

  /**
   * Set JDBDT save-point.
   * @param callInfo Call info.
   */
  void save(CallInfo callInfo) {
    access(callInfo, () -> {
      if (!savepointSupport) {
        throw new UnsupportedOperationException("Savepoints are not supported by the database driver.");
      }
      logSetup(callInfo);
      clearSavePointIfSet();
      if (getAutoCommit()) {
        throw new InvalidOperationException("Auto-commit is set for database connection.");
      }      
      savepoint = connection.setSavepoint();
      return 0;
    });
  }

  @SuppressWarnings("javadoc")
  private void clearSavePointIfSet() {
    if (savepoint != null) {
      ignoreSQLException( () -> connection.releaseSavepoint(savepoint));
      savepoint = null;
    }
  }

  /**
   * Commit changes in the current transaction.
   * @param callInfo Call info.
   */
  void commit(CallInfo callInfo) {
    access(callInfo, () -> {
      logSetup(callInfo);
      clearSavePointIfSet();
      connection.commit();
      return 0;
    });
  }

  /**
   * Roll back changes to JDBDT save-point.
   * @param callInfo Call info.
   */
  void restore(CallInfo callInfo) {
    // Note: this is a conservative implementation, it sets another save-point
    // after roll-back, some engines seem to implicitly release the save point on roll-back
    // (an issue with HSQLDB)
    access(callInfo, () -> {
      logSetup(callInfo);
      try {
        if (!savepointSupport) {
          throw new UnsupportedOperationException("Savepoints are not supported by the database driver.");
        }
        if (savepoint == null) {
          throw new InvalidOperationException("Save point is not set.");
        }
        Savepoint s = savepoint;
        savepoint = null;
        connection.rollback(s);
        return 0;
      }
      finally {
        clearSavePointIfSet();
      }
    });
  }

  /**
   * Tear down the database handle, freeing any internal
   * resources. 
   * @param callInfo Call info.
   * @param closeConn Close underlying connection.
   */
  void teardown(CallInfo callInfo, boolean closeConn) {
    logSetup(callInfo);
    if (pool != null) {
      for (WrappedStatement ws : pool.values()) {
        ignoreSQLException(ws.getStatement()::close);
      }
      pool.clear();
      pool = null;
    }
    clearSavePointIfSet();
    log.close();
    log = null;
    if (closeConn) {
      ignoreSQLException(connection::close);
    }
  }

  /**
   * Functional interface for generic database access.
   * @param <T> Type of result.
   */
  @FunctionalInterface
  interface Access<T> {
    /**
     * Execute some code that accesses the DB.
     * @return Result of executing the operation.
     * @throws SQLException If a database error occurs.
     */
    public T execute() throws SQLException;
  }

  /**
   * Run a operation. 
   * 
   * Any {@link java.sql.SQLException} error that occurs is wrapped
   * into a {@link DBExecutionException} instance.
   * @param <T> Type of result.
   * @param callInfo Call info.
   * @param op Operation.
   * @return DB access result.
   * @throws DBExecutionException If a database error occurs.
   */
  <T> T access(CallInfo callInfo, Access<T> op) {
    try {
      return op.execute();
    }
    catch (SQLException e) {
      if (isEnabled(DB.Option.LOG_DATABASE_EXCEPTIONS)) {
        log.write(callInfo, e);
      }
      throw new DBExecutionException(e); 
    }
  }

  /**
   * Test if batch updates should be performed.
   * @return <code>true</code> if batch updates should be performed.
   */
  boolean useBatchUpdates() {
    return batchUpdateSupport && isEnabled(DB.Option.BATCH_UPDATES); 
  }

  /**
   * Log query result.
   * @param callInfo Call info.
   * @param data Data set.
   */
  void logQuery(CallInfo callInfo, DataSet data) {
    if (isEnabled(Option.LOG_QUERIES)) {
      log.write(callInfo, data);
    }
  }

  /**
   * Log query result.
   * @param callInfo Call info.
   * @param data Data set.
   */
  void logSnapshot(CallInfo callInfo, DataSet data) {
    if (isEnabled(Option.LOG_SNAPSHOTS)) {
      log.write(callInfo, data);
    }
  }

  /**
   * Log insertion.
   * @param callInfo Call info.
   * @param data Data set.
   */
  void logDataSetOperation(CallInfo callInfo, DataSet data) {
    if (isEnabled(Option.LOG_SETUP)) {
      log.write(callInfo, data);
    }
  }

  /**
   * Log delta assertion.
   * @param callInfo Call info.
   * @param da Delta assertion.
   */
  void log(CallInfo callInfo, DeltaAssertion da) {
    if (isEnabled(Option.LOG_ASSERTIONS) ||
        (    ! da.passed() 
            && isEnabled(Option.LOG_ASSERTION_ERRORS) )) {
      log.write(callInfo, da);
    }
  }

  /**
   * Log state assertion.
   * @param callInfo Call info.
   * @param dsa state assertion.
   */
  void log(CallInfo callInfo, DataSetAssertion dsa) {
    if (isEnabled(Option.LOG_ASSERTIONS) ||
        (    ! dsa.passed() 
            && isEnabled(Option.LOG_ASSERTION_ERRORS) )) {
      log.write(callInfo, dsa);
    }
  }

  /**
   * Log simple assertion.
   * @param callInfo Call info.
   * @param sa Simple assertion.
   */
  void log(CallInfo callInfo, SimpleAssertion sa) {
    if (isEnabled(Option.LOG_ASSERTIONS) ||
        (    ! sa.passed() 
            && isEnabled(Option.LOG_ASSERTION_ERRORS) )) {
      log.write(callInfo, sa);
    }
  }

  /**
   * Log database setup command.
   * @param callInfo Call info.
   * @param sql SQL code.
   */
  void logSetup(CallInfo callInfo, String sql) {
    if (isEnabled(Option.LOG_SETUP)) {
      log.writeSQL(callInfo, sql);
    }
  }

  /**
   * Log database setup call.
   * @param callInfo Call info.
   */
  void logSetup(CallInfo callInfo) {
    if (isEnabled(Option.LOG_SETUP)) {
      log.writeCallInfo(callInfo);
    }
  }

  @SuppressWarnings("javadoc")
  @FunctionalInterface
  private interface SQLOperationThatMayFail {
    void run() throws SQLException;
  }

  @SuppressWarnings("javadoc")
  private void ignoreSQLException(SQLOperationThatMayFail op) {
    try {
      op.run();
    }
    catch (SQLException e) { 
      // Do nothing.
    }
  }
}
