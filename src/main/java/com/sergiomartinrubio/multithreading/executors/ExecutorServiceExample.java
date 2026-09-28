package com.sergiomartinrubio.multithreading.executors;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/// The standard way to run tasks: hand them to a pool instead of creating threads.
///
/// Interview questions:
/// - Q: Why use an `ExecutorService` instead of `new Thread(...)`?
/// - Q: `submit()` vs `execute()`? `invokeAll()` vs `invokeAny()`?
/// - Q: `shutdown()` vs `shutdownNow()`?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.executors.ExecutorServiceExample
public class ExecutorServiceExample {

  static void main() throws Exception {
    // A: thread creation is expensive and unbounded thread creation is how services
    //    fall over. A pool gives you reuse, a bounded thread count, a queue,
    //    lifecycle control and Futures for results.
    ExecutorService executor = Executors.newFixedThreadPool(3);

    try {
      // execute(Runnable): fire and forget, no handle, exceptions go to the
      // UncaughtExceptionHandler.
      executor.execute(() -> log("fire and forget"));

      // submit(): returns a Future, exceptions are captured and rethrown by get().
      Future<String> single = executor.submit(() -> "submitted result");
      log(single.get());

      List<Callable<String>> tasks = List.of(
          taskNamed("a", 100),
          taskNamed("b", 200),
          taskNamed("c", 50));

      // A: invokeAll blocks until EVERY task finishes and returns all Futures, in order.
      for (Future<String> future : executor.invokeAll(tasks)) {
        log("invokeAll -> " + future.get());
      }

      // A: invokeAny blocks until the FIRST success, then cancels the rest.
      log("invokeAny -> " + executor.invokeAny(tasks));
    } finally {
      shutdownGracefully(executor);
    }

    // Since Java 19 ExecutorService is AutoCloseable, so the whole dance above is
    // usually just:
    try (ExecutorService closeable = Executors.newFixedThreadPool(2)) {
      closeable.submit(() -> log("closed automatically on block exit"));
    }
  }

  /// A: shutdown() stops accepting new tasks and lets queued ones finish.
  ///    shutdownNow() also drops the queue and interrupts running tasks.
  ///    Neither one waits: awaitTermination() is what waits.
  static void shutdownGracefully(ExecutorService executor) throws InterruptedException {
    executor.shutdown();
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
      executor.shutdownNow();
    }
  }

  static Callable<String> taskNamed(String name, long millis) {
    return () -> {
      TimeUnit.MILLISECONDS.sleep(millis);
      return name + " on " + Thread.currentThread().getName();
    };
  }

  static void log(String message) {
    System.out.println("[" + Thread.currentThread().getName() + "] " + message);
  }
}
