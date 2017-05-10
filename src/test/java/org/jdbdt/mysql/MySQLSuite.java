package org.jdbdt.mysql;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.jdbdt.DBConfig;
import org.jdbdt.DBEngineTestSuite;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.mysql.management.MysqldResource;
import com.mysql.management.MysqldResourceI;

@SuppressWarnings("javadoc")
public class MySQLSuite extends DBEngineTestSuite {

  @BeforeClass 
  public static void setup() throws ClassNotFoundException {
    DBConfig.getConfig()
      .reset()
      .setDriver("com.mysql.jdbc.Driver")
      .setURL(startDatabase());
  }
  
  @AfterClass
  public static void teardown() {
    stopDatabase();
  }
  
  private static final File   DB_PATH = new File("mysql");
  private static final int    DB_PORT = 9999;
  private static final String DB_USER = "jdbdt";
  private static final String DB_PASS = "jdbdt";
  
  private static MysqldResource engine;
  
  private static String startDatabase() {

    Map<String,String> args = new HashMap<>();
    args.put(MysqldResourceI.PORT, Integer.toString(DB_PORT));
    args.put(MysqldResourceI.INITIALIZE_USER, "true");
    args.put(MysqldResourceI.INITIALIZE_USER_NAME, DB_USER);
    args.put(MysqldResourceI.INITIALIZE_PASSWORD, DB_PASS);
    
    engine = new MysqldResource(DB_PATH);
    engine.start("jdbdt-mysqld-thread", args);
    
    if (!engine.isRunning()) {
      throw new RuntimeException("MySQL did not start.");
    }
    
    return String.format
    (
      "jdbc:mysql://127.0.0.1:%d/jdbdt?user=%s&password=%s&createDatabaseIfNotExist=true", 
      DB_PORT, 
      DB_USER, 
      DB_PASS
    );
  }
  
  private static void stopDatabase() {
    engine.shutdown();
  }
}