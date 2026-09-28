package com.sergiomartinrubio.multithreading.coordination;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/// `CountDownLatch`: wait until N things have happened.
///
/// Interview questions:
/// - Q: What is a `CountDownLatch` for?
/// - Q: `CountDownLatch` vs `CyclicBarrier`?
/// - Q: Why is `await(timeout)` preferable to `await()`?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.coordination.CountDownLatchExample
public class CountDownLatchExample {

  static void main() throws InterruptedException {
    // A: a one-shot gate. One or more threads wait until a counter reaches zero.
    //    Two standard shapes: "wait for N workers to finish" (below) and
    //    "hold N workers until a start signal" (one latch of 1, everyone awaits it).
    int workers = 4;
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch startSignal = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(workers);

    try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
      for (int i = 1; i <= workers; i++) {
        int id = i;
        executor.submit(() -> {
          try {
            log("worker " + id + " initialised");
            ready.countDown();

            startSignal.await();          // everyone blocks here
            log("worker " + id + " running");
            TimeUnit.MILLISECONDS.sleep(50L * id);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            // In a finally block: a worker that dies without counting down hangs
            // every waiter forever.
            done.countDown();
          }
        });
      }

      ready.await();
      log("all workers initialised, releasing the start gate");
      startSignal.countDown();

      // A: await() with no timeout waits forever, so one lost countDown() deadlocks the
      //    application with no diagnostics. The timed version lets you fail loudly.
      if (done.await(5, TimeUnit.SECONDS)) {
        log("all workers finished");
      } else {
        log("timed out waiting, " + done.getCount() + " workers outstanding");
      }
    }

    // A: a latch counts DOWN once and stays at zero: it cannot be reset, and the waiters
    //    are usually not the workers. A CyclicBarrier counts UP to a threshold, releases
    //    everybody, then RESETS for the next round, and the participants are the ones
    //    waiting. Latch = "wait for an event". Barrier = "meet up each round".
    log("latch count is stuck at " + done.getCount() + " forever; it is not reusable");
  }

  static void log(String message) {
    System.out.println("[" + Thread.currentThread().getName() + "] " + message);
  }
}
