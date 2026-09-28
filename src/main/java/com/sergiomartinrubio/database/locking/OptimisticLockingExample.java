package com.sergiomartinrubio.database.locking;

import com.sergiomartinrubio.database.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/// Optimistic locking: take no lock, but refuse a write whose row changed since it was read.
///
/// "Optimistic" because it assumes conflicts are rare. It does not prevent them, it
/// DETECTS them, and the caller retries.
///
/// Interview questions:
/// - Q: How does optimistic locking work?
/// - Q: Where does the conflict actually get detected?
/// - Q: What do you do when it fails?
/// - Q: Is a timestamp as good as a version number?
/// - Q: How does JPA implement this?
///
/// Run: java -cp "target/classes:target/deps/*"
///   com.sergiomartinrubio.database.locking.OptimisticLockingExample
public class OptimisticLockingExample {

  static void main() throws Exception {
    try (Db db = Db.accounts("optimistic")) {
      // A: add a version column. Read the row and its version, then make the UPDATE
      //    conditional on the version still being the one you read, and bump it in the
      //    same statement:
      //
      //      UPDATE account SET balance = ?, version = version + 1
      //       WHERE id = ? AND version = ?
      //
      //    No lock is ever taken. Nothing blocks. The whole mechanism is one WHERE clause.
      conflictIsDetected(db);

      db.reset();
      System.out.println();
      retryUntilItSticks(db);

      System.out.println();
      System.out.println("final balance = " + db.balanceOf(1)
          + ", version = " + db.versionOf(1));

      // A: a timestamp works but is a worse choice. Clock resolution can be coarser than
      //    your update rate, so two updates in the same millisecond look identical, and
      //    clocks move backwards (NTP, DST, clock skew across app servers). An integer
      //    that only ever increments has none of those failure modes.
      //
      // A: in JPA you annotate a field with @Version and Hibernate appends the version
      //    predicate to every UPDATE and bumps the column for you. A zero row count
      //    becomes OptimisticLockException. Same SQL, generated rather than written.
      //    Nothing about it is Hibernate magic: it is the WHERE clause above.
    }
  }

  /// Both transactions read version 0. The first update matches and succeeds; the second
  /// matches nothing.
  static void conflictIsDetected(Db db) throws Exception {
    System.out.println("--- two writers, same version ---");
    CountDownLatch bothHaveRead = new CountDownLatch(2);
    AtomicInteger conflicts = new AtomicInteger();

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 1; i <= 2; i++) {
        String name = "writer-" + i;
        executor.submit(() -> {
          try (Connection connection = db.open()) {
            Account account = load(connection, 1);
            Db.log(name + " read balance=" + account.balance + " version=" + account.version);

            bothHaveRead.countDown();
            bothHaveRead.await();

            int rowsAffected = updateIfUnchanged(connection, account, account.balance + 100);

            // A: right here, in the row count. This is the part people miss in interviews:
            //    the database reports success either way, because "UPDATE matched zero
            //    rows" is a perfectly valid outcome. The application MUST check the count
            //    and decide. Ignore it and you are back to a silent lost update.
            if (rowsAffected == 0) {
              conflicts.incrementAndGet();
              Db.log(name + " CONFLICT: 0 rows matched, someone else committed first");
            } else {
              Db.log(name + " committed, version is now " + (account.version + 1));
            }
          } catch (SQLException | InterruptedException e) {
            throw new IllegalStateException(e);
          }
          return null;
        });
      }
    }

    System.out.println("conflicts detected = " + conflicts.get()
        + ", balance = " + db.balanceOf(1) + " (no update was lost silently)");
  }

  /// A: you retry, from the read. Re-reading is essential: the whole point is that your
  ///    copy is stale, so replaying the same computation on the same stale data would
  ///    conflict forever. Bound the attempts and give up with a real error.
  ///
  ///    The alternative, when a retry is not safe or the user's intent may have changed,
  ///    is to surface it: "this record was modified by someone else, reload and redo".
  static void retryUntilItSticks(Db db) throws Exception {
    System.out.println("--- 8 concurrent deposits with a retry loop ---");
    int writers = 8;
    AtomicInteger totalRetries = new AtomicInteger();

    try (ExecutorService executor = Executors.newFixedThreadPool(writers)) {
      for (int i = 0; i < writers; i++) {
        executor.submit(() -> {
          try (Connection connection = db.open()) {
            deposit(connection, 1, 100, totalRetries);
          } catch (SQLException e) {
            throw new IllegalStateException(e);
          }
          return null;
        });
      }
    }

    System.out.println("8 deposits of 100 onto a balance of 100");
    System.out.println("balance = " + db.balanceOf(1) + " (expected 900)");
    System.out.println("version = " + db.versionOf(1) + " (expected 8, one per success)");
    System.out.println("retries = " + totalRetries.get()
        + " wasted attempts -- this is what contention costs you");
  }

  static final int MAX_ATTEMPTS = 50;

  static void deposit(Connection connection, int id, int amount, AtomicInteger retries)
      throws SQLException {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      // Re-read every attempt. Using a stale copy would loop until MAX_ATTEMPTS.
      Account account = load(connection, id);

      if (updateIfUnchanged(connection, account, account.balance + amount) == 1) {
        return;
      }
      retries.incrementAndGet();
      // A real system backs off here (jittered sleep) instead of hammering the row.
    }
    throw new IllegalStateException("gave up after " + MAX_ATTEMPTS + " optimistic attempts");
  }

  record Account(int id, int balance, int version) { }

  static Account load(Connection connection, int id) throws SQLException {
    // The version travels with the data. Whatever holds the row between read and write
    // (an HTTP session, a hidden form field, a detached JPA entity) must carry it too.
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT id, balance, version FROM account WHERE id = ?")) {
      statement.setInt(1, id);
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return new Account(rows.getInt("id"), rows.getInt("balance"), rows.getInt("version"));
      }
    }
  }

  /// The entire mechanism. Returns 1 on success, 0 when someone else got there first.
  static int updateIfUnchanged(Connection connection, Account account, int newBalance)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        UPDATE account
           SET balance = ?, version = version + 1
         WHERE id = ? AND version = ?""")) {
      statement.setInt(1, newBalance);
      statement.setInt(2, account.id());
      statement.setInt(3, account.version());
      return statement.executeUpdate();
    }
  }
}
