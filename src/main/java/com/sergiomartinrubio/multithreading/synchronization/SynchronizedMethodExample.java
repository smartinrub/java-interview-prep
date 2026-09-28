package com.sergiomartinrubio.multithreading.synchronization;

/// `synchronized` on a method: which object is the lock?
///
/// Interview questions:
/// - Q: What does a `synchronized` method lock on?
/// - Q: Do two threads calling different `synchronized` methods of the same object block?
/// - Q: What about a `static synchronized` method?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.synchronization.SynchronizedMethodExample
public class SynchronizedMethodExample {

  static void main() throws InterruptedException {
    Account account = new Account(100);

    Thread depositor = new Thread(() -> {
      for (int i = 0; i < 1_000; i++) {
        account.deposit(1);
      }
    });
    Thread withdrawer = new Thread(() -> {
      for (int i = 0; i < 1_000; i++) {
        account.withdraw(1);
      }
    });

    depositor.start();
    withdrawer.start();
    depositor.join();
    withdrawer.join();

    System.out.println("balance = " + account.balance() + " (expected 100)");
    System.out.println("audited operations = " + Account.operations());
  }

  static class Account {

    private static int operations;
    private int balance;

    Account(int balance) {
      this.balance = balance;
    }

    /// A: an instance method locks on `this`. So this is exactly equivalent to
    ///    wrapping the body in `synchronized (this) { ... }`.
    synchronized void deposit(int amount) {
      balance += amount;
      countOperation();
    }

    /// A: yes, they block each other. There is ONE lock per object, not one per method,
    ///    so all synchronized instance methods of the same object are mutually exclusive.
    ///    Different objects do not contend: each has its own lock.
    synchronized void withdraw(int amount) {
      balance -= amount;
      countOperation();
    }

    synchronized int balance() {
      return balance;
    }

    /// A: a static synchronized method locks on the CLASS object (Account.class),
    ///    which is a different lock from any instance's. A common bug is mixing the two
    ///    and assuming they exclude each other. They do not.
    static synchronized void countOperation() {
      operations++;
    }

    static synchronized int operations() {
      return operations;
    }
  }
}
