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


import static org.jdbdt.JDBDT.*;
import static org.jdbdt.TestUtil.*;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static org.junit.Assert.*;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

@SuppressWarnings("javadoc")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class QueryBuilderTest extends DBTestCase { 

  @Test
  public void testExecPlain() {
    DataSource ds = 
      select(UserDAO.COLUMNS)
      .from(UserDAO.TABLE_NAME)
      .build(getDB());
    DataSet actual = executeQuery(ds);
    DataSet expected = 
        data(ds, getConversion())
        .rows(INITIAL_DATA);
    assertDataSet(expected, actual);
  }

  @Test
  public void testExecWhere() throws SQLException {
    User u = getDAO().query(EXISTING_DATA_ID1);
    DataSource ds = 
     select(UserDAO.COLUMNS)
      .from(UserDAO.TABLE_NAME)
      .where("login='" + EXISTING_DATA_ID1 + "'")
      .build(getDB());
    DataSet actual = executeQuery(ds);
    DataSet expected = data(ds, getConversion()).row(u);
    assertDataSet(expected, actual);
  }


  @Test
  public void testExecWhereWithArgs() throws SQLException {
    User u = getDAO().query(EXISTING_DATA_ID1);
    DataSource ds = 
      select( UserDAO.COLUMNS)
      .from(UserDAO.TABLE_NAME)
      .where("login=?")
      .arguments(EXISTING_DATA_ID1)
      .build(getDB());
    DataSet actual = 
        executeQuery(ds);     
    DataSet expected = 
        data(ds, getConversion())
        .row(u);
    assertDataSet(expected, actual);
  }
  @Test
  public void testExecColumns1() throws SQLException {
    User u = getDAO().query(EXISTING_DATA_ID1);
    Query q =
        select("password")
        .from(UserDAO.TABLE_NAME)
        .where("login=?")
        .arguments(EXISTING_DATA_ID1)
        .build(getDB());
    DataSet actual = executeQuery(q);
    DataSet expected = 
        data(q)
        .row(u.getPassword());
    assertDataSet(expected, actual);
  }

  @Test
  public void testExecColumns2() throws SQLException {
    User u = getDAO().query(EXISTING_DATA_ID1);
    Query q =
        select("password", "name")
        .from(UserDAO.TABLE_NAME)
        .where("login=?")
        .arguments(EXISTING_DATA_ID1)
        .build(getDB());
    DataSet actual = 
      executeQuery(q);
    DataSet expected = 
        data(q)
        .row(u.getPassword(), u.getName());
    assertDataSet(expected, actual);
  }

  @Test
  public void testExecWithDistinct1() {
    Query q =
        select(UserDAO.COLUMNS)
        .distinct()
        .from(UserDAO.TABLE_NAME)
        .build(getDB());
    DataSet actual = executeQuery(q);
    DataSet expected = 
        data(q, getConversion())
        .rows(INITIAL_DATA);
    assertDataSet(expected, actual);
  }

  @Test
  public void testExecWithDistinct2() {
    Query q =
        select("password")
        .distinct()
        .from(UserDAO.TABLE_NAME)
        .build(getDB());
    DataSet actual = 
     executeQuery(q);
    HashSet<String> distinctPass = new HashSet<>();
    DataSet expected = data(q);
    for (User u : INITIAL_DATA) {
      if (distinctPass.add(u.getPassword())) {
        expected.row(u.getPassword());
      }
    }
    assertDataSet(expected, actual);
  }

  
  @Test
  public void testExecWithOrderBy1() {
    Query q =
        select(UserDAO.COLUMNS)
        .from(UserDAO.TABLE_NAME)
        .orderBy("login")
        .build(getDB());
    DataSet actual = executeQuery(q);
    User[] sortedUsers = INITIAL_DATA.clone();
    Arrays.sort(sortedUsers, 
                (a,b) -> a.getLogin().compareTo(b.getLogin()));
    DataSet expected = data(q, getConversion()).rows(sortedUsers);
    assertTrue(expected.sameDataAs(actual));
  }

  @Test
  public void testExecWithOrderBy2() {
    Query q =
        select(UserDAO.COLUMNS)
        .from(UserDAO.TABLE_NAME)
        .orderBy("password", "login")
        .build(getDB());
    DataSet actual = executeQuery(q);
    User[] sortedUsers = INITIAL_DATA.clone();
    Arrays.sort(sortedUsers, 
        (a,b) -> {
          int cmp = a.getPassword().compareTo(b.getPassword());
          if (cmp == 0) {
            cmp = a.getLogin().compareTo(b.getLogin());
          }
          return cmp;
        });
    DataSet expected = 
        data(q, getConversion())
        .rows(sortedUsers);
    assertTrue(expected.sameDataAs(actual));
  }

  <T extends Number> 
  DataSet passCount(DataSource ds, T zero, Function<T,T> incr, BiPredicate<String,T> pred) {
    DataSet expected = data(ds);
    HashMap<String,T> count = new HashMap<>();
    for (User u : INITIAL_DATA) {
      count.put(u.getPassword(), 
          incr.apply(count.getOrDefault(u.getPassword(),zero)));
    }
    for (Entry<String,T> e : count.entrySet()) {
      if (pred.test(e.getKey(), e.getValue())) {
        expected.row(e.getKey(), e.getValue());
      }
    }
    return expected;
  }
  
  @Test
  public void testExecWithGroupBy1() {
    Query q = select("password","count(*)")
        .from(UserDAO.TABLE_NAME)
        .groupBy("password")
        .build(getDB());
    
    DataSet expected = 
        DBConfig.getConfig().doesCountReturnAnInteger() ?
              passCount(q, 0, x -> x + 1, (p,n) -> true) 
            :
              passCount(q, 0L, x -> x + 1L, (p,n) -> true);
    DataSet actual = executeQuery(q);
    assertDataSet(expected, actual);
  }
  
  @Test
  public void testExecWithGroupBy2() {
    Query q = select("password","count(*)")
        .from(UserDAO.TABLE_NAME)
        .groupBy("password")
        .having("count(*) > 1")
        .build(getDB());
    
    DataSet expected = 
        DBConfig.getConfig().doesCountReturnAnInteger() ?
              passCount(q, 0, x -> x + 1, (p,n) -> n > 1) 
            :
              passCount(q, 0L, x -> x + 1L, (p,n) -> n > 1);
    DataSet actual = executeQuery(q);
    assertDataSet(expected, actual);
  }
  
  @Test
  public void testExecWithMultipleSources() {
    Query q =
        select("u1.LOGIN", "u2.LOGIN")
        .from(UserDAO.TABLE_NAME + " u1", UserDAO.TABLE_NAME + " u2" )
        .where("u1.login <> u2.login AND u1.PASSWORD = u2.PASSWORD")
        .build(getDB());

    DataSet expected = data(q);
    for (int i=0; i < INITIAL_DATA.length; i++) {
      User a = INITIAL_DATA[i];
      for (int j=i+1; j < INITIAL_DATA.length; j++) {
        User b = INITIAL_DATA[j];
        if (a.getPassword().equals(b.getPassword())) {
          expected.row(a.getLogin(), b.getLogin())
                  .row(b.getLogin(), a.getLogin());
        }
      }
    }
    DataSet actual = executeQuery(q);
    assertDataSet(expected, actual);
  }

  @Test @Category(TestCategories.SupportForLimit.class)
  public void testExecWithOrderByAndLimit() {
    Query q =
        select(UserDAO.COLUMNS)
        .from(UserDAO.TABLE_NAME)
        .orderBy("login")
        .limit(2)
        .build(getDB());
    DataSet actual = executeQuery(q);
    User[] sortedUsers = INITIAL_DATA.clone();
    Arrays.sort(sortedUsers, 
        (a,b) -> a.getLogin().compareTo(b.getLogin()));
    DataSet expected = 
        data(q, getConversion())
        .rows(sortedUsers[0], sortedUsers[1]);
    assertDataSet(expected, actual);
    assertTrue(expected.sameDataAs(actual));
  }
}
