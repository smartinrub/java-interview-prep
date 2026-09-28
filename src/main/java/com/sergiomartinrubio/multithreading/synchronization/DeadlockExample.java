package com.sergiomartinrubio.multithreading.synchronization;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/// Deadlock, and the two ways out of it.
///
/// Interview questions:
/// - Q: What is a deadlock? What conditions does it need?
/// - Q: How do you prevent one?
/// - Q: Deadlock vs livelock vs starvation?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.synchronization.DeadlockExample
public class DeadlockExample {

  private static final Object LOCK_A = new Object();
  private static final Object LOCK_B = new Object();

  static void main() throws InterruptedException {
    // A: two threads each hold a lock the other needs, and neither will let go.
    //    It needs all four Coffman conditions at once: mutual exclusion, hold-and-wait,
    //    no pre-emption, and circular wait. Break any one and deadlock is impossible.
    //
    //    The classic shape, NOT run here because it would hang forever:
    //      thread 1: synchronized (A) { synchronized (B) { } }
    //      thread 2: synchronized (B) { synchronized (A) { } }   // reversed order
    //
    //    To see it live: uncomment the line below, run, then `jstack <pid>`
    //    or jcmd <pid> Thread.print - the JVM detects and names Java-level deadlocks.
    // deadlock();

    orderedLocking();
    timedLocking();

    // A: deadlock  - nobody progresses, everybody is blocked forever
    //    livelock  - threads keep running and keep reacting to each other, but no work
    //                gets done (two people stepping aside in the same direction)
    //    starvation - a thread is runnable but never scheduled or never wins the lock;
    //                a fair lock (new ReentrantLock(true)) trades throughput to avoid it
  }

  /// Fix 1: impose a global lock ordering. Every thread takes A then B, never B then A,
  /// so the circular wait cannot form. This is the primary fix in real systems.
  static void orderedLocking() throws InterruptedException {
    Runnable task = () -> {
      synchronized (LOCK_A) {
        sleep(50);
        synchronized (LOCK_B) {
          log("acquired A then B");
        }
      }
    };

    Thread one = new Thread(task, "ordered-1");
    Thread two = new Thread(task, "ordered-2");
    one.start();
    two.start();
    one.join();
    two.join();
  }

  /// Fix 2: `tryLock` with a timeout. You cannot wait forever, so you back out and
  /// retry instead of hanging. Note both locks must be released on any failure path.
  static void timedLocking() throws InterruptedException {
    ReentrantLock first = new ReentrantLock();
    ReentrantLock second = new ReentrantLock();

    Runnable task = () -> {
      String name = Thread.currentThread().getName();
      // Deliberately opposite acquisition orders: with plain lock() this would deadlock.
      ReentrantLock outer = name.endsWith("1") ? first : second;
      ReentrantLock inner = name.endsWith("1") ? second : first;

      for (int attempt = 1; attempt <= 5; attempt++) {
        try {
          if (outer.tryLock(100, TimeUnit.MILLISECONDS)) {
            try {
              if (inner.tryLock(100, TimeUnit.MILLISECONDS)) {
                try {
                  log("got both locks on attempt " + attempt);
                  return;
                } finally {
                  inner.unlock();
                }
              }
            } finally {
              outer.unlock();
            }
          }
          log("backing off after attempt " + attempt);
          sleep(20);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
      log("gave up");
    };

    Thread one = new Thread(task, "timed-1");
    Thread two = new Thread(task, "timed-2");
    one.start();
    two.start();
    one.join();
    two.join();
  }

  /// Hangs forever by design. Kept for reference; see the comment in main().
  @SuppressWarnings("unused")
  static void deadlock() throws InterruptedException {
    Thread one = new Thread(() -> {
      synchronized (LOCK_A) {
        sleep(100);
        synchronized (LOCK_B) {
          log("unreachable");
        }
      }
    }, "deadlock-1");

    Thread two = new Thread(() -> {
      synchronized (LOCK_B) {
        sleep(100);
        synchronized (LOCK_A) {
          log("unreachable");
        }
      }
    }, "deadlock-2");

    one.start();
    two.start();
    one.join();
    two.join();
  }

  static void sleep(long millis) {
    try {
      TimeUnit.MILLISECONDS.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static void log(String message) {
    System.out.println("[" + Thread.currentThread().getName() + "] " + message);
  }
}
