package com.sergiomartinrubio.multithreading.locks;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/// A semaphore limits how many threads may do something at once.
///
/// Interview questions:
/// - Q: Semaphore vs Lock?
/// - Q: What is a binary semaphore, and how does it differ from a lock?
/// - Q: What are semaphores actually used for?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.locks.SemaphoreExample
public class SemaphoreExample {

  private static final int PERMITS = 3;

  static void main() throws InterruptedException {
    // A: a Lock allows exactly one holder and is owned by the acquiring thread.
    //    A Semaphore allows N and has no owner: any thread may release a permit,
    //    including one that never acquired it. It is a counter, not ownership.
    Semaphore semaphore = new Semaphore(PERMITS);

    try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
      for (int i = 1; i <= 8; i++) {
        int id = i;
        executor.submit(() -> callThrottledApi(semaphore, id));
      }
    }

    System.out.println("available permits back to " + semaphore.availablePermits());

    // A: a binary semaphore (1 permit) behaves like a mutex but is NOT reentrant and has
    //    no owner, so acquiring twice on one thread deadlocks it. Use a lock for mutual
    //    exclusion, a semaphore for capacity.
    //
    //    Real uses: bounding concurrent calls to a downstream service, a connection pool,
    //    limiting parallel file handles, simple rate limiting, throttling a batch job.

    // tryAcquire lets you shed load instead of queueing up unboundedly.
    Semaphore tight = new Semaphore(1);
    tight.acquire();
    System.out.println("tryAcquire when exhausted -> "
        + tight.tryAcquire(100, TimeUnit.MILLISECONDS));
    tight.release();
  }

  static void callThrottledApi(Semaphore semaphore, int id) {
    try {
      semaphore.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }
    try {
      System.out.println("request " + id + " running ("
          + (PERMITS - semaphore.availablePermits()) + "/" + PERMITS + " in flight)");
      TimeUnit.MILLISECONDS.sleep(200);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      // finally, exactly like unlock(): a leaked permit shrinks capacity permanently.
      semaphore.release();
    }
  }
}
