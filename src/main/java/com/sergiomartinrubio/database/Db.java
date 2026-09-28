package com.sergiomartinrubio.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/// Throwaway in-memory H2 database for the examples in this package.
///
/// This only hides connection and schema boilerplate. The SQL that carries the lesson
/// (`FOR UPDATE`, `WHERE version = ?`, isolation levels) always stays visible in the
/// example itself.
///
/// Every instance gets its own named in-memory database, so examples never interfere.
public final class Db implements AutoCloseable {

  /// H2 error code for "could not get the lock in time".
  /// Verified against H2 2.3.232: SQLState HYT00.
  public static final int LOCK_TIMEOUT = 50200;

  /// H2 error code for a detected deadlock. The loser is rolled back automatically.
  /// Verified against H2 2.3.232: SQLState 40001.
  public static final int DEADLOCK = 40001;

  private final String url;

  private Db(String url) {
    this.url = url;
  }

  /// Creates a fresh database with an `account` table holding two rows:
  /// id 1 with balance 100, id 2 with balance 200, both at version 0.
  ///
  /// `DB_CLOSE_DELAY=-1` keeps the database alive while no connection is open, which an
  /// in-memory H2 would otherwise discard between connections.
  public static Db accounts(String name) {
    Db db = new Db("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
    db.execute("""
        CREATE TABLE IF NOT EXISTS account (
          id      INT PRIMARY KEY,
          owner   VARCHAR(50) NOT NULL,
          balance INT         NOT NULL,
          version INT         NOT NULL DEFAULT 0
        )""");
    db.reset();
    return db;
  }

  /// Restores the seed data. Handy between scenarios in one example.
  public void reset() {
    execute("DELETE FROM account");
    execute("""
        INSERT INTO account (id, owner, balance, version) VALUES
          (1, 'alice', 100, 0),
          (2, 'bob',   200, 0)""");
  }

  /// A connection in autocommit mode: every statement is its own transaction.
  /// This is the JDBC default, and the reason lost updates are so easy to write.
  public Connection open() throws SQLException {
    return DriverManager.getConnection(url);
  }

  /// A connection with autocommit off, so you control the transaction boundary.
  /// `lockTimeoutMillis` bounds how long a statement waits for a row lock before
  /// failing with {@link #LOCK_TIMEOUT}, which keeps a demo from hanging forever.
  public Connection openTransaction(int lockTimeoutMillis) throws SQLException {
    Connection connection = DriverManager.getConnection(url);
    connection.setAutoCommit(false);
    try (Statement statement = connection.createStatement()) {
      statement.execute("SET LOCK_TIMEOUT " + lockTimeoutMillis);
    }
    return connection;
  }

  public int balanceOf(int id) {
    return queryInt("SELECT balance FROM account WHERE id = ?", id);
  }

  public int versionOf(int id) {
    return queryInt("SELECT version FROM account WHERE id = ?", id);
  }

  public int queryInt(String sql, Object... parameters) {
    try (Connection connection = open();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) {
        statement.setObject(i + 1, parameters[i]);
      }
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(sql, e);
    }
  }

  public void execute(String sql) {
    try (Connection connection = open();
         Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException(sql, e);
    }
  }

  /// Rolls back and closes, swallowing failures. For use in a finally block, where an
  /// exception from cleanup would mask the one you actually care about.
  public static void rollbackQuietly(Connection connection) {
    try {
      if (connection != null && !connection.isClosed()) {
        connection.rollback();
        connection.close();
      }
    } catch (SQLException ignored) {
      // nothing useful to do while unwinding
    }
  }

  @Override
  public void close() {
    execute("SHUTDOWN");
  }

  public static void log(String message) {
    System.out.printf("%-10s %s%n", "[" + Thread.currentThread().getName() + "]", message);
  }
}
