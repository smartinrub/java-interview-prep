package com.sergiomartinrubio.database.jdbc;

import com.sergiomartinrubio.database.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/// Why `PreparedStatement` matters, demonstrated by attacking a `Statement`.
///
/// This is the real answer to "PreparedStatement vs Statement". Candidates usually lead
/// with performance; the security difference is the one that gets people fired.
///
/// Interview questions:
/// - Q: What is SQL injection, and what actually causes it?
/// - Q: How does a `PreparedStatement` prevent it?
/// - Q: Is escaping the input enough?
/// - Q: Can a `PreparedStatement` still be injectable?
///
/// Everything below runs against a throwaway in-memory database.
///
/// Run: java -cp "target/classes:target/deps/*"
///   com.sergiomartinrubio.database.jdbc.SqlInjectionExample
public class SqlInjectionExample {

  static void main() throws Exception {
    try (Db db = Db.accounts("injection")) {
      // A second table, to show that injection is not limited to the table you queried.
      db.execute("CREATE TABLE IF NOT EXISTS secret (id INT PRIMARY KEY, token VARCHAR(50))");
      db.execute("DELETE FROM secret");
      db.execute("INSERT INTO secret VALUES (1, 'api-key-xyz'), (2, 'root-password')");

      // A: the cause is not "user input". It is building a statement by CONCATENATING
      //    data into SQL text, which lets the data change the statement's structure. The
      //    database receives one string and cannot tell which characters you intended as
      //    code and which as a value.
      legitimateSearch(db);
      tautology(db);
      unionReadsAnotherTable(db);
      escapingIsNotEnough(db);
      parametersAreDataNotCode(db);
      destructive(db);

      System.out.println();
      // A: a PreparedStatement sends the SQL and the parameters over the wire SEPARATELY.
      //    The statement is parsed and planned with `?` placeholders before any value is
      //    supplied, so the structure is already fixed. A parameter can never become an
      //    operator, a keyword or another statement. It is not escaping -- the value is
      //    never part of the SQL text at all.
      //
      // A: escaping is a mitigation, not a fix. You have to get every case right for
      //    every dialect, for every type, forever -- quotes, backslashes, unicode,
      //    numeric contexts with no quotes at all. Parameterisation removes the problem
      //    instead of managing it.
      //
      // A: yes, in one way: placeholders only work where the database allows a VALUE.
      //    Table names, column names, ORDER BY columns and SQL keywords cannot be
      //    parameterised, so if those come from user input you must validate them against
      //    an allow-list. This is the one case that still needs care:
      //
      //      String column = ALLOWED.contains(input) ? input : "id";   // allow-list
      //      "SELECT * FROM account ORDER BY " + column;               // now safe
      System.out.println("Rule: every value goes through a parameter. Anything that cannot be");
      System.out.println("a parameter (table, column, ORDER BY, keywords) goes through an allow-list.");
    }
  }

  /// The normal case, so the difference is visible.
  static void legitimateSearch(Db db) throws SQLException {
    System.out.println("--- 1. a legitimate search ---");
    System.out.println("input: alice");
    printOwners(vulnerableSearch(db, "alice"));
  }

  /// A: classic tautology. The injected `' OR '1'='1` closes the string literal and adds a
  ///    condition that is always true, so the WHERE clause stops filtering.
  static void tautology(Db db) throws SQLException {
    System.out.println("\n--- 2. tautology: dump the whole table ---");
    String input = "' OR '1'='1";
    System.out.println("input: " + input);
    System.out.println("becomes: SELECT id, owner FROM account WHERE owner = '" + input + "'");
    printOwners(vulnerableSearch(db, input));
    System.out.println("  ^ the filter is gone; every row leaked");
  }

  /// Worse than dumping one table: read any other table the connection can see.
  static void unionReadsAnotherTable(Db db) throws SQLException {
    System.out.println("\n--- 3. UNION: read a table the query never mentioned ---");
    String input = "' UNION SELECT id, token FROM secret WHERE '1'='1";
    System.out.println("input: " + input);
    printOwners(vulnerableSearch(db, input));
    System.out.println("  ^ contents of the `secret` table, from a query against `account`");
  }

  /// A: and concatenation is not only unsafe, it is also WRONG for ordinary data. A name
  ///    with an apostrophe breaks the statement, which is the same bug as the injection,
  ///    just triggered by accident instead of on purpose.
  static void escapingIsNotEnough(Db db) throws SQLException {
    System.out.println("\n--- 4. the same flaw breaks ordinary data ---");
    try (Connection connection = db.open();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO account VALUES (5, 'O'Brien', 60, 0)");
      System.out.println("insert succeeded (unexpected)");
    } catch (SQLException e) {
      System.out.println("concatenating \"O'Brien\" -> SQL syntax error, code " + e.getErrorCode());
    }

    try (Connection connection = db.open();
         PreparedStatement statement = connection.prepareStatement(
             "INSERT INTO account (id, owner, balance, version) VALUES (?, ?, ?, 0)")) {
      statement.setInt(1, 5);
      statement.setString(2, "O'Brien");
      statement.setInt(3, 60);
      statement.executeUpdate();
      System.out.println("the same value via a parameter -> inserted correctly");
    }
  }

  /// The proof that a parameter is data: feed SQL into a numeric parameter and the driver
  /// rejects it as a bad value rather than running it.
  static void parametersAreDataNotCode(Db db) throws SQLException {
    System.out.println("\n--- 5. the identical attack through a parameter ---");
    String input = "' OR '1'='1";

    try (Connection connection = db.open();
         PreparedStatement statement = connection.prepareStatement(
             "SELECT id, owner FROM account WHERE owner = ?")) {
      statement.setString(1, input);
      try (ResultSet rows = statement.executeQuery()) {
        int count = 0;
        while (rows.next()) {
          count++;
        }
        System.out.println("PreparedStatement returned " + count
            + " rows -- it searched for an owner literally named \"" + input + "\"");
      }
    }

    try (Connection connection = db.open();
         PreparedStatement statement = connection.prepareStatement(
             "SELECT id FROM account WHERE id = ?")) {
      statement.setString(1, "1 OR 1=1");
      statement.executeQuery();
      System.out.println("numeric injection succeeded (unexpected)");
    } catch (SQLException e) {
      System.out.println("\"1 OR 1=1\" into a numeric parameter -> data conversion error, code "
          + e.getErrorCode());
      System.out.println("  ^ it was treated as a VALUE and found invalid, never as SQL");
    }
  }

  /// A: and if the driver permits stacked statements, injection is not limited to reading.
  ///    H2 allows them, so this genuinely drops the table. Support varies: the Postgres
  ///    JDBC driver allows stacked statements, MySQL Connector/J requires
  ///    allowMultiQueries=true. Never rely on the driver refusing.
  static void destructive(Db db) throws SQLException {
    System.out.println("\n--- 6. stacked statements: not just reading ---");
    String input = "x'; DROP TABLE secret; --";
    System.out.println("input: " + input);

    try (Connection connection = db.open();
         Statement statement = connection.createStatement()) {
      statement.execute("SELECT id FROM account WHERE owner = '" + input + "'");
    } catch (SQLException e) {
      System.out.println("(driver reported: " + e.getMessage().split("\n")[0] + ")");
    }

    boolean secretSurvived;
    try (Connection connection = db.open();
         ResultSet tables = connection.getMetaData().getTables(null, null, "SECRET", null)) {
      secretSurvived = tables.next();
    }
    System.out.println("`secret` table still exists = " + secretSurvived
        + (secretSurvived ? "" : "  <- dropped by the injected statement"));
  }

  /// The vulnerable method, kept deliberately small so the flaw is unmissable: the input is
  /// concatenated into the SQL text. Only the concatenation is wrong here -- the resources
  /// are still closed properly, so the example does not teach two bad habits at once.
  static List<String> vulnerableSearch(Db db, String ownerInput) throws SQLException {
    List<String> found = new ArrayList<>();
    try (Connection connection = db.open();
         Statement statement = connection.createStatement();
         ResultSet rows = statement.executeQuery(
             "SELECT id, owner FROM account WHERE owner = '" + ownerInput + "'")) {
      while (rows.next()) {
        found.add(rows.getInt(1) + " / " + rows.getString(2));
      }
    }
    return found;
  }

  static void printOwners(List<String> rows) {
    if (rows.isEmpty()) {
      System.out.println("  -> no rows");
      return;
    }
    rows.forEach(row -> System.out.println("  -> " + row));
  }
}
