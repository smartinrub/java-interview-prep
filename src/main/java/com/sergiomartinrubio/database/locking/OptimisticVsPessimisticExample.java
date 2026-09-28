package com.sergiomartinrubio.database.locking;

import com.sergiomartinrubio.database.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/// The same workload under both strategies, at low and high contention.
///
/// Both are correct. The question is always which one is cheaper for YOUR access pattern,
/// so the useful answer in an interview is a trade-off, not a winner.
///
/// Interview questions:
/// - Q: Which would you choose?
/// - Q: What happens to each as contention rises?
/// - Q: Can you use both together?
///
/// Run: java -cp "target/classes:target/deps/*"
///   com.sergiomartinrubio.database.locking.OptimisticVsPessimisticExample
public class OptimisticVsPessimisticExample {

  /// Each attempt does a little application work between reading and writing, because a
  /// real transaction does. Without it an in-memory database is so fast that the two
  /// strategies look identical, which hides the entire trade-off.
  private static final long THINK_MILLIS = 2;

  record Result(String strategy, long millis, int wasted, int actual, int expected) {

    void print() {
      System.out.printf("  %-12s %,6d ms   wasted work: %-4d  balance: %,6d  %s%n",
          strategy, millis, wasted, actual, actual == expected ? "ok" : "WRONG");
    }
  }

  static void main() throws Exception {
    // A: it comes down to the probability that two transactions touch the same row at the
    //    same time.
    //
    //      Low contention  -> optimistic. Conflicts are rare, so you almost never pay the
    //                         retry, and you never pay for locking or waiting at all.
    //      High contention -> pessimistic. Every collision under optimistic locking is
    //                         wasted work that has to be redone, and the waste grows with
    //                         the number of writers.
    System.out.println("Each run: N writers each deposit 100, onto one starting balance of 100.");

    try (Db db = Db.accounts("comparison")) {
      System.out.println("Each attempt also does " + THINK_MILLIS
          + "ms of application work between the read and the write.");

      System.out.println("\nLOW contention - 8 writers spread over 8 different rows:");
      seedRows(db, 8);
      lowContention(db, true).print();
      seedRows(db, 8);
      lowContention(db, false).print();

      System.out.println("\nHIGH contention - 16 writers all fighting over row 1:");
      db.reset();
      highContention(db, true, 16).print();
      db.reset();
      highContention(db, false, 16).print();

      System.out.println();
      // A: read the "wasted work" column, not the clock. With 16 writers on one row,
      //    optimistic redoes the read-modify-write around 120 times to commit 16 updates,
      //    while pessimistic wastes nothing. The retries grow faster than the writer
      //    count, and in the worst case a writer can starve, losing every race it enters.
      //
      //    Note the two totals finish in about the same wall-clock time here. That is worth
      //    understanding rather than glossing over: the retries run in parallel, so on an
      //    idle machine they cost throughput capacity (CPU, connections, database round
      //    trips) rather than latency. Under real load that capacity is exactly what you
      //    have run out of, which is when the wasted work turns into slow requests.
      //
      //    Pessimistic degrades predictably instead: it forms a queue. Throughput on the
      //    hot row is capped at one transaction at a time, nothing is recomputed, and
      //    latency is roughly queue depth times transaction duration.
      //
      // A: yes, and it is common. Pessimistic locking cannot span a user's think time,
      //    because the lock dies with the transaction. So a typical web application uses
      //      - optimistic (@Version) across the request boundary: load, edit, save
      //      - pessimistic inside a single short transaction that must not be retried,
      //        such as decrementing stock or allocating a seat
      //
      //    Decision guide:
      //      read-mostly, conflicts rare               -> optimistic
      //      edit spans multiple requests              -> optimistic (the only option)
      //      retry is unsafe or side-effecting         -> pessimistic
      //      short transaction on a hot row            -> pessimistic
      //      you need to hand out work to N consumers  -> pessimistic + SKIP LOCKED
      //      the update needs no prior read            -> neither; one atomic statement:
      //                                                  UPDATE ... SET balance = balance + 100
      atomicUpdateBeatsBoth(db);
    }
  }

  static void seedRows(Db db, int rows) {
    db.execute("DELETE FROM account");
    StringBuilder sql = new StringBuilder("INSERT INTO account (id, owner, balance, version) VALUES ");
    for (int i = 1; i <= rows; i++) {
      sql.append(i > 1 ? ", " : "").append("(").append(i).append(", 'owner").append(i)
          .append("', 100, 0)");
    }
    db.execute(sql.toString());
  }

  /// Each writer owns its own row, so conflicts are structurally impossible.
  static Result lowContention(Db db, boolean optimistic) throws Exception {
    int writers = 8;
    AtomicInteger wasted = new AtomicInteger();
    long start = System.nanoTime();

    try (ExecutorService executor = Executors.newFixedThreadPool(writers)) {
      for (int i = 1; i <= writers; i++) {
        int row = i;
        executor.submit(() -> {
          runOne(db, row, optimistic, wasted);
          return null;
        });
      }
    }

    long millis = (System.nanoTime() - start) / 1_000_000;
    // One writer per row, so every row should have gone from 100 to 200.
    return new Result(optimistic ? "optimistic" : "pessimistic", millis, wasted.get(),
        db.balanceOf(1), 200);
  }

  /// Every writer targets row 1, so every pair of them conflicts.
  static Result highContention(Db db, boolean optimistic, int writers) throws Exception {
    AtomicInteger wasted = new AtomicInteger();
    long start = System.nanoTime();

    try (ExecutorService executor = Executors.newFixedThreadPool(writers)) {
      for (int i = 0; i < writers; i++) {
        executor.submit(() -> {
          runOne(db, 1, optimistic, wasted);
          return null;
        });
      }
    }

    long millis = (System.nanoTime() - start) / 1_000_000;
    return new Result(optimistic ? "optimistic" : "pessimistic", millis, wasted.get(),
        db.balanceOf(1), 100 + writers * 100);
  }

  static void runOne(Db db, int id, boolean optimistic, AtomicInteger wasted) {
    if (optimistic) {
      optimisticDeposit(db, id, wasted);
    } else {
      pessimisticDeposit(db, id, wasted);
    }
  }

  static void optimisticDeposit(Db db, int id, AtomicInteger wasted) {
    try (Connection connection = db.open()) {
      for (int attempt = 0; attempt < 500; attempt++) {
        int[] row = readBalanceAndVersion(connection, id);
        think();
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE account SET balance = ?, version = version + 1
             WHERE id = ? AND version = ?""")) {
          statement.setInt(1, row[0] + 100);
          statement.setInt(2, id);
          statement.setInt(3, row[1]);
          if (statement.executeUpdate() == 1) {
            return;
          }
        }
        wasted.incrementAndGet();
      }
      throw new IllegalStateException("optimistic retries exhausted");
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  static void pessimisticDeposit(Db db, int id, AtomicInteger wasted) {
    Connection connection = null;
    try {
      connection = db.openTransaction(30_000);
      int balance;
      try (PreparedStatement select = connection.prepareStatement(
          "SELECT balance FROM account WHERE id = ? FOR UPDATE")) {
        select.setInt(1, id);
        try (ResultSet rows = select.executeQuery()) {
          rows.next();
          balance = rows.getInt(1);
        }
      }
      think();
      try (PreparedStatement update = connection.prepareStatement(
          "UPDATE account SET balance = ? WHERE id = ?")) {
        update.setInt(1, balance + 100);
        update.setInt(2, id);
        update.executeUpdate();
      }
      connection.commit();
      connection.close();
    } catch (SQLException e) {
      Db.rollbackQuietly(connection);
      if (e.getErrorCode() == Db.LOCK_TIMEOUT) {
        wasted.incrementAndGet();
      } else {
        throw new IllegalStateException(e);
      }
    }
  }

  /// Stands in for whatever the application computes between the read and the write.
  static void think() {
    try {
      Thread.sleep(THINK_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static int[] readBalanceAndVersion(Connection connection, int id) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT balance, version FROM account WHERE id = ?")) {
      statement.setInt(1, id);
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return new int[] {rows.getInt("balance"), rows.getInt("version")};
      }
    }
  }

  /// The answer interviewers are often fishing for, and the one candidates skip: if the new
  /// value is a pure function of the old one, you do not need to read it into the
  /// application at all. A single statement is atomic, so there is no window to lose an
  /// update in, no version column and no lock held across a round trip.
  static void atomicUpdateBeatsBoth(Db db) throws Exception {
    db.reset();
    int writers = 16;
    long start = System.nanoTime();

    try (ExecutorService executor = Executors.newFixedThreadPool(writers)) {
      for (int i = 0; i < writers; i++) {
        executor.submit(() -> {
          try (Connection connection = db.open();
               PreparedStatement statement = connection.prepareStatement(
                   "UPDATE account SET balance = balance + 100 WHERE id = 1")) {
            statement.executeUpdate();
          }
          return null;
        });
      }
    }

    long millis = (System.nanoTime() - start) / 1_000_000;
    System.out.printf("SAME 16 writers via one atomic UPDATE: %,d ms, balance = %,d (expected 1700)%n",
        millis, db.balanceOf(1));
    System.out.println("No version column, no FOR UPDATE, no retry loop. Prefer this when it fits.");
  }
}
