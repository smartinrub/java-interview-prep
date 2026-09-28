package com.sergiomartinrubio.database.transactions;

import com.sergiomartinrubio.database.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/// The four isolation levels against the three read anomalies, measured rather than recited.
///
/// Interview questions:
/// - Q: What are the read anomalies, and which level prevents which?
/// - Q: What is the default isolation level?
/// - Q: Why doesn't SERIALIZABLE solve everything?
/// - Q: Does isolation prevent lost updates?
///
/// Every row in the table below is produced by actually running the scenario. Two results
/// do NOT match the textbook table, which is the most useful thing in this file -- see the
/// notes at the end.
///
/// Run: java -cp "target/classes:target/deps/*"
///   com.sergiomartinrubio.database.transactions.IsolationLevelsExample
public class IsolationLevelsExample {

  record Level(String name, int jdbcConstant) { }

  static final Level[] LEVELS = {
      new Level("READ_UNCOMMITTED", Connection.TRANSACTION_READ_UNCOMMITTED),
      new Level("READ_COMMITTED", Connection.TRANSACTION_READ_COMMITTED),
      new Level("REPEATABLE_READ", Connection.TRANSACTION_REPEATABLE_READ),
      new Level("SERIALIZABLE", Connection.TRANSACTION_SERIALIZABLE),
  };

  static void main() throws Exception {
    try (Db db = Db.accounts("isolation")) {
      // A: the three anomalies, weakest to strongest guarantee needed to stop them:
      //      dirty read          - you read a row another transaction has not committed,
      //                            and it may yet roll back. You acted on a value that
      //                            never existed.
      //      non-repeatable read - you read the same ROW twice in one transaction and get
      //                            two different values, because someone committed between
      //                            your reads.
      //      phantom read        - you run the same QUERY twice and the number of matching
      //                            rows changes, because someone inserted or deleted.
      try (Connection connection = db.open()) {
        System.out.println("engine  = H2 " + connection.getMetaData().getDatabaseProductVersion());
        System.out.println("default = " + nameOf(connection.getTransactionIsolation())
            + ", autoCommit = " + connection.getAutoCommit());
      }

      System.out.println();
      System.out.printf("%-18s %-12s %-18s %-12s%n",
          "level", "dirty read", "non-repeatable", "phantom");
      System.out.println("-".repeat(62));

      for (Level level : LEVELS) {
        db.reset();
        boolean dirty = dirtyRead(db, level);
        db.reset();
        boolean[] repeatAndPhantom = repeatedReads(db, level);
        System.out.printf("%-18s %-12s %-18s %-12s%n",
            level.name(),
            dirty ? "POSSIBLE" : "prevented",
            repeatAndPhantom[0] ? "POSSIBLE" : "prevented",
            repeatAndPhantom[1] ? "POSSIBLE" : "prevented");
      }

      System.out.println();
      notes();
      db.reset();
      isolationDoesNotStopLostUpdates(db);
    }
  }

  /// Reader tries to observe a value the writer has not committed.
  static boolean dirtyRead(Db db, Level level) throws SQLException {
    Connection writer = db.openTransaction(1_000);
    Connection reader = db.openTransaction(1_000);
    try {
      reader.setTransactionIsolation(level.jdbcConstant());

      setBalance(writer, 1, 999);   // NOT committed

      int seen = readBalance(reader, 1);
      return seen == 999;
    } catch (SQLException e) {
      // Some engines refuse the read rather than allow it; that counts as prevented.
      if (e.getErrorCode() == Db.LOCK_TIMEOUT) {
        return false;
      }
      throw e;
    } finally {
      Db.rollbackQuietly(writer);   // the 999 never existed
      Db.rollbackQuietly(reader);
    }
  }

  /// Reader reads a row and a count twice, with a committed write in between.
  /// Returns {nonRepeatableRead, phantomRead}.
  static boolean[] repeatedReads(Db db, Level level) throws SQLException {
    Connection reader = db.openTransaction(1_000);
    try {
      reader.setTransactionIsolation(level.jdbcConstant());

      int balanceBefore = readBalance(reader, 1);
      int countBefore = countRows(reader);

      // A separate, fully committed transaction. Autocommit, so it commits immediately.
      db.execute("UPDATE account SET balance = 555 WHERE id = 1");
      db.execute("INSERT INTO account (id, owner, balance, version) VALUES (99, 'carol', 1, 0)");

      int balanceAfter = readBalance(reader, 1);
      int countAfter = countRows(reader);

      return new boolean[] {balanceBefore != balanceAfter, countBefore != countAfter};
    } finally {
      Db.rollbackQuietly(reader);
    }
  }

  static void notes() {
    // A: the default varies by engine and is worth knowing for the ones you use:
    //      READ_COMMITTED  - Postgres, Oracle, SQL Server, H2 -> Each statement gets a new snapshot.
    //                        So within the same transaction, two identical SELECTs can return different results.
    //      REPEATABLE_READ - MySQL/InnoDB -> The transaction gets a stable snapshot when the transaction's first statement executes.
    //                        Transaction A continues seeing the version of the data that was visible to its snapshot.
    //    A candidate who says "SERIALIZABLE" has usually not had to tune a database.
    System.out.println("Notes on the table above:");
    System.out.println();
    System.out.println("1. H2's REPEATABLE_READ also prevents phantoms, which the SQL standard");
    System.out.println("   does NOT require. H2 implements it as snapshot isolation, so the whole");
    System.out.println("   transaction sees one consistent snapshot and no new row can appear.");
    System.out.println("   Postgres behaves the same way. MySQL/InnoDB likewise, via gap locks.");
    System.out.println("   The lesson: the level NAMES are a standard, the behaviour is not.");
    System.out.println("   Never assume; test against the engine you actually run.");
    System.out.println();
    System.out.println("2. SERIALIZABLE and REPEATABLE_READ look identical here because H2 gives");
    System.out.println("   both a snapshot. They differ on WRITE conflicts, not on these reads.");
    System.out.println();
    // A: because SERIALIZABLE is about making concurrent transactions equivalent to SOME
    //    serial order, and it buys that with either heavy locking or aborted transactions.
    //    You still have to handle serialization failures and retry, throughput on
    //    contended rows drops, and it does nothing about anomalies that span transaction
    //    boundaries, which is where the lost update in the locking package lives.
    System.out.println("3. Why not just use SERIALIZABLE everywhere? It buys correctness with");
    System.out.println("   lock contention or aborted transactions, so you still need retry logic,");
    System.out.println("   and throughput on hot rows drops sharply. It is the right default only");
    System.out.println("   when correctness clearly outranks throughput.");
    System.out.println();
  }

  /// A: no, and this is the single most important point connecting the two packages.
  ///    Isolation governs what a transaction SEES. A lost update is about what
  ///    transactions are allowed to WRITE, and it happens across transaction boundaries
  ///    where no isolation level applies at all.
  static void isolationDoesNotStopLostUpdates(Db db) throws SQLException {
    System.out.println("4. Isolation does not prevent a lost update across transactions:");

    // Two SERIALIZABLE connections, each doing read-then-write in its own transaction.
    try (Connection first = db.open(); Connection second = db.open()) {
      first.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
      second.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

      int readByFirst = readBalance(first, 1);
      int readBySecond = readBalance(second, 1);

      setBalance(first, 1, readByFirst + 100);
      setBalance(second, 1, readBySecond + 100);

      System.out.println("   both SERIALIZABLE connections read " + readByFirst
          + ", each added 100");
      System.out.println("   balance = " + db.balanceOf(1) + ", expected "
          + (readByFirst + 200) + " -- one update still lost");
      System.out.println("   Fix it with a version check or FOR UPDATE, not an isolation level.");
    }
  }

  static int readBalance(Connection connection, int id) throws SQLException {
    try (PreparedStatement statement =
             connection.prepareStatement("SELECT balance FROM account WHERE id = ?")) {
      statement.setInt(1, id);
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    }
  }

  static int countRows(Connection connection) throws SQLException {
    try (PreparedStatement statement =
             connection.prepareStatement("SELECT COUNT(*) FROM account");
         ResultSet rows = statement.executeQuery()) {
      rows.next();
      return rows.getInt(1);
    }
  }

  static void setBalance(Connection connection, int id, int balance) throws SQLException {
    try (PreparedStatement statement =
             connection.prepareStatement("UPDATE account SET balance = ? WHERE id = ?")) {
      statement.setInt(1, balance);
      statement.setInt(2, id);
      statement.executeUpdate();
    }
  }

  static String nameOf(int jdbcConstant) {
    for (Level level : LEVELS) {
      if (level.jdbcConstant() == jdbcConstant) {
        return level.name();
      }
    }
    return "UNKNOWN(" + jdbcConstant + ")";
  }
}
