package com.sergiomartinrubio.database.locking;

import com.sergiomartinrubio.database.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/// Database deadlock: two transactions locking the same rows in opposite orders.
///
/// The same Coffman circular-wait as a Java deadlock, with one big difference: the database
/// DETECTS it and kills a victim, where the JVM just hangs.
///
/// Interview questions:
/// - Q: How does a database deadlock differ from an application deadlock?
/// - Q: What does the database do about it?
/// - Q: How do you prevent one?
/// - Q: Deadlock vs lock timeout -- how do you tell them apart, and does it matter?
///
/// Run: java -cp "target/classes:target/deps/*"
///   com.sergiomartinrubio.database.locking.DatabaseDeadlockExample
public class DatabaseDeadlockExample {

  static void main() throws Exception {
    try (Db db = Db.accounts("deadlock")) {
      // A: the mechanism is identical -- each party holds what the other needs -- but the
      //    outcome is not. A JVM deadlock hangs until you kill the process. The database
      //    runs a deadlock detector, finds the cycle in its wait-for graph, picks a victim,
      //    rolls it back and returns an error. Your application gets an exception rather
      //    than a hang, which means deadlock is a RETRYABLE condition, not an outage.
      System.out.println("--- transfers in opposite directions ---");
      oppositeOrder(db);

      System.out.println();
      System.out.println("balances: row 1 = " + db.balanceOf(1) + ", row 2 = " + db.balanceOf(2));
      System.out.println("The victim was rolled back entirely, so no half-transfer survived.");

      db.reset();
      System.out.println();
      orderedLocking(db);
      System.out.println("both committed: balances " + db.balanceOf(1) + " / "
          + db.balanceOf(2) + " (two opposite transfers of 10 cancel out, so 100/200)");

      System.out.println();
      // A: prevention is the same as in application code, and the first option is the one
      //    that actually scales:
      //      1. consistent lock ordering - always touch rows in the same order, e.g.
      //         sorted by primary key. This makes a cycle impossible. See orderedLocking().
      //      2. keep transactions short - less overlap, less chance of a cycle
      //      3. take all the locks you need up front, in one statement where possible
      //      4. lower granularity / better indexes - fewer rows locked, fewer collisions
      //      5. and always: catch the deadlock error and retry, because you cannot
      //         eliminate every cycle in a system with concurrent multi-row writes
      //
      // A: they are different errors and they mean different things.
      //      deadlock (H2 " + Db.DEADLOCK + ", Postgres 40P01, MySQL 1213)
      //        -> a genuine cycle. Retrying usually works, because the other party has
      //           committed by the time you come back.
      //      lock timeout (H2 " + Db.LOCK_TIMEOUT + ", MySQL 1205)
      //        -> no cycle, just a lock held longer than you were willing to wait. Retrying
      //           may well hit the same wall, so it points at a slow transaction to fix.
      //    Both arrive as SQLException, so branch on getErrorCode()/getSQLState(), never on
      //    the message text.
      System.out.println("deadlock error code    = " + Db.DEADLOCK + " (SQL state 40001)");
      System.out.println("lock timeout error code = " + Db.LOCK_TIMEOUT + " (SQL state HYT00)");
    }
  }

  /// tx-A locks row 1 then row 2; tx-B locks row 2 then row 1. The latch guarantees both
  /// hold their first lock before either asks for its second, so the cycle always forms.
  static void oppositeOrder(Db db) throws Exception {
    CountDownLatch bothHoldFirstLock = new CountDownLatch(2);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<String> a = executor.submit(() -> transfer(db, "tx-A", 1, 2, bothHoldFirstLock));
      Future<String> b = executor.submit(() -> transfer(db, "tx-B", 2, 1, bothHoldFirstLock));

      System.out.println("tx-A: " + a.get());
      System.out.println("tx-B: " + b.get());
    }
  }

  /// Fix: both transactions lock in ascending id order, so no cycle can form and both
  /// commit. Note this is the SAME two logical transfers as above.
  ///
  /// No barrier here, and that is not an oversight. Once both transactions want the lower
  /// id first, the second one blocks on its FIRST lock, so it can never reach a barrier
  /// that waits for it. Under ordered locking the transactions simply queue -- which is
  /// the point.
  static void orderedLocking(Db db) throws Exception {
    System.out.println("--- same two transfers, but always locking the lower id first ---");

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      // Math.min/max is the whole fix: the logical direction of the transfer no longer
      // determines the lock order.
      Future<String> a = executor.submit(() -> transfer(db, "tx-A", 1, 2, null, true));
      Future<String> b = executor.submit(() -> transfer(db, "tx-B", 2, 1, null, true));

      System.out.println("tx-A: " + a.get());
      System.out.println("tx-B: " + b.get());
    }
  }

  static String transfer(Db db, String name, int from, int to, CountDownLatch gate) {
    return transfer(db, name, from, to, gate, false);
  }

  /// `gate` forces the interleaving that guarantees a deadlock; pass null to let the two
  /// transactions run without being pinned against each other.
  ///
  /// Note how the ordered branch separates ACQUIRING the locks from APPLYING the transfer.
  /// That separation is the point: lock ordering must not change what the transaction
  /// means. Debiting whichever row happened to be locked first would silently reverse half
  /// the transfers.
  static String transfer(Db db, String name, int from, int to, CountDownLatch gate,
      boolean orderLocks) {
    Connection tx = null;
    try {
      tx = db.openTransaction(5_000);

      if (orderLocks) {
        // Acquire in a consistent global order (ascending id), regardless of the direction
        // money is moving. No cycle can form, so no deadlock is possible.
        int firstLock = Math.min(from, to);
        int secondLock = Math.max(from, to);

        lockRow(tx, firstLock);
        Db.log(name + " locked row " + firstLock);
        passGate(gate);
        lockRow(tx, secondLock);
        Db.log(name + " locked row " + secondLock);
      } else {
        // The buggy version: lock order follows the transfer direction, so two opposite
        // transfers acquire the same two rows in opposite orders.
        addToBalance(tx, from, -10);
        Db.log(name + " locked row " + from);
        passGate(gate);
        addToBalance(tx, to, +10);
        Db.log(name + " locked row " + to);

        tx.commit();
        tx.close();
        return "committed";
      }

      // Both rows are locked, so the transfer itself cannot race anybody.
      addToBalance(tx, from, -10);
      addToBalance(tx, to, +10);

      tx.commit();
      tx.close();
      return "committed";
    } catch (SQLException e) {
      Db.rollbackQuietly(tx);
      if (e.getErrorCode() == Db.DEADLOCK) {
        // This is the transaction the database chose to sacrifice. It has already been
        // rolled back, so the correct response is to retry the whole unit of work.
        return "DEADLOCK VICTIM, rolled back by the database (errorCode "
            + e.getErrorCode() + ", state " + e.getSQLState() + ")";
      }
      if (e.getErrorCode() == Db.LOCK_TIMEOUT) {
        return "lock timeout (errorCode " + e.getErrorCode() + ")";
      }
      return "failed: " + e.getMessage().split("\n")[0];
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Db.rollbackQuietly(tx);
      return "interrupted";
    }
  }

  static void passGate(CountDownLatch gate) throws InterruptedException {
    if (gate != null) {
      gate.countDown();
      gate.await(5, TimeUnit.SECONDS);
    }
  }

  static void lockRow(Connection connection, int id) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT balance FROM account WHERE id = ? FOR UPDATE")) {
      statement.setInt(1, id);
      try (var rows = statement.executeQuery()) {
        rows.next();
      }
    }
  }

  static void addToBalance(Connection connection, int id, int delta) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "UPDATE account SET balance = balance + ? WHERE id = ?")) {
      statement.setInt(1, delta);
      statement.setInt(2, id);
      statement.executeUpdate();
    }
  }
}
