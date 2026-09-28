package com.sergiomartinrubio.multithreading.executors;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/// Delayed and periodic execution.
///
/// Interview questions:
/// - Q: `scheduleAtFixedRate()` vs `scheduleWithFixedDelay()`?
/// - Q: What happens if a periodic task throws?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.executors.ScheduledExecutorExample
public class ScheduledExecutorExample {

  static void main() throws InterruptedException {
    try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2)) {

      scheduler.schedule(() -> log("one-shot, 200ms later"), 200, TimeUnit.MILLISECONDS);

      // A: fixed RATE measures period start-to-start. If a run overruns the period the
      //    next one starts immediately, so runs can bunch up (they never overlap,
      //    they queue).
      var atFixedRate = scheduler.scheduleAtFixedRate(
          () -> log("fixed rate"), 0, 150, TimeUnit.MILLISECONDS);

      // A: fixed DELAY measures end-to-start, so there is always a real gap between runs.
      //    This is the safer default when the task duration varies.
      var withFixedDelay = scheduler.scheduleWithFixedDelay(
          () -> log("fixed delay"), 0, 150, TimeUnit.MILLISECONDS);

      TimeUnit.MILLISECONDS.sleep(600);

      atFixedRate.cancel(true);
      withFixedDelay.cancel(true);
    }

    // A: an uncaught exception silently CANCELS the schedule. The task simply stops
    //    running and nothing is logged. Always wrap the body in try/catch.
    try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
      var task = scheduler.scheduleAtFixedRate(() -> {
        try {
          throw new IllegalStateException("boom");
        } catch (RuntimeException e) {
          log("caught inside the task, schedule survives: " + e.getMessage());
        }
      }, 0, 100, TimeUnit.MILLISECONDS);

      TimeUnit.MILLISECONDS.sleep(250);
      task.cancel(true);
    }
  }

  static void log(String message) {
    System.out.println("[" + Thread.currentThread().getName() + "] " + message);
  }
}
