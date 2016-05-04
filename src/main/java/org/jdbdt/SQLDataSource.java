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
   * Constructor.
   * @param db Database handle.
   * @param sql SQL for query
   * @param args Optional arguments for query.
   */
  SQLDataSource(DB db, String sql, Object... args) {
    super(db);
    this.sqlForQuery = sql;
    if (args != null && args.length > 0) {
      super.setQueryArguments(args);
    }
  }
  
  @Override
  String getSQLForQuery() {
    return sqlForQuery;
  } 
}
