package com.sergiomartinrubio.database.jdbc;

import com.sergiomartinrubio.database.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;

/// Batching: the other big reason to hold on to a `PreparedStatement`.
///
/// Interview questions:
/// - Q: What does `addBatch()`/`executeBatch()` actually save?
/// - Q: Why does autocommit destroy batch performance?
/// - Q: What does `executeBatch()` return, and what happens if one row fails?
/// - Q: Should you batch everything in one go?
///
/// Run: java -cp "target/classes:target/deps/*"
///   com.sergiomartinrubio.database.jdbc.BatchUpdateExample
public class BatchUpdateExample {

  private static final int ROWS = 20_000;
  private static final int BATCH_SIZE = 500;

  static void main() throws Exception {
    try (Db db = Db.accounts("batch")) {
      db.execute("CREATE TABLE IF NOT EXISTS entry (id INT PRIMARY KEY, label VARCHAR(50))");

      long oneByOneAutocommit = insertOneByOne(db, true);
      long oneByOneTransaction = insertOneByOne(db, false);
      long batched = insertBatched(db);

      System.out.printf("%n%,d inserts:%n", ROWS);
      System.out.printf("  one by one, autocommit on  : %,6d ms%n", oneByOneAutocommit);
      System.out.printf("  one by one, one transaction: %,6d ms%n", oneByOneTransaction);
      System.out.printf("  batched in %d, one txn     : %,6d ms%n", BATCH_SIZE, batched);

      System.out.println();
      // A: round trips. Without batching each execute is a separate request/response to the
      //    database; with batching the driver ships many parameter sets in one go. On a
      //    local in-memory database that saves little, but across a network it is the
      //    difference between N round trips and N/BATCH_SIZE of them.
      //
      // A: because with autocommit on, EVERY statement is its own transaction, so the
      //    database must make each row durable separately -- a flush (and often an fsync)
      //    per row. That, not the parsing, is usually the dominant cost. Note above that
      //    simply turning autocommit off is often the bigger win of the two.
      partialFailure(db);

      System.out.println();
      // A: no. One giant batch buys nothing beyond a certain size and costs memory in both
      //    the driver and the database, and it makes a failure expensive to unpick. A few
      //    hundred to a few thousand rows per batch is the usual sweet spot, which is why
      //    the loop below clears the batch every BATCH_SIZE rows.
      System.out.println("Batch in chunks (a few hundred to a few thousand), not all at once:");
      System.out.println("one huge batch wastes memory and makes failures hard to recover from.");
    }
  }

  static long insertOneByOne(Db db, boolean autocommit) throws SQLException {
    db.execute("DELETE FROM entry");
    long start = System.nanoTime();

    try (Connection connection = db.open()) {
      connection.setAutoCommit(autocommit);
      try (PreparedStatement statement =
               connection.prepareStatement("INSERT INTO entry (id, label) VALUES (?, ?)")) {
        for (int i = 0; i < ROWS; i++) {
          statement.setInt(1, i);
          statement.setString(2, "label-" + i);
          statement.executeUpdate();
        }
      }
      if (!autocommit) {
        connection.commit();
      }
    }
    return (System.nanoTime() - start) / 1_000_000;
  }

  static long insertBatched(Db db) throws SQLException {
    db.execute("DELETE FROM entry");
    long start = System.nanoTime();

    try (Connection connection = db.open()) {
      connection.setAutoCommit(false);
      try (PreparedStatement statement =
               connection.prepareStatement("INSERT INTO entry (id, label) VALUES (?, ?)")) {
        for (int i = 0; i < ROWS; i++) {
          statement.setInt(1, i);
          statement.setString(2, "label-" + i);
          statement.addBatch();

          // Flush periodically so neither the driver nor the database has to hold all
          // 20,000 parameter sets in memory at once.
          if ((i + 1) % BATCH_SIZE == 0) {
            statement.executeBatch();
            statement.clearBatch();
          }
        }
        statement.executeBatch();   // whatever is left over
      }
      connection.commit();
    }
    return (System.nanoTime() - start) / 1_000_000;
  }

  /// A: it returns one update count per statement in the batch, in order. On failure the
  ///    driver throws BatchUpdateException, whose getUpdateCounts() tells you how far it
  ///    got -- including Statement.EXECUTE_FAILED for the entries that failed. Whether the
  ///    remaining statements are attempted is driver-dependent, which is exactly why you
  ///    run a batch inside a transaction: then you can roll the whole chunk back and stop
  ///    guessing.
  static void partialFailure(Db db) throws SQLException {
    System.out.println("--- when one row in the batch fails ---");
    db.execute("DELETE FROM entry");
    db.execute("INSERT INTO entry (id, label) VALUES (7, 'already here')");

    Connection connection = db.open();
    try {
      connection.setAutoCommit(false);
      try (PreparedStatement statement =
               connection.prepareStatement("INSERT INTO entry (id, label) VALUES (?, ?)")) {
        for (int id : new int[] {5, 6, 7, 8}) {   // 7 violates the primary key
          statement.setInt(1, id);
          statement.setString(2, "label-" + id);
          statement.addBatch();
        }
        int[] counts = statement.executeBatch();
        System.out.println("  unexpected success: " + Arrays.toString(counts));
        connection.commit();
      } catch (java.sql.BatchUpdateException e) {
        System.out.println("  BatchUpdateException, errorCode " + e.getErrorCode());
        System.out.println("  update counts: " + Arrays.toString(e.getUpdateCounts()));
        System.out.println("  (EXECUTE_FAILED is " + java.sql.Statement.EXECUTE_FAILED + ")");
        connection.rollback();
        System.out.println("  rolled back the whole chunk");
      }
    } finally {
      Db.rollbackQuietly(connection);
    }

    System.out.println("  rows in table now = " + db.queryInt("SELECT COUNT(*) FROM entry")
        + " (just the pre-existing one; nothing partially applied)");
  }
}
