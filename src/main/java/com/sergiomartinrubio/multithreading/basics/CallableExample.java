package com.sergiomartinrubio.multithreading.basics;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/// `Callable` returns a value and may throw checked exceptions; `Runnable` does neither.
///
/// Interview questions:
/// - Q: `Runnable` vs `Callable`?
/// - Q: What does `Future.get()` do while the task is still running?
/// - Q: Where does an exception thrown inside a `Callable` surface?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.basics.CallableExample
public class CallableExample {

  static void main() throws Exception {
    // A: Runnable -> void, no checked exceptions.
    //    Callable -> returns a result, may throw checked exceptions.
    Callable<Integer> sum = () -> 10 + 20;

    // Calling it directly runs on the current thread: no concurrency involved.
    System.out.println("direct call: " + sum.call());

    // try-with-resources on ExecutorService (Java 19+) shuts it down and awaits termination.
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Integer> future = executor.submit(sum);

      // A: get() blocks the calling thread until the result is available.
      //    Always prefer the timed overload in production code.
      System.out.println("via executor: " + future.get());

      // A: the exception is captured and rethrown by get(), wrapped in ExecutionException.
      Future<Integer> failing = executor.submit(() -> {
        throw new IllegalStateException("task blew up");
      });

      try {
        failing.get();
      } catch (ExecutionException e) {
        System.out.println("get() rethrew: " + e.getCause());
      }

      // Worth knowing: submit(Runnable) also returns a Future, but get() yields null.
      // And a Runnable exception thrown via execute() goes to the thread's
      // UncaughtExceptionHandler instead, where it is easy to lose.
    }
  }
}
