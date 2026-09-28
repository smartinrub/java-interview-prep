package com.sergiomartinrubio.multithreading.synchronization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sergiomartinrubio.multithreading.synchronization.Counters.Counter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/// Proves the claims the examples make: the safe counters are exact, the unsafe one is not.
///
/// Note how the unsafe assertion is written. `assertTrue(value <= EXPECTED)` is
/// deterministic; asserting that updates ARE lost would be a flaky test, because a race
/// condition is allowed to not happen. That is the whole problem with races.
class CountersTest {

  private static final int THREADS = 8;
  private static final int INCREMENTS = 20_000;
  private static final int EXPECTED = THREADS * INCREMENTS;

  static Supplier<Counter>[] threadSafeCounters() {
    return new Supplier[] {
        (Supplier<Counter>) Counters.Synchronized::new,
        (Supplier<Counter>) Counters.Locked::new,
        (Supplier<Counter>) Counters.Atomic::new,
    };
  }

  @ParameterizedTest
  @MethodSource("threadSafeCounters")
  @DisplayName("thread-safe counters never lose an update")
  void threadSafeCountersAreExact(Supplier<Counter> factory) throws InterruptedException {
    Counter counter = factory.get();

    incrementConcurrently(counter);

    assertEquals(EXPECTED, counter.value());
  }

  @Test
  @DisplayName("the unsafe counter can only under-count, never over-count")
  void unsafeCounterLosesUpdates() throws InterruptedException {
    Counter counter = new Counters.Unsafe();

    incrementConcurrently(counter);

    int actual = counter.value();
    assertTrue(actual <= EXPECTED,
        "a lost update can only lower the total, got " + actual);
    assertTrue(actual > 0, "some increments must have landed, got " + actual);
  }

  /// Uses a start latch so every thread begins at roughly the same moment, which makes
  /// contention (and therefore the race) far more likely than staggered starts.
  private static void incrementConcurrently(Counter counter) throws InterruptedException {
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
      for (int t = 0; t < THREADS; t++) {
        executor.submit(() -> {
          start.await();
          for (int i = 0; i < INCREMENTS; i++) {
            counter.increment();
          }
          return null;
        });
      }
      start.countDown();
    }
  }
}
