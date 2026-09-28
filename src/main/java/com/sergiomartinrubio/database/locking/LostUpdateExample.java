package com.sergiomartinrubio.database.locking;

import com.sergiomartinrubio.database.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// The problem both optimistic and pessimistic locking exist to solve.
///
/// Interview questions:
/// - Q: What is a lost update?
/// - Q: Why doesn't the database prevent it on its own?
/// - Q: Why doesn't a higher isolation level fix it here?
/// - Q: What are the two families of fix?
///
/// Run: java -cp "target/classes:target/deps/*"
///   com.sergiomartinrubio.database.locking.LostUpdateExample
public class LostUpdateExample {

  static void main() throws Exception {
    try (Db db = Db.accounts("lost_update")) {
      // A: two transactions read the same row, each computes a new value from what it
      //    read, and both write back. The second write is based on a value that is already
      //    stale, so the first update is silently overwritten. Nothing errors. The money
      //    is just gone.
      //
      //    This is the read-modify-write cycle every CRUD screen performs: load a record,
      //    let the user edit it, save it back.
      readModifyWrite(db);

      System.out.println();
      System.out.println("balance = " + db.balanceOf(1) + ", expected 300 (100 + 100 + 100)");
      System.out.println("One deposit was lost, and no error was raised anywhere.");

      // A: because the database did exactly what it was told. Both UPDATE statements were
      //    valid, and each ran in its own transaction that committed successfully. The
      //    database cannot know that the second writer's value was computed from a row it
      //    had already changed. That intent lives in the application.
      //
      // A: a higher isolation level does not help, and this is the key insight.
      //    Isolation controls what a transaction can SEE. It does not stop two
      //    transactions from both being allowed to write. Under snapshot isolation the
      //    second writer would see an even more stale value. You need a rule about
      //    WRITING, which is what the two lock strategies provide:
      //
      //      pessimistic - take an exclusive lock on read, so the second reader waits
      //                    (see PessimisticLockingExample)
      //      optimistic  - do not lock, but refuse the write if the row changed since it
      //                    was read (see OptimisticLockingExample)
    }
  }

  /// Two threads each: read the balance, add 100, write it back. Exactly the shape of a
  /// web request that loads a form and saves it.
  ///
  /// The latch forces both reads to happen before either write, which is what makes the
  /// lost update reliable rather than occasional. In production the gap is the user's
  /// thinking time, and it is far wider than this.
  static void readModifyWrite(Db db) throws Exception {
    CountDownLatch bothHaveRead = new CountDownLatch(2);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 1; i <= 2; i++) {
        String name = "deposit-" + i;
        executor.submit(() -> {
          // Autocommit: the SELECT and the UPDATE are two SEPARATE transactions, so no
          // lock is held between them. This is the JDBC default.
          try (Connection connection = db.open()) {
            int balance = read(connection);
            Db.log(name + " read balance = " + balance);

            bothHaveRead.countDown();
            bothHaveRead.await();

            int updated = balance + 100;
            write(connection, updated);
            Db.log(name + " wrote balance = " + updated);
          } catch (SQLException | InterruptedException e) {
            throw new IllegalStateException(e);
          }
          return null;
        });
      }
    }
  }

  static int read(Connection connection) throws SQLException {
    try (PreparedStatement statement =
             connection.prepareStatement("SELECT balance FROM account WHERE id = 1");
         ResultSet rows = statement.executeQuery()) {
      rows.next();
      return rows.getInt("balance");
    }
  }

  static void write(Connection connection, int balance) throws SQLException {
    // The bug in one line: the new value is assigned outright, with no check that the row
    // still holds what this transaction read.
    try (PreparedStatement statement =
             connection.prepareStatement("UPDATE account SET balance = ? WHERE id = 1")) {
      statement.setInt(1, balance);
      statement.executeUpdate();
    }
  }
}
