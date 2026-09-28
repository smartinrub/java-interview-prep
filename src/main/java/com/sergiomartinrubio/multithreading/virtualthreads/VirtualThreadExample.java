package com.sergiomartinrubio.multithreading.virtualthreads;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/// Virtual threads (JEP 444, final in Java 21): cheap threads for blocking code.
///
/// Interview questions:
/// - Q: What is a virtual thread, and how does it differ from a platform thread?
/// - Q: What problem do they solve?
/// - Q: When do they NOT help?
/// - Q: Why should you not pool them?
/// - Q: What is pinning?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.virtualthreads.VirtualThreadExample
public class VirtualThreadExample {

  static void main() throws Exception {
    // A: a platform thread is a thin wrapper over an OS thread: ~1MB of stack, a
    //    syscall to create, and a kernel context switch to schedule. A virtual thread is
    //    managed by the JVM, starts at a few hundred bytes, and is MOUNTED on a carrier
    //    platform thread only while it runs. When it blocks on I/O the JVM unmounts it
    //    and frees the carrier for someone else.
    Thread virtual = Thread.ofVirtual().name("virtual-1").start(() -> log("on a virtual thread"));
    virtual.join();

    Thread platform = Thread.ofPlatform().name("platform-1")
        .start(() -> log("on a platform thread"));
    platform.join();

    System.out.println("isVirtual: " + Thread.ofVirtual().unstarted(() -> { }).isVirtual());

    manyBlockingTasks();
    cpuBoundIsNotFaster();

    // A: never pool them. Pooling exists to amortise expensive thread creation, and
    //    creation is no longer expensive. A pool would just reintroduce the limit you
    //    adopted virtual threads to remove. The idiom is one virtual thread per task,
    //    via newVirtualThreadPerTaskExecutor(), which creates a thread per submission.

    // A: pinning - a virtual thread that cannot unmount stays stuck on its carrier,
    //    holding it hostage. It happens inside a synchronized block (largely fixed in
    //    Java 24 by JEP 491) and in native/foreign calls. If you see it, prefer a
    //    ReentrantLock over synchronized around blocking calls.
    //    Diagnose with -Djdk.tracePinnedThreads=full.

    // Related, still preview in Java 25: structured concurrency (StructuredTaskScope)
    // and ScopedValue, which replace ThreadLocal for per-task context.
  }

  /// A: the thread-per-request model with blocking I/O. A platform-thread pool caps
  ///    concurrency at a few hundred threads, so you either block or rewrite everything
  ///    reactively. Virtual threads let straightforward blocking code scale to hundreds
  ///    of thousands of concurrent tasks.
  static void manyBlockingTasks() throws InterruptedException {
    int tasks = 50_000;
    AtomicLong completed = new AtomicLong();

    long start = System.nanoTime();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      IntStream.range(0, tasks).forEach(i -> executor.submit(() -> {
        // Stands in for a network or database call.
        TimeUnit.MILLISECONDS.sleep(100);
        completed.incrementAndGet();
        return null;
      }));
    }
    long millis = (System.nanoTime() - start) / 1_000_000;

    System.out.printf("%,d blocking tasks (100ms each) finished in %d ms on virtual threads%n",
        completed.get(), millis);
    System.out.println("A fixed pool of 200 platform threads would need ~"
        + (tasks / 200 * 100) + " ms, and 50,000 platform threads would exhaust memory.");
  }

  /// A: for CPU-bound work. There is no blocking to unmount on, so you are limited by
  ///    cores either way, and you add scheduling overhead for nothing. Use a fixed
  ///    platform pool (or fork/join) sized to availableProcessors() for computation.
  static void cpuBoundIsNotFaster() throws InterruptedException {
    int tasks = 2_000;

    long virtualMillis = time(Executors.newVirtualThreadPerTaskExecutor(), tasks);
    long platformMillis = time(
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()), tasks);

    System.out.printf("CPU-bound: virtual %d ms vs fixed platform pool %d ms (no real win)%n",
        virtualMillis, platformMillis);
  }

  static long time(ExecutorService executor, int tasks) throws InterruptedException {
    long start = System.nanoTime();
    try (executor) {
      for (int i = 0; i < tasks; i++) {
        executor.submit(VirtualThreadExample::burnCpu);
      }
    }
    return (System.nanoTime() - start) / 1_000_000;
  }

  static long burnCpu() {
    long sum = 0;
    for (int i = 0; i < 200_000; i++) {
      sum += i % 7;
    }
    return sum;
  }

  static void log(String message) {
    Thread current = Thread.currentThread();
    System.out.println("[" + current.getName() + "] virtual=" + current.isVirtual()
        + " -> " + message);
  }
}
