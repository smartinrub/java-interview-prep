package com.sergiomartinrubio.multithreading.basics;

import java.util.concurrent.TimeUnit;

/// Thread states, `join()`, interruption and daemon threads.
///
/// Interview questions:
/// - Q: What are the thread states?
/// - Q: How do you stop a thread? Why is `Thread.stop()` gone?
/// - Q: What happens to the interrupt flag when `InterruptedException` is thrown?
/// - Q: What is a daemon thread?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.basics.ThreadLifecycleExample
public class ThreadLifecycleExample {

  static void main() throws InterruptedException {
    // A: NEW -> RUNNABLE -> (BLOCKED | WAITING | TIMED_WAITING) -> TERMINATED.
    //    BLOCKED waits for a monitor lock; WAITING waits for another thread to signal.
    Thread sleeper = new Thread(() -> sleep(500), "sleeper");
    log("before start: " + sleeper.getState());          // NEW

    sleeper.start();
    TimeUnit.MILLISECONDS.sleep(100);
    log("while sleeping: " + sleeper.getState());        // TIMED_WAITING

    sleeper.join();                                      // wait for it to finish
    log("after join: " + sleeper.getState());            // TERMINATED

    // A: you cooperate, you do not kill. Thread.stop() could leave locks held and
    //    objects half-updated, so it was deprecated and then removed.
    //    The contract is: interrupt() requests a stop, the task decides how to honour it.
    Thread worker = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          TimeUnit.MILLISECONDS.sleep(50);
        } catch (InterruptedException e) {
          // A: throwing InterruptedException CLEARS the flag. Restore it so callers
          //    up the stack can still see the request, then exit.
          Thread.currentThread().interrupt();
          log("interrupted while sleeping, exiting");
          return;
        }
      }
      log("interrupt flag observed, exiting");
    }, "worker");

    worker.start();
    TimeUnit.MILLISECONDS.sleep(150);
    worker.interrupt();
    worker.join();

    // A: the JVM exits when the last non-daemon thread finishes; daemon threads are
    //    abandoned mid-execution. Never use one for work that must complete.
    Thread daemon = new Thread(() -> {
      while (true) {
        sleep(100);
      }
    }, "daemon");
    daemon.setDaemon(true);
    daemon.start();

    log("main is done; the daemon will not keep the JVM alive");
  }

  static void sleep(long millis) {
    try {
      TimeUnit.MILLISECONDS.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static void log(String message) {
    System.out.println("[" + Thread.currentThread().getName() + "] " + message);
  }
}
