package com.sergiomartinrubio.multithreading.atomic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/// Lock-free counters via compare-and-swap.
///
/// Interview questions:
/// - Q: How does `AtomicInteger` achieve thread safety without locks?
/// - Q: What is CAS, and when is it slower than a lock?
/// - Q: What can `AtomicInteger` NOT do for you?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.atomic.AtomicCounterExample
public class AtomicCounterExample {

  static void main() throws InterruptedException {
    AtomicInteger counter = new AtomicInteger(0);

    // A: compare-and-swap, a single CPU instruction (LOCK CMPXCHG on x86). The JVM reads
    //    the value, computes the new one, and swaps it in only if the current value is
    //    still what it read. If another thread won, it retries. No thread ever blocks,
    //    so there is no context switch and no deadlock possible.
    System.out.println("incrementAndGet -> " + counter.incrementAndGet());  // 1, returns new
    System.out.println("getAndIncrement -> " + counter.getAndIncrement());  // returns old (1)
    System.out.println("decrementAndGet -> " + counter.decrementAndGet());  // 1
    System.out.println("addAndGet(10)   -> " + counter.addAndGet(10));      // 11

    // compareAndSet is the primitive everything above is built on.
    System.out.println("compareAndSet(11, 20) -> " + counter.compareAndSet(11, 20));
    System.out.println("compareAndSet(11, 99) -> " + counter.compareAndSet(11, 99)
        + " (fails: current value is no longer 11)");

    // updateAndGet / accumulateAndGet take the retry loop off your hands, so you can
    // apply any pure function atomically.
    System.out.println("updateAndGet(x -> x * 2) -> " + counter.updateAndGet(x -> x * 2));
    System.out.println("accumulateAndGet(5, Math::max) -> "
        + counter.accumulateAndGet(5, Math::max));

    counter.set(0);
    try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
      for (int t = 0; t < 8; t++) {
        executor.submit(() -> {
          for (int i = 0; i < 25_000; i++) {
            counter.incrementAndGet();
          }
        });
      }
    }
    System.out.println("8 threads x 25,000 -> " + counter.get() + " (exact)");

    // A: CAS loses under heavy contention. Every failed swap re-runs the loop, so with
    //    many writers you burn CPU spinning instead of sleeping. That is what LongAdder
    //    solves, and why a lock can win when the critical section is long.
    //
    // A: the limit is scope. An Atomic makes ONE variable's update atomic, not a
    //    multi-step operation. Two atomics read together are still a race:
    //      if (atomicA.get() > 0) atomicB.increment();   // NOT atomic
    //    Invariants spanning several fields need a lock or an immutable snapshot.
    //
    // Also available: AtomicLong, AtomicBoolean, AtomicReference,
    // AtomicIntegerArray, and the Atomic*FieldUpdater classes.
  }
}
