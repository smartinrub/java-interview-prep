package com.sergiomartinrubio.database.locking;

import com.sergiomartinrubio.database.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/// Pessimistic locking: take an exclusive lock when you read, so nobody else can read it
/// to write until you commit.
///
/// "Pessimistic" because it assumes conflict is likely and pays up front to prevent it.
///
/// Interview questions:
/// - Q: How does pessimistic locking work?
/// - Q: How long is the lock held?
/// - Q: What are the risks?
/// - Q: What is `NOWAIT` / `SKIP LOCKED` for?
/// - Q: How does JPA express this?
///
/// Run: java -cp "target/classes:target/deps/*"
///   com.sergiomartinrubio.database.locking.PessimisticLockingExample
public class PessimisticLockingExample {

  static void main() throws Exception {
    try (Db db = Db.accounts("pessimistic")) {
      // A: you read with `SELECT ... FOR UPDATE`, which takes an exclusive row lock. A
      //    second transaction issuing the same statement BLOCKS until the first one
      //    commits or rolls back. The read-modify-write is then serialised, so there is
      //    no stale copy to lose.
      secondReaderWaits(db);

      db.reset();
      System.out.println();
      noUpdateIsLost(db);

      db.reset();
      System.out.println();
      lockTimeout(db);

      System.out.println();
      // A: until the transaction ends. Not until the statement ends: the lock is released
      //    by COMMIT or ROLLBACK. That is why a long transaction is so damaging here, and
      //    why you must never wait for a user, a remote API or a file upload while holding
      //    one. Keep the transaction as short as the correctness requirement allows.
      //
      // A: the risks, all of them queueing-related:
      //      - reduced throughput: writers to a hot row are serialised by construction
      //      - deadlock if two transactions lock the same rows in different orders
      //        (see DatabaseDeadlockExample)
      //      - connection-pool exhaustion: blocked transactions hold their connection,
      //        so a slow lock holder can stall the whole application
      //      - it does not survive a round trip to the user; the lock dies with the
      //        transaction, so it cannot protect a multi-request edit workflow.
      //        That case needs optimistic locking.
      //
      // A: in JPA, em.find(Account.class, id, LockModeType.PESSIMISTIC_WRITE), which
      //    generates FOR UPDATE. PESSIMISTIC_READ asks for a shared lock where the
      //    dialect supports one. A javax.persistence.lock.timeout hint maps to the
      //    dialect's NOWAIT or timeout syntax.
    }
  }

  /// Times the second transaction to prove it really waited for the first.
  static void secondReaderWaits(Db db) throws Exception {
    System.out.println("--- SELECT ... FOR UPDATE serialises two readers ---");
    CountDownLatch firstHoldsLock = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      executor.submit(() -> {
        Connection connection = db.openTransaction(5_000);
        try {
          int balance = selectForUpdate(connection, 1);
          Db.log("tx-1 locked row 1, balance = " + balance);
          firstHoldsLock.countDown();

          // Holding the lock deliberately, to make the second transaction's wait visible.
          TimeUnit.MILLISECONDS.sleep(400);

          update(connection, 1, balance + 100);
          connection.commit();
          Db.log("tx-1 committed, lock released");
          connection.close();
        } catch (Exception e) {
          Db.rollbackQuietly(connection);
          throw new IllegalStateException(e);
        }
        return null;
      });

      executor.submit(() -> {
        firstHoldsLock.await();
        Connection connection = db.openTransaction(5_000);
        try {
          long start = System.nanoTime();
          Db.log("tx-2 requesting the same row FOR UPDATE...");
          int balance = selectForUpdate(connection, 1);
          long waitedMillis = (System.nanoTime() - start) / 1_000_000;

          // The value read here is tx-1's committed value, not the original.
          Db.log("tx-2 acquired the lock after waiting " + waitedMillis
              + "ms, balance = " + balance);
          update(connection, 1, balance + 100);
          connection.commit();
          connection.close();
        } catch (Exception e) {
          Db.rollbackQuietly(connection);
          throw new IllegalStateException(e);
        }
        return null;
      });
    }

    System.out.println("balance = " + db.balanceOf(1)
        + " (expected 300: the second reader saw the first one's committed value)");
  }

  /// The same 8 concurrent deposits the optimistic example runs, for comparison.
  /// Every one succeeds first time, because they queue instead of colliding.
  static void noUpdateIsLost(Db db) throws Exception {
    System.out.println("--- 8 concurrent deposits, all serialised ---");
    int writers = 8;
    AtomicInteger timeouts = new AtomicInteger();

    try (ExecutorService executor = Executors.newFixedThreadPool(writers)) {
      for (int i = 0; i < writers; i++) {
        executor.submit(() -> {
          Connection connection = db.openTransaction(10_000);
          try {
            int balance = selectForUpdate(connection, 1);
            update(connection, 1, balance + 100);
            connection.commit();
            connection.close();
          } catch (SQLException e) {
            Db.rollbackQuietly(connection);
            if (e.getErrorCode() == Db.LOCK_TIMEOUT) {
              timeouts.incrementAndGet();
            } else {
              throw new IllegalStateException(e);
            }
          }
          return null;
        });
      }
    }

    System.out.println("balance = " + db.balanceOf(1) + " (expected 900)");
    System.out.println("lock timeouts = " + timeouts.get()
        + ", retries = 0 -- the waiting happened inside the database");
  }

  /// A: a lock request is not free to wait forever, so you bound it. H2 uses
  ///    `SET LOCK_TIMEOUT`; Postgres and Oracle offer `FOR UPDATE NOWAIT` to fail
  ///    instantly, and `FOR UPDATE SKIP LOCKED` to step over locked rows instead of
  ///    waiting, which is how you build a work queue that several consumers can poll
  ///    without handing each other the same job.
  static void lockTimeout(Db db) throws Exception {
    System.out.println("--- what happens when the wait is too long ---");
    CountDownLatch holderReady = new CountDownLatch(1);
    CountDownLatch waiterDone = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      executor.submit(() -> {
        Connection connection = db.openTransaction(5_000);
        try {
          selectForUpdate(connection, 1);
          Db.log("holder locked row 1 and is sitting on it");
          holderReady.countDown();
          waiterDone.await(5, TimeUnit.SECONDS);
          connection.rollback();
          connection.close();
        } catch (Exception e) {
          Db.rollbackQuietly(connection);
        }
        return null;
      });

      executor.submit(() -> {
        holderReady.await();
        // A deliberately tiny timeout, so the failure is quick and visible.
        Connection connection = db.openTransaction(300);
        try {
          selectForUpdate(connection, 1);
          Db.log("waiter unexpectedly got the lock");
        } catch (SQLException e) {
          Db.log("waiter failed with errorCode=" + e.getErrorCode()
              + " sqlState=" + e.getSQLState());
          Db.log("  -> " + e.getMessage().split("\n")[0]);
          Db.log("  This is the error to catch and turn into a retry or a 503.");
        } finally {
          Db.rollbackQuietly(connection);
          waiterDone.countDown();
        }
        return null;
      });
    }
  }

  /// The entire mechanism: two words at the end of a SELECT.
  static int selectForUpdate(Connection connection, int id) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT balance FROM account WHERE id = ? FOR UPDATE")) {
      statement.setInt(1, id);
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt("balance");
      }
    }
  }

  static void update(Connection connection, int id, int balance) throws SQLException {
    try (PreparedStatement statement =
             connection.prepareStatement("UPDATE account SET balance = ? WHERE id = ?")) {
      statement.setInt(1, balance);
      statement.setInt(2, id);
      statement.executeUpdate();
    }
  }
}
