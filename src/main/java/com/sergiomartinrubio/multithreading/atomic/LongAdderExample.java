package com.sergiomartinrubio.multithreading.atomic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/// `LongAdder` beats `AtomicLong` when many threads write to one counter.
///
/// Interview questions:
/// - Q: When would you choose `LongAdder` over `AtomicLong`?
/// - Q: How does it work?
/// - Q: What is the trade-off?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.atomic.LongAdderExample
public class LongAdderExample {

  private static final int THREADS = 8;
  private static final int ITERATIONS = 500_000;

  static void main() throws InterruptedException {
    // A: high write contention. AtomicLong has every thread CAS-ing the same memory
    //    location, so cores fight over one cache line and most attempts fail and retry.
    long atomicMillis = timeAtomicLong();
    long adderMillis = timeLongAdder();

    System.out.printf("AtomicLong: %d ms%n", atomicMillis);
    System.out.printf("LongAdder : %d ms%n", adderMillis);
    System.out.println();
    System.out.println("Timings vary by machine and core count; the gap widens with threads.");

    // A: LongAdder keeps an array of per-thread cells and adds to the one for the calling
    //    thread, so writers rarely touch the same cache line. sum() adds the cells up.
    //
    // A: the trade-off is that sum() is neither atomic nor a point-in-time snapshot: it
    //    walks the cells while others are still writing. It also uses more memory.
    //    So: LongAdder for write-heavy metrics you read occasionally (request counts,
    //    hit rates); AtomicLong when you need an accurate current value, or
    //    compareAndSet, or an ID generator where every read must be exact.
  }

  static long timeAtomicLong() throws InterruptedException {
    AtomicLong counter = new AtomicLong();
    long start = System.nanoTime();
    runConcurrently(() -> counter.incrementAndGet());
    long millis = (System.nanoTime() - start) / 1_000_000;
    verify(counter.get());
    return millis;
  }

  static long timeLongAdder() throws InterruptedException {
    LongAdder counter = new LongAdder();
    long start = System.nanoTime();
    runConcurrently(counter::increment);
    long millis = (System.nanoTime() - start) / 1_000_000;
    verify(counter.sum());
    return millis;
  }

  static void runConcurrently(Runnable increment) throws InterruptedException {
    try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
      for (int t = 0; t < THREADS; t++) {
        executor.submit(() -> {
          for (int i = 0; i < ITERATIONS; i++) {
            increment.run();
          }
        });
      }
    }
  }

  static void verify(long actual) {
    long expected = (long) THREADS * ITERATIONS;
    if (actual != expected) {
      throw new AssertionError("expected " + expected + " but got " + actual);
    }
  }
}
