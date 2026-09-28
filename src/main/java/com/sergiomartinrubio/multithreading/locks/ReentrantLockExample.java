package com.sergiomartinrubio.multithreading.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/// Explicit locking: everything `synchronized` does, plus timeouts, interruption and fairness.
///
/// Interview questions:
/// - Q: Why use `ReentrantLock` over `synchronized`?
/// - Q: What does "reentrant" mean?
/// - Q: Why must `unlock()` be in a `finally` block?
/// - Q: What is a fair lock, and what does fairness cost?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.locks.ReentrantLockExample
public class ReentrantLockExample {

  private static final Lock LOCK = new ReentrantLock();

  static void main() throws InterruptedException {
    // A: ReentrantLock adds what synchronized cannot express:
    //    - tryLock()                       : take it or move on, never block
    //    - tryLock(timeout)                : bounded wait, the escape from deadlock
    //    - lockInterruptibly()             : cancellable waiting
    //    - lock/unlock across method bounds: acquire here, release there
    //    - optional fairness policy
    //    - newCondition(), and several conditions per lock
    //    synchronized is shorter and cannot leak a lock, so it stays the default.

    LOCK.lock();
    try {
      // A: if the body throws, finally still runs and the lock is released. Without it
      //    the lock is held forever and every other thread blocks: this is the single
      //    most common ReentrantLock bug, and the reason synchronized is safer by default.
      System.out.println("critical section");
      reentrantCall();
    } finally {
      LOCK.unlock();
    }

    tryLockExample();
    fairnessNote();
  }

  /// A: a reentrant lock can be re-acquired by the thread that already holds it, so a
  ///    synchronized/locked method can call another one without deadlocking itself.
  ///    The hold count increments, and the thread must unlock the same number of times.
  static void reentrantCall() {
    LOCK.lock();
    try {
      ReentrantLock reentrant = (ReentrantLock) LOCK;
      System.out.println("re-acquired the same lock, hold count = " + reentrant.getHoldCount());
    } finally {
      LOCK.unlock();
    }
  }

  static void tryLockExample() throws InterruptedException {
    ReentrantLock lock = new ReentrantLock();

    Thread holder = new Thread(() -> {
      lock.lock();
      try {
        sleep(300);
      } finally {
        lock.unlock();
      }
    }, "holder");
    holder.start();
    TimeUnit.MILLISECONDS.sleep(50);

    // Non-blocking attempt: useful when you would rather skip the work than wait.
    if (lock.tryLock()) {
      try {
        System.out.println("tryLock() succeeded");
      } finally {
        lock.unlock();
      }
    } else {
      System.out.println("tryLock() failed immediately, lock is held elsewhere");
    }

    // Bounded attempt: waits, but never forever.
    if (lock.tryLock(1, TimeUnit.SECONDS)) {
      try {
        System.out.println("tryLock(1s) succeeded after the holder released");
      } finally {
        lock.unlock();
      }
    }

    holder.join();
  }

  static void fairnessNote() {
    // A: a fair lock hands the lock to the longest-waiting thread instead of whoever
    //    happens to be scheduled. It removes starvation but costs real throughput,
    //    because barging (letting a running thread take a free lock immediately) is
    //    what makes the unfair default fast. Default is unfair; opt in only if you
    //    measured starvation.
    Lock fair = new ReentrantLock(true);
    fair.lock();
    try {
      System.out.println("fair lock acquired (FIFO ordering, slower under contention)");
    } finally {
      fair.unlock();
    }
  }

  static void sleep(long millis) {
    try {
      TimeUnit.MILLISECONDS.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
