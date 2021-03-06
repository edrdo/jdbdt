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
import java.sql.Date;
import java.sql.SQLException;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;


/**
 * Parent class for a database test classes.
 * 
 * @since 1.0
 */
@SuppressWarnings("javadoc")
public class DBTestCase {

  private static DB gDB;
  private static UserDAO gDAO;
  private static Conversion<User> gConversion;

  protected static final 
  Conversion<User> STD_CONVERSION = 
  u -> new Object[] {
      u.getLogin(), 
      u.getName(), 
      u.getPassword(), 
      u.getCreated()
  };
  // Conversion when DATE is not supported (e.g. for SQLite)
  protected static final 
  Conversion<User> ALT_CONVERSION = 
  u -> new Object[] {
      u.getLogin(), 
      u.getName(), 
      u.getPassword(), 
      u.getCreated() != null ? u.getCreated().getTime() : null
  };

  protected static Conversion<User> getConversion() {
    return gConversion;
  }
  protected static Object dateValue(Date d) {
    return gConversion == STD_CONVERSION ?  d : d.getTime();
  }

  static int gTestSuiteCounter = 0;

  @BeforeClass
  public static void setupDB() throws Exception {
    DBConfig cfg = DBConfig.getConfig();
    Class.forName(cfg.getDriver());
    gDB = JDBDT.database(cfg.getURL());
    gDB.setAutoCommit(true);
    gDAO = new UserDAO(gDB.getConnection());
    gConversion = cfg.isDateSupported() ? STD_CONVERSION : ALT_CONVERSION;
    if (!cfg.reuseStatements()) {
      gDB.disable(DB.Option.REUSE_STATEMENTS);
    }
    newUserCounter = 0;

    File logDir = new File("jdbdt-log");
    String fileName = String.format("%02d_%s.jdbdt.log.gz",
                                    ++gTestSuiteCounter,
                                    cfg.getDriver());
    logDir.mkdirs();
    gDB.setLog(new File(logDir, fileName));
    gDB.enableFullLogging();
  }

  @AfterClass
  public static void teardownDB() throws SQLException {
    JDBDT.teardown(gDB, true);
    useCustomInit = false;
  }

  protected static DB getDB() {
    return gDB;
  }

  protected static UserDAO getDAO() {
    return gDAO;
  }

  private static boolean useCustomInit = false;

  static void useCustomInit() {
    useCustomInit = true;
  }

  @Before
  public void populateByDefault() throws SQLException {
    if (!useCustomInit) {
      getDAO().doDeleteAll();
      getDAO().doInsert(INITIAL_DATA);
    }
  }


  static final User[] INITIAL_DATA = {
      new User("linus", "Linus Torvalds", "linux", Date.valueOf("2015-01-01")),
      new User("steve", "Steve Jobs", "macos", Date.valueOf("2015-12-31")),
      new User("bill", "Bill Gates", "windows", Date.valueOf("2015-09-12")),
      new User("alanis", "Alanis", "xyz", Date.valueOf("2015-01-01")),
      new User("blanis", "Blanis", "xyz", Date.valueOf("2015-01-02")),
      new User("clanis", "Clanis", "xyz", Date.valueOf("2015-01-03")),
      new User("dlanis", "Dlanis", "xyz", null),
      new User("elanis", "Elanis", "xyz", Date.valueOf("2015-01-05")),
      new User("flanis", "Flanis", "xyz", Date.valueOf("2015-01-06")),
      new User("glanis", "Glanis", "xyz", Date.valueOf("2015-01-07")),
      new User("hlanis", "Hlanis", "xyz", Date.valueOf("2015-01-08")),
      new User("ilanis", "Ilanis", "xyz", Date.valueOf("2015-01-09")),
      new User("jlanis", "Jlanis", "xyz", null)
  };

  protected static final String EXISTING_DATA_ID1 =
      INITIAL_DATA[0].getLogin();

  protected static final String EXISTING_DATA_ID2 =
      INITIAL_DATA[INITIAL_DATA.length/2].getLogin();

  protected static final String EXISTING_DATA_ID3 =
      INITIAL_DATA[INITIAL_DATA.length-1].getLogin();

  protected static User getTestData(String id) {
    for (User u : INITIAL_DATA) {
      if (u.getLogin().equals(id)) {
        return u;
      }
    }
    return null;
  }

  protected Object[] rowFor(User u) {
    return getConversion().convert(u);
  }
  
  protected Object[] rowFor(String id) {
    return getConversion().convert(getTestData(id));
  }
  

  private static int newUserCounter = 0;

  protected static User buildNewUser() {
    final int unique = newUserCounter++;
    return new User("user" + unique, 
        "New User" + unique, 
        "pass" + unique, 
        Date.valueOf("2016-01-01"));
  }

}
