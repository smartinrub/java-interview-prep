package com.sergiomartinrubio.multithreading.synchronization;

import com.sergiomartinrubio.multithreading.synchronization.Counters.Counter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/// A race condition you can watch happen, and the three standard fixes.
///
/// Interview questions:
/// - Q: Why isn't `count++` atomic?
/// - Q: How do you fix a race condition?
/// - Q: Which fix would you pick?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.synchronization.RaceConditionExample
public class RaceConditionExample {

  private static final int THREADS = 8;
  private static final int INCREMENTS_PER_THREAD = 50_000;
  private static final int EXPECTED = THREADS * INCREMENTS_PER_THREAD;

  static void main() throws InterruptedException {
    // A: count++ compiles to read / add / write. Two threads can read 41, both add 1,
    //    both write 42, and one increment vanishes. Nothing about the ++ operator is atomic.
    System.out.println("expected total: " + EXPECTED);
    System.out.println();

    report("Unsafe (int count++)", Counters.Unsafe::new);
    report("synchronized method", Counters.Synchronized::new);
    report("ReentrantLock", Counters.Locked::new);
    report("AtomicInteger", Counters.Atomic::new);

    System.out.println();
    // A: cheapest correct option that fits the problem.
    //    single variable          -> Atomic*
    //    a few fields, short body -> synchronized
    //    need timeout / tryLock / fairness / many readers -> explicit Lock
    System.out.println("Unsafe loses updates; the other three always total " + EXPECTED + ".");
    System.out.println("Note the loss is not guaranteed on every run: that is exactly why");
    System.out.println("race conditions survive testing and show up in production.");
  }

  static void report(String label, Supplier<Counter> factory) throws InterruptedException {
    Counter counter = factory.get();

    long start = System.nanoTime();
    try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
      for (int t = 0; t < THREADS; t++) {
        executor.submit(() -> {
          for (int i = 0; i < INCREMENTS_PER_THREAD; i++) {
            counter.increment();
          }
        });
      }
    } // close() waits for every task to finish
    long millis = (System.nanoTime() - start) / 1_000_000;

    int actual = counter.value();
    System.out.printf("%-22s -> %,8d  %-12s (%d ms)%n",
        label, actual, actual == EXPECTED ? "correct" : "LOST " + (EXPECTED - actual), millis);
  }
}
