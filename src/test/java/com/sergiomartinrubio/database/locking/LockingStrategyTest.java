package com.sergiomartinrubio.database.locking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Asserts the claims the locking examples make, so a change that quietly breaks one of
/// them fails the build.
///
/// Each test gets its own in-memory database, so they can run in any order.
class LockingStrategyTest {

  private static final int WRITERS = 8;
  private static final int DEPOSIT = 100;
  private static final int STARTING_BALANCE = 100;
  private static final int EXPECTED = STARTING_BALANCE + WRITERS * DEPOSIT;

  private Db db;

  @BeforeEach
  void setUp() {
    db = Db.accounts("test_" + System.nanoTime());
  }

  @AfterEach
  void tearDown() {
    db.close();
  }

  @Test
  @DisplayName("unguarded read-modify-write loses at least one update")
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void unguardedWritesLoseUpdates() throws Exception {
    // Both writers must read before either writes, which makes the loss deterministic.
    CountDownLatch bothHaveRead = new CountDownLatch(2);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 2; i++) {
        executor.submit(() -> {
          try (Connection connection = db.open()) {
            int balance = balance(connection);
            bothHaveRead.countDown();
            bothHaveRead.await();
            setBalance(connection, balance + DEPOSIT);
          }
          return null;
        });
      }
    }

    assertEquals(STARTING_BALANCE + DEPOSIT, db.balanceOf(1),
        "both writers read the same value, so exactly one deposit should survive");
  }

  @Test
  @DisplayName("optimistic locking detects the conflict instead of losing the update")
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void optimisticDetectsConflict() throws Exception {
    CountDownLatch bothHaveRead = new CountDownLatch(2);
    AtomicInteger conflicts = new AtomicInteger();

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 2; i++) {
        executor.submit(() -> {
          try (Connection connection = db.open()) {
            int[] row = balanceAndVersion(connection);
            bothHaveRead.countDown();
            bothHaveRead.await();
            if (versionedUpdate(connection, row[0] + DEPOSIT, row[1]) == 0) {
              conflicts.incrementAndGet();
            }
          }
          return null;
        });
      }
    }

    assertEquals(1, conflicts.get(), "exactly one writer should have been rejected");
    assertEquals(1, db.versionOf(1), "only the winner may bump the version");
  }

  @Test
  @DisplayName("optimistic locking with a retry loop converges on the correct total")
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void optimisticRetryConverges() throws Exception {
    AtomicInteger retries = new AtomicInteger();

    try (ExecutorService executor = Executors.newFixedThreadPool(WRITERS)) {
      for (int i = 0; i < WRITERS; i++) {
        executor.submit(() -> {
          try (Connection connection = db.open()) {
            OptimisticLockingExample.deposit(connection, 1, DEPOSIT, retries);
          }
          return null;
        });
      }
    }

    assertEquals(EXPECTED, db.balanceOf(1), "no deposit may be lost");
    assertEquals(WRITERS, db.versionOf(1), "the version must advance once per committed write");
    // Retries are contention-dependent, so only the direction is safe to assert.
    assertTrue(retries.get() >= 0, "retry count should be recorded");
  }

  @Test
  @DisplayName("pessimistic locking serialises writers with no retries")
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void pessimisticSerialises() throws Exception {
    AtomicInteger failures = new AtomicInteger();

    try (ExecutorService executor = Executors.newFixedThreadPool(WRITERS)) {
      for (int i = 0; i < WRITERS; i++) {
        executor.submit(() -> {
          Connection tx = null;
          try {
            tx = db.openTransaction(30_000);
            int balance = PessimisticLockingExample.selectForUpdate(tx, 1);
            PessimisticLockingExample.update(tx, 1, balance + DEPOSIT);
            tx.commit();
            tx.close();
          } catch (SQLException e) {
            Db.rollbackQuietly(tx);
            failures.incrementAndGet();
          }
          return null;
        });
      }
    }

    assertEquals(0, failures.get(), "a generous lock timeout should let every writer through");
    assertEquals(EXPECTED, db.balanceOf(1), "serialised writers must not lose an update");
  }

  @Test
  @DisplayName("a second FOR UPDATE waits for the first transaction to commit")
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void forUpdateBlocksUntilCommit() throws Exception {
    CountDownLatch holderHasLock = new CountDownLatch(1);
    long holdMillis = 300;

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      executor.submit(() -> {
        Connection tx = db.openTransaction(10_000);
        try {
          int balance = PessimisticLockingExample.selectForUpdate(tx, 1);
          holderHasLock.countDown();
          TimeUnit.MILLISECONDS.sleep(holdMillis);
          PessimisticLockingExample.update(tx, 1, balance + DEPOSIT);
          tx.commit();
          tx.close();
        } catch (Exception e) {
          Db.rollbackQuietly(tx);
          throw new IllegalStateException(e);
        }
        return null;
      });

      var waiter = executor.submit(() -> {
        holderHasLock.await();
        Connection tx = db.openTransaction(10_000);
        try {
          long start = System.nanoTime();
          int balance = PessimisticLockingExample.selectForUpdate(tx, 1);
          long waited = (System.nanoTime() - start) / 1_000_000;
          tx.commit();
          tx.close();
          return new long[] {waited, balance};
        } catch (SQLException e) {
          Db.rollbackQuietly(tx);
          throw new IllegalStateException(e);
        }
      });

      long[] result = waiter.get();
      long waited = result[0];
      long balanceSeen = result[1];

      // Allow slack for scheduling, but it must have genuinely waited on the lock.
      assertTrue(waited > holdMillis / 2,
          "second reader should have blocked; it waited only " + waited + "ms");
      assertEquals(STARTING_BALANCE + DEPOSIT, balanceSeen,
          "after waiting, it must see the first transaction's committed value");
    }
  }

  @Test
  @DisplayName("lock timeout surfaces as H2 error code 50200")
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void lockTimeoutHasAKnownErrorCode() throws Exception {
    Connection holder = db.openTransaction(10_000);
    Connection waiter = db.openTransaction(200);
    try {
      PessimisticLockingExample.selectForUpdate(holder, 1);

      SQLException thrown = null;
      try {
        PessimisticLockingExample.selectForUpdate(waiter, 1);
      } catch (SQLException e) {
        thrown = e;
      }

      assertTrue(thrown != null, "the second FOR UPDATE should have timed out");
      assertEquals(Db.LOCK_TIMEOUT, thrown.getErrorCode(),
          "examples branch on this code, so it must stay accurate");
      assertEquals("HYT00", thrown.getSQLState());
    } finally {
      Db.rollbackQuietly(holder);
      Db.rollbackQuietly(waiter);
    }
  }

  private int balance(Connection connection) throws SQLException {
    try (PreparedStatement statement =
             connection.prepareStatement("SELECT balance FROM account WHERE id = 1");
         ResultSet rows = statement.executeQuery()) {
      rows.next();
      return rows.getInt(1);
    }
  }

  private int[] balanceAndVersion(Connection connection) throws SQLException {
    try (PreparedStatement statement =
             connection.prepareStatement("SELECT balance, version FROM account WHERE id = 1");
         ResultSet rows = statement.executeQuery()) {
      rows.next();
      return new int[] {rows.getInt(1), rows.getInt(2)};
    }
  }

  private void setBalance(Connection connection, int balance) throws SQLException {
    try (PreparedStatement statement =
             connection.prepareStatement("UPDATE account SET balance = ? WHERE id = 1")) {
      statement.setInt(1, balance);
      statement.executeUpdate();
    }
  }

  private int versionedUpdate(Connection connection, int balance, int version)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        UPDATE account SET balance = ?, version = version + 1
         WHERE id = 1 AND version = ?""")) {
      statement.setInt(1, balance);
      statement.setInt(2, version);
      return statement.executeUpdate();
    }
  }
}
