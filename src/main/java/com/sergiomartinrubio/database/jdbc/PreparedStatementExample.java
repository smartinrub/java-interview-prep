package com.sergiomartinrubio.database.jdbc;

import com.sergiomartinrubio.database.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

/// `PreparedStatement` beyond security: reuse, typing, and the cases it cannot cover.
///
/// Interview questions:
/// - Q: Why is a `PreparedStatement` faster when reused?
/// - Q: Is it always faster?
/// - Q: When is a plain `Statement` the right choice?
/// - Q: How do you bind a NULL, and why not `setString(i, null)`?
/// - Q: How do you parameterise an `IN` clause?
///
/// Run: java -cp "target/classes:target/deps/*"
///   com.sergiomartinrubio.database.jdbc.PreparedStatementExample
public class PreparedStatementExample {

  private static final int ITERATIONS = 20_000;

  static void main() throws Exception {
    try (Db db = Db.accounts("prepared")) {
      reuseIsWhatPays(db);
      whenStatementIsFine(db);
      nullsAndTypes(db);
      inClause(db);
      likeAndWildcards(db);

      System.out.println();
      // A: because the expensive part -- parsing the SQL, resolving names, building and
      //    choosing a plan -- happens ONCE, at prepare time. Each execution then just ships
      //    the parameter values. Many databases also cache the plan server-side, keyed on
      //    the statement text, so even a freshly prepared statement can reuse a cached plan.
      //
      // A: not always, and this is the honest nuance:
      //      - prepared once, executed once  -> roughly the same, sometimes slower,
      //        because you pay a prepare round trip for a single execution
      //      - prepared once, executed many  -> clearly faster
      //      - a cached plan can be WORSE than a fresh one when data is skewed, because it
      //        was chosen for different parameter values ("parameter sniffing")
      //    So the performance argument is real but conditional. The security argument is not.
      System.out.println("Use a PreparedStatement for anything with a value in it, every time.");
      System.out.println("Treat the performance benefit as a bonus, not the reason.");
    }
  }

  /// Same 20,000 lookups: one prepare and many executes, versus a fresh parse each time.
  static void reuseIsWhatPays(Db db) throws SQLException {
    System.out.println("--- reuse: " + String.format("%,d", ITERATIONS) + " lookups ---");

    long statementMillis;
    try (Connection connection = db.open();
         Statement statement = connection.createStatement()) {
      long start = System.nanoTime();
      for (int i = 0; i < ITERATIONS; i++) {
        int id = (i % 2) + 1;
        // A new SQL string every iteration, so the database must parse and plan each one.
        try (ResultSet rows =
                 statement.executeQuery("SELECT balance FROM account WHERE id = " + id)) {
          rows.next();
          rows.getInt(1);
        }
      }
      statementMillis = (System.nanoTime() - start) / 1_000_000;
    }

    long preparedMillis;
    try (Connection connection = db.open();
         // Prepared ONCE, outside the loop. Preparing inside the loop throws away the
         // benefit and is a common mistake.
         PreparedStatement statement =
             connection.prepareStatement("SELECT balance FROM account WHERE id = ?")) {
      long start = System.nanoTime();
      for (int i = 0; i < ITERATIONS; i++) {
        statement.setInt(1, (i % 2) + 1);
        try (ResultSet rows = statement.executeQuery()) {
          rows.next();
          rows.getInt(1);
        }
      }
      preparedMillis = (System.nanoTime() - start) / 1_000_000;
    }

    System.out.printf("  Statement (parsed each time)   : %,5d ms%n", statementMillis);
    System.out.printf("  PreparedStatement (prepared 1x): %,5d ms%n", preparedMillis);
    System.out.println("  H2 is in-process, so this understates the gain: over a network the");
    System.out.println("  saved parse plus the saved round trips matter far more.");
  }

  /// A: when there are no values to bind. DDL, a fixed query with no parameters, or a
  ///    one-off admin statement. `Statement` is marginally cheaper there because you skip
  ///    the prepare step, and there is no injection risk without concatenated input.
  ///    The moment any value comes from outside, switch.
  static void whenStatementIsFine(Db db) throws SQLException {
    System.out.println("\n--- when a plain Statement is fine ---");
    try (Connection connection = db.open();
         Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE IF NOT EXISTS audit (id INT PRIMARY KEY, note VARCHAR(50))");
      System.out.println("  DDL with no parameters: Statement is appropriate");

      try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM account")) {
        rows.next();
        System.out.println("  fixed query with no parameters: " + rows.getInt(1) + " accounts");
      }
    }
  }

  /// A: with `setNull(index, Types.X)`. The driver has to tell the database the column type
  ///    of the NULL, and `setString(i, null)` leaves that ambiguous -- some drivers accept
  ///    it, some fail, and behaviour differs by column type. `setNull` is unambiguous.
  static void nullsAndTypes(Db db) throws SQLException {
    System.out.println("\n--- NULLs and typed setters ---");
    db.execute("CREATE TABLE IF NOT EXISTS profile ("
        + "id INT PRIMARY KEY, nickname VARCHAR(50), age INT, joined TIMESTAMP)");
    db.execute("DELETE FROM profile");

    try (Connection connection = db.open();
         PreparedStatement statement = connection.prepareStatement(
             "INSERT INTO profile (id, nickname, age, joined) VALUES (?, ?, ?, ?)")) {
      statement.setInt(1, 1);
      statement.setNull(2, Types.VARCHAR);
      statement.setNull(3, Types.INTEGER);
      // Typed setters also remove all date and number FORMATTING concerns: no locale, no
      // string format to get wrong, no timezone ambiguity from concatenating a date.
      statement.setTimestamp(4, java.sql.Timestamp.valueOf("2026-09-28 10:00:00"));
      statement.executeUpdate();
    }

    try (Connection connection = db.open();
         PreparedStatement statement =
             connection.prepareStatement("SELECT nickname, age, joined FROM profile WHERE id = ?")) {
      statement.setInt(1, 1);
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        System.out.println("  nickname = " + rows.getString("nickname"));
        int age = rows.getInt("age");
        // getInt returns 0 for NULL, so wasNull() is the only way to tell them apart.
        System.out.println("  age      = " + (rows.wasNull() ? "NULL" : age)
            + "   (getInt returns " + age + " for NULL -- always check wasNull)");
        System.out.println("  joined   = " + rows.getTimestamp("joined"));
      }
    }
  }

  /// A: you cannot bind a list to one placeholder. `WHERE id IN (?)` with "1,2,3" looks
  ///    right and matches nothing, because the whole string is one value. You generate as
  ///    many placeholders as you have values.
  static void inClause(Db db) throws SQLException {
    System.out.println("\n--- parameterising IN ---");
    List<Integer> ids = List.of(1, 2);

    // The wrong version, shown because it is the mistake people actually make:
    try (Connection connection = db.open();
         PreparedStatement statement =
             connection.prepareStatement("SELECT COUNT(*) FROM account WHERE id IN (?)")) {
      statement.setString(1, "1,2");
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        System.out.println("  IN (?) with \"1,2\"     -> " + rows.getInt(1) + " rows (wrong)");
      }
    } catch (SQLException e) {
      System.out.println("  IN (?) with \"1,2\"     -> rejected, code " + e.getErrorCode());
    }

    // The right version: build the placeholder list from the size of the collection, then
    // bind each value. The SQL structure comes from your code, the values from parameters.
    String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
    String sql = "SELECT COUNT(*) FROM account WHERE id IN (" + placeholders + ")";

    try (Connection connection = db.open();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < ids.size(); i++) {
        statement.setInt(i + 1, ids.get(i));
      }
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        System.out.println("  IN (" + placeholders + ") bound one by one -> "
            + rows.getInt(1) + " rows (correct)");
      }
    }
    System.out.println("  Note: a different list size produces a different SQL string, which");
    System.out.println("  means a different cached plan. Very large IN lists are a known");
    System.out.println("  plan-cache problem; a temp table or a join scales better.");
  }

  /// The wildcards belong in the VALUE, not in the SQL.
  static void likeAndWildcards(Db db) throws SQLException {
    System.out.println("\n--- LIKE ---");
    try (Connection connection = db.open();
         PreparedStatement statement =
             connection.prepareStatement("SELECT owner FROM account WHERE owner LIKE ?")) {
      // Correct: concatenate the % into the parameter value.
      // Wrong: "LIKE '%' || ? || '%'" works but "LIKE '%?%'" does not -- inside a string
      // literal the ? is just a question mark, not a placeholder.
      statement.setString(1, "a%");
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          System.out.println("  owner starting with 'a': " + rows.getString(1));
        }
      }
    }
  }
}
