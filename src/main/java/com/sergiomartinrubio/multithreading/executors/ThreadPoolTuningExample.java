package com.sergiomartinrubio.multithreading.executors;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/// What the `Executors` factory methods actually build, and what happens under load.
///
/// Interview questions:
/// - Q: What happens when you submit more tasks than the pool has threads?
/// - Q: How many threads should a pool have?
/// - Q: Why is `Executors.newCachedThreadPool()` risky? And `newFixedThreadPool()`?
/// - Q: What are the rejection policies?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.executors.ThreadPoolTuningExample
public class ThreadPoolTuningExample {

  static void main() throws InterruptedException {
    // A: the order is counter-intuitive and a classic interview trap:
    //    1. below corePoolSize          -> start a new thread
    //    2. core threads busy          -> QUEUE the task
    //    3. queue full                 -> start threads up to maximumPoolSize
    //    4. queue full and at max      -> hand the task to the rejection handler
    //    So with an unbounded queue, maximumPoolSize is never reached.
    ThreadPoolExecutor pool = new ThreadPoolExecutor(
        1,                                   // corePoolSize
        2,                                   // maximumPoolSize
        30, TimeUnit.SECONDS,                // idle keep-alive for non-core threads
        new ArrayBlockingQueue<>(1),         // bounded queue: 1 waiting task
        new ThreadPoolExecutor.AbortPolicy() // A: throws RejectedExecutionException
    );

    // A: the usual starting points, then measure.
    //    CPU-bound  -> ~ Runtime.availableProcessors()
    //    I/O-bound  -> higher, or virtual threads (see the virtualthreads package)
    log("available processors: " + Runtime.getRuntime().availableProcessors());

    try {
      // Capacity here is 2 running + 1 queued = 3. The fourth submission is rejected.
      for (int i = 1; i <= 4; i++) {
        int id = i;
        try {
          pool.execute(() -> {
            log("task " + id + " running");
            sleep(300);
          });
          log("task " + id + " accepted");
        } catch (RejectedExecutionException e) {
          log("task " + id + " REJECTED: pool full and queue full");
        }
      }
    } finally {
      pool.shutdown();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    log("completed tasks: " + pool.getCompletedTaskCount());

    // A: the pitfalls of the convenience factories:
    //    newFixedThreadPool  -> UNBOUNDED LinkedBlockingQueue. Under overload the
    //                           queue grows until you run out of memory, and latency
    //                           degrades silently long before that.
    //    newCachedThreadPool -> UNBOUNDED thread count. A burst of slow tasks creates
    //                           a thread each, and the process dies on OOM.
    //    Production answer: construct ThreadPoolExecutor yourself with a bounded queue
    //    and a rejection policy you chose on purpose.

    // The four built-in policies:
    //   AbortPolicy         - throw RejectedExecutionException (default)
    //   CallerRunsPolicy    - run it on the submitting thread; natural backpressure
    //   DiscardPolicy       - drop it silently
    //   DiscardOldestPolicy - drop the oldest queued task and retry
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
