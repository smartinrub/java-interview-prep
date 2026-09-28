package com.sergiomartinrubio.multithreading.basics;

/// Defining work as a `Runnable`, separated from the thread that executes it.
///
/// Interview questions:
/// - Q: Why prefer `Runnable` over extending `Thread`?
/// - Q: Can the same `Runnable` be handed to several threads?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.basics.RunnableExample
public class RunnableExample {

  static void main() throws InterruptedException {
    // A: the task is decoupled from its execution, so the same object can run on a
    //    bare Thread, on an executor, or inline in a test.
    Runnable task = () -> log("running the task");

    Thread first = new Thread(task, "worker-1");
    Thread second = new Thread(task, "worker-2");

    first.start();
    second.start();

    first.join();
    second.join();

    // A: yes, and that is the catch. A shared Runnable means shared state:
    //    every field it touches is now concurrently accessed.
    Counter shared = new Counter();
    Runnable unsafeTask = shared::increment;

    Thread a = new Thread(unsafeTask);
    Thread b = new Thread(unsafeTask);
    a.start();
    b.start();
    a.join();
    b.join();

    log("counter = " + shared.value() + " (shared mutable state, not synchronized)");
  }

  static void log(String message) {
    System.out.println("[" + Thread.currentThread().getName() + "] " + message);
  }

  /// Deliberately not thread-safe; see the `synchronization` package for the fixes.
  static class Counter {

    private int count;

    void increment() {
      for (int i = 0; i < 1_000; i++) {
        count++;
      }
    }

    int value() {
      return count;
    }
  }
}
