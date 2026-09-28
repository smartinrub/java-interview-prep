package com.sergiomartinrubio.multithreading.basics;

/// Creating a thread by extending `Thread`.
///
/// Interview questions:
/// - Q: Why call `start()` instead of `run()`?
/// - Q: Why is extending `Thread` less flexible than implementing `Runnable`?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.basics.ThreadExample
public class ThreadExample {

  static void main() throws InterruptedException {
    // A: start() asks the JVM for a NEW thread, which then runs run().
    //    Calling run() directly is just a method call on the CURRENT thread: no concurrency.
    MyThread onNewThread = new MyThread("started");
    onNewThread.start();
    onNewThread.join();

    new MyThread("called-directly").run();

    log("done");

    // A: extending Thread burns your single inheritance slot and couples the task
    //    to the mechanism that runs it. Runnable keeps the task reusable, and it is
    //    the only option that works with executors.
  }

  static void log(String message) {
    System.out.println("[" + Thread.currentThread().getName() + "] " + message);
  }

  static class MyThread extends Thread {

    MyThread(String name) {
      super(name);
    }

    @Override
    public void run() {
      // Prints the worker's name when started, but "main" when run() is called directly.
      log("MyThread.run()");
    }
  }
}
