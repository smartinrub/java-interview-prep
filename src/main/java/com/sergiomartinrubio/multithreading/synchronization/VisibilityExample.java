package com.sergiomartinrubio.multithreading.synchronization;

import java.util.concurrent.TimeUnit;

/// `volatile`: the other half of thread safety, and the half people forget.
///
/// Interview questions:
/// - Q: What does `volatile` guarantee?
/// - Q: Is `volatile` enough to make a counter thread-safe?
/// - Q: When is `volatile` the right tool?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.synchronization.VisibilityExample
public class VisibilityExample {

  /// Without `volatile` the JVM may hoist this read out of the loop below, and the
  /// worker can spin forever after main has already set it to true.
  private static volatile boolean stopRequested;

  private static volatile int volatileCounter;

  static void main() throws InterruptedException {
    // A: visibility and ordering, NOT atomicity.
    //    - a write is published to all threads (no caching in a register or core cache)
    //    - reads/writes are not reordered across it
    //    - it never makes a read-modify-write atomic
    Thread worker = new Thread(() -> {
      long spins = 0;
      while (!stopRequested) {
        spins++;
      }
      System.out.println("worker observed the flag after " + spins + " spins");
    }, "worker");

    worker.start();
    TimeUnit.MILLISECONDS.sleep(100);
    stopRequested = true;
    worker.join();

    // A: no. volatile++ is still read / add / write, so increments are still lost.
    //    volatile fixes visibility; it does nothing for mutual exclusion.
    Runnable increments = () -> {
      for (int i = 0; i < 100_000; i++) {
        volatileCounter++;
      }
    };
    Thread a = new Thread(increments);
    Thread b = new Thread(increments);
    a.start();
    b.start();
    a.join();
    b.join();

    System.out.println("volatile counter = " + volatileCounter + " (expected 200000)");
    System.out.println("-> volatile is not a substitute for AtomicInteger or a lock");

    // A: use volatile for a flag or a reference that one thread writes and others read,
    //    where the new value does not depend on the old one. Anything else wants an
    //    Atomic class or a lock.
  }
}
