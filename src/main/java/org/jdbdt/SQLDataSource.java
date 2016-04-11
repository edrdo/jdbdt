package org.jdbdt;

/**
 * Data source created from arbitrary SQL query statement.
 *
 * @since 0.1 
 *
 */
public final class SQLDataSource extends DataSource {

  /**
   * SQL code for query.
   */
  private final String sqlForQuery; 
  
  /**
   * Query arguments (if any).
   */
  private Object[] queryArgs = null;
  
  /**
   * Constructor.
   * @param db Database handle.
   * @param sql SQL for query
   */
  SQLDataSource(DB db, String sql) {
    super(db);
    this.sqlForQuery = sql;
  }

  /**
   * Set arguments for SQL query.
   * @param args Query arguments.
   * @return The data source instance, for chained calls.
   */
  @SafeVarargs
  public final SQLDataSource withArguments(Object... args) {
    if (queryArgs != null) {
      throw new InvalidOperationException("Query arguments already defined.");
    }
    queryArgs = args; 
    return this;
  }
  
  @Override
  String getSQLForQuery() {
    return sqlForQuery;
  }

  
}
