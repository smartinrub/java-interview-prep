package com.sergiomartinrubio.multithreading.atomic;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.stream.Stream;

/// Atomically swapping a whole object reference: the immutable-snapshot pattern.
///
/// Interview questions:
/// - Q: What is `AtomicReference` for?
/// - Q: How do you atomically update several related fields without a lock?
/// - Q: What is the ABA problem?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.atomic.AtomicReferenceExample
public class AtomicReferenceExample {

  /// Immutable, so publishing it is safe: no reader can ever see a half-built value.
  record Stats(int count, long total) {

    Stats plus(long value) {
      return new Stats(count + 1, total + value);
    }
  }

  static void main() throws InterruptedException {
    // A: CAS on an object reference rather than a number. The pattern that makes it
    //    powerful: keep the mutable state in an IMMUTABLE object and swap the whole
    //    reference. Several fields then move from one consistent state to the next
    //    atomically, with no lock at all.
    AtomicReference<Stats> stats = new AtomicReference<>(new Stats(0, 0));

    try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
      for (int t = 0; t < 4; t++) {
        executor.submit(() -> {
          for (int i = 1; i <= 1_000; i++) {
            long value = i;
            // updateAndGet runs the CAS retry loop for you. The function must be pure
            // and side-effect free: it can be invoked several times under contention.
            stats.updateAndGet(current -> current.plus(value));
          }
        });
      }
    }

    Stats result = stats.get();
    System.out.println("count = " + result.count() + " (expected 4000)");
    System.out.println("total = " + result.total() + " (expected 4 x 500500 = 2002000)");

    // The explicit CAS loop, for when you need to see what updateAndGet is doing:
    AtomicReference<List<String>> log = new AtomicReference<>(List.of());
    List<String> previous;
    List<String> next;
    do {
      previous = log.get();
      next = Stream.concat(previous.stream(), Stream.of("entry")).toList();
    } while (!log.compareAndSet(previous, next));
    System.out.println("copy-on-write log = " + log.get());

    // A: ABA - a value changes A -> B -> A while you are computing. Your compareAndSet
    //    succeeds because the reference matches, even though the world moved underneath
    //    you. It bites lock-free stacks and queues, where a recycled node looks identical.
    //    The fix is a version stamp alongside the reference:
    AtomicStampedReference<String> stamped = new AtomicStampedReference<>("A", 0);
    int[] stampHolder = new int[1];
    String value = stamped.get(stampHolder);
    stamped.compareAndSet(value, "B", stampHolder[0], stampHolder[0] + 1);
    stamped.compareAndSet("B", "A", 1, 2);
    System.out.println("value is \"A\" again but the stamp is "
        + stamped.getStamp() + ", so a stale CAS at stamp 0 correctly fails: "
        + stamped.compareAndSet("A", "C", 0, 1));
  }
}
