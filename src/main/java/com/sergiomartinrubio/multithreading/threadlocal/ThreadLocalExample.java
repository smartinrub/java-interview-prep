package com.sergiomartinrubio.multithreading.threadlocal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// `ThreadLocal`: one variable, a separate value per thread.
///
/// Interview questions:
/// - Q: What is `ThreadLocal` for?
/// - Q: Why does it leak memory in a thread pool, and how do you avoid that?
/// - Q: What is an `InheritableThreadLocal`?
/// - Q: Does it still make sense with virtual threads?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.threadlocal.ThreadLocalExample
public class ThreadLocalExample {

  /// A: per-thread context that would otherwise be threaded through every method
  ///    signature: a request id, the current user, a transaction, a locale.
  private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

  /// A: and per-thread instances of objects that are expensive to build but NOT
  ///    thread-safe. SimpleDateFormat is the textbook case: sharing one across threads
  ///    silently produces wrong dates.
  private static final ThreadLocal<SimpleDateFormat> FORMATTER =
      ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm:ss.SSS"));

  static void main() throws InterruptedException {
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (int i = 1; i <= 4; i++) {
        int id = i;
        executor.submit(() -> handleRequest("req-" + id));
      }
    }

    // Nothing was set on main, and no value leaked in from the workers.
    System.out.println("main sees REQUEST_ID = " + REQUEST_ID.get());

    inheritance();

    // A: with virtual threads there is one thread per task, often millions of them, so a
    //    ThreadLocal is no longer "a handful of values" and cheap reuse disappears.
    //    Java 25 offers ScopedValue (JEP 506) instead: immutable, explicitly scoped,
    //    automatically cleaned up at the end of the scope, and inherited by
    //    structured-concurrency subtasks. Prefer it for new context-propagation code.
  }

  static void handleRequest(String requestId) {
    REQUEST_ID.set(requestId);
    try {
      // Anything called from here can read the context without it being passed down.
      logWithContext("handling");
    } finally {
      // A: the leak. A pool thread lives for the life of the application, and its
      //    ThreadLocalMap holds the value strongly, so the value (and its whole object
      //    graph, possibly a classloader) is never collected. Worse, the NEXT task on
      //    that thread reads the previous task's context: a real data-leak bug.
      //    Always remove() in a finally block.
      REQUEST_ID.remove();
      FORMATTER.remove();
    }
  }

  static void logWithContext(String message) {
    System.out.println("[" + Thread.currentThread().getName() + "] "
        + FORMATTER.get().format(new Date())
        + " requestId=" + REQUEST_ID.get() + " " + message);
  }

  static void inheritance() throws InterruptedException {
    // A: an InheritableThreadLocal is copied into any thread created by the current one,
    //    at creation time. Handy for propagating context into a spawned thread, but it
    //    does NOT work with pooled threads: the pool's threads were created long before
    //    your task arrived.
    ThreadLocal<String> plain = new ThreadLocal<>();
    InheritableThreadLocal<String> inheritable = new InheritableThreadLocal<>();

    plain.set("parent-only");
    inheritable.set("inherited");

    Thread child = new Thread(() -> {
      System.out.println("child sees plain       = " + plain.get());
      System.out.println("child sees inheritable = " + inheritable.get());
    }, "child");
    child.start();
    child.join();
  }
}
