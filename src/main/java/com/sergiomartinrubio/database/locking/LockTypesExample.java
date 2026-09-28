package com.sergiomartinrubio.database.locking;

import com.sergiomartinrubio.database.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/// What kinds of locks a database takes, beyond the optimistic/pessimistic choice.
///
/// Interview questions:
/// - Q: Shared vs exclusive lock?
/// - Q: Does a reader block a writer?
/// - Q: What lock granularities exist?
/// - Q: Why can a missing index turn a row lock into something much wider?
///
/// Everything printed below was produced by this program against H2 2.3.232. Where H2
/// cannot demonstrate something, that is stated explicitly rather than implied.
///
/// Run: java -cp "target/classes:target/deps/*"
///   com.sergiomartinrubio.database.locking.LockTypesExample
public class LockTypesExample {

  static void main() throws Exception {
    try (Db db = Db.accounts("lock_types")) {
      writerBlocksWriter(db);

      db.reset();
      System.out.println();
      readerDoesNotBlockWriter(db);

      db.reset();
      System.out.println();
      differentRowsDoNotContend(db);

      System.out.println();
      sharedVersusExclusive();
      granularity();
    }
  }

  /// Exclusive (X) vs exclusive: the only genuinely blocking combination in an MVCC engine.
  static void writerBlocksWriter(Db db) throws Exception {
    System.out.println("--- exclusive vs exclusive: blocks ---");
    CountDownLatch holds = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      executor.submit(() -> {
        Connection tx = db.openTransaction(5_000);
        try {
          update(tx, 1, 500);
          Db.log("tx-1 holds an exclusive lock on row 1 (uncommitted)");
          holds.countDown();
          done.await(5, TimeUnit.SECONDS);
          tx.rollback();
          tx.close();
        } catch (Exception e) {
          Db.rollbackQuietly(tx);
        }
        return null;
      });

      executor.submit(() -> {
        holds.await();
        Connection tx = db.openTransaction(300);
        try {
          update(tx, 1, 600);
          Db.log("tx-2 wrote too -- would mean no exclusive lock");
        } catch (SQLException e) {
          Db.log("tx-2 blocked, then errorCode=" + e.getErrorCode()
              + " (only one writer may hold a row)");
        } finally {
          Db.rollbackQuietly(tx);
          done.countDown();
        }
        return null;
      });
    }
  }

  /// A: in an MVCC database, no. Readers see the last committed version of the row while
  ///    the writer's uncommitted change sits in its own version, so neither waits for the
  ///    other. This is why "readers block writers" is wrong for Postgres, Oracle, MySQL
  ///    InnoDB and H2, but was true of older lock-based engines.
  static void readerDoesNotBlockWriter(Db db) throws Exception {
    System.out.println("--- plain read vs uncommitted write: no blocking (MVCC) ---");
    Connection writer = db.openTransaction(5_000);
    try {
      update(writer, 1, 777);
      Db.log("writer changed row 1 to 777 but has NOT committed");

      long start = System.nanoTime();
      int seen = db.balanceOf(1);
      long millis = (System.nanoTime() - start) / 1_000_000;

      Db.log("reader returned " + seen + " in " + millis + "ms without waiting");
      Db.log("  -> it read the last COMMITTED version, not the uncommitted 777");
      writer.rollback();
    } finally {
      Db.rollbackQuietly(writer);
    }
  }

  /// Row-level granularity: two writers on different rows never contend.
  static void differentRowsDoNotContend(Db db) throws Exception {
    System.out.println("--- two writers, different rows: no contention ---");
    Connection tx1 = db.openTransaction(1_000);
    Connection tx2 = db.openTransaction(1_000);
    try {
      update(tx1, 1, 111);
      update(tx2, 2, 222);
      Db.log("both writers succeeded concurrently -> locks are per row, not per table");
      tx1.commit();
      tx2.commit();
    } finally {
      Db.rollbackQuietly(tx1);
      Db.rollbackQuietly(tx2);
    }
    System.out.println("row 1 = " + db.balanceOf(1) + ", row 2 = " + db.balanceOf(2));
  }

  /// A: a shared (S) lock permits other readers but excludes writers; an exclusive (X)
  ///    lock excludes everyone. The compatibility matrix is the thing to be able to draw:
  ///
  ///              held S     held X
  ///      want S     ok       wait
  ///      want X    wait      wait
  ///
  ///    So S locks are compatible with each other and nothing else. A shared lock is what
  ///    you take when you must guarantee a row does not change before you commit, but you
  ///    are not going to change it yourself (checking a foreign key still exists,
  ///    validating a balance before inserting elsewhere).
  static void sharedVersusExclusive() {
    System.out.println("--- shared (S) vs exclusive (X) ---");
    System.out.println("  compatibility:  want S + held S = ok, everything else waits");
    System.out.println("  exclusive:  SELECT ... FOR UPDATE          (all major engines)");
    System.out.println("  shared:     SELECT ... FOR SHARE           (Postgres)");
    System.out.println("              SELECT ... LOCK IN SHARE MODE  (MySQL)");
    System.out.println("              LockModeType.PESSIMISTIC_READ  (JPA)");
    // NOT demonstrated: H2 2.3.232 rejects FOR SHARE with a syntax error (SQL state 42000),
    // so there is no way to show an S lock here. Verified, not assumed.
    System.out.println("  H2 2.3.232 does not support FOR SHARE, so this one is described,");
    System.out.println("  not demonstrated -- it rejects the syntax outright.");
  }

  /// A: engines lock at several granularities, coarser ones trading concurrency for
  ///    bookkeeping cost.
  static void granularity() {
    System.out.println("--- granularity ---");
    System.out.println("  row    - the normal case; two writers on different rows never contend");
    System.out.println("  page   - a disk page of rows; a neighbour's update can block you");
    System.out.println("  table  - DDL, some bulk operations, and lock escalation");
    System.out.println("  predicate/gap - locks a RANGE that does not exist yet, which is how");
    System.out.println("           SERIALIZABLE and MySQL's REPEATABLE READ stop phantom rows");
    System.out.println();
    // A: because the lock is taken on whatever the engine had to examine, not on what the
    //    statement logically matched. UPDATE ... WHERE email = ? with no index on email
    //    forces a full scan, and a scan visits every row, so it can lock every row it
    //    touches. The same statement with an index on email locks one. This is a very
    //    common production incident: the query was always slow, and then it started
    //    blocking everything else too.
    System.out.println("  A missing index widens a lock: an unindexed WHERE scans the table,");
    System.out.println("  and locks tend to follow whatever rows the engine examined. Index the");
    System.out.println("  predicate and the same UPDATE locks one row instead of the table.");
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
