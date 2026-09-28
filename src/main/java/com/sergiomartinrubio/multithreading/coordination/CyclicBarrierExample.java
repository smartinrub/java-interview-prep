package com.sergiomartinrubio.multithreading.coordination;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/// `CyclicBarrier`: threads meet at the end of each round, then all continue together.
///
/// Interview questions:
/// - Q: When would you use a `CyclicBarrier`?
/// - Q: What happens if one participant fails or times out?
/// - Q: What is the barrier action for?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.coordination.CyclicBarrierExample
public class CyclicBarrierExample {

  private static final int PARTIES = 3;
  private static final int ROUNDS = 3;

  static void main() throws InterruptedException {
    AtomicInteger round = new AtomicInteger();

    // A: iterative or phased algorithms, where no thread may start phase N+1 until every
    //    thread has finished phase N. Simulations, matrix passes, multi-stage pipelines,
    //    parallel test harnesses that must start in lockstep.
    //
    // A: the barrier action runs ONCE per round, on the last thread to arrive, while all
    //    others are still parked. It is the natural place to merge partial results or
    //    advance the phase counter, with no extra synchronisation needed.
    CyclicBarrier barrier = new CyclicBarrier(PARTIES,
        () -> System.out.println("--- round " + round.incrementAndGet() + " complete ---"));

    try (ExecutorService executor = Executors.newFixedThreadPool(PARTIES)) {
      for (int i = 1; i <= PARTIES; i++) {
        int id = i;
        executor.submit(() -> {
          try {
            for (int r = 1; r <= ROUNDS; r++) {
              TimeUnit.MILLISECONDS.sleep(50L * id);
              log("worker " + id + " finished phase " + r);
              // Returns the arrival index, so the last arrival is PARTIES - 1... 0.
              barrier.await();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (BrokenBarrierException e) {
            // A: the barrier BREAKS. If any participant is interrupted, times out or
            //    throws, every other waiter gets a BrokenBarrierException instead of
            //    hanging. The barrier is then unusable until reset(), and that is
            //    deliberate: a partial round is not a valid state to continue from.
            log("worker " + id + " saw a broken barrier");
          }
        });
      }
    }

    System.out.println("parties = " + barrier.getParties()
        + ", broken = " + barrier.isBroken()
        + " (reusable, unlike a CountDownLatch)");
  }

  static void log(String message) {
    System.out.println("[" + Thread.currentThread().getName() + "] " + message);
  }
}
