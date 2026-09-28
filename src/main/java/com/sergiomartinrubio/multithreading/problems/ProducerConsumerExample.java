package com.sergiomartinrubio.multithreading.problems;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/// Classic whiteboard problem: N producers, M consumers, a bounded buffer.
///
/// The interviewer usually wants: a bounded queue, no busy-waiting, a clean shutdown
/// that drops nothing, and an answer to "what if a consumer throws?".
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.problems.ProducerConsumerExample
public class ProducerConsumerExample {

  private static final int PRODUCERS = 2;
  private static final int CONSUMERS = 3;
  private static final int ITEMS_PER_PRODUCER = 10;
  private static final int CAPACITY = 4;

  /// Sentinel value, one per consumer, queued behind all real work.
  private static final Integer POISON_PILL = Integer.MIN_VALUE;

  static void main() throws InterruptedException {
    // Bounded on purpose: the bound is the backpressure. An unbounded queue turns a slow
    // consumer into an OutOfMemoryError instead of a slow producer.
    BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(CAPACITY);
    AtomicInteger produced = new AtomicInteger();
    AtomicInteger consumed = new AtomicInteger();
    AtomicInteger failed = new AtomicInteger();

    try (ExecutorService producers = Executors.newFixedThreadPool(PRODUCERS);
         ExecutorService consumers = Executors.newFixedThreadPool(CONSUMERS)) {

      for (int c = 1; c <= CONSUMERS; c++) {
        int id = c;
        consumers.submit(() -> consume(queue, consumed, failed, "consumer-" + id));
      }

      for (int p = 1; p <= PRODUCERS; p++) {
        int id = p;
        producers.submit(() -> produce(queue, produced, id));
      }

      // Order matters: every producer must be done before the pills go in, otherwise a
      // consumer could take a pill while real items are still coming.
      producers.shutdown();
      if (!producers.awaitTermination(30, TimeUnit.SECONDS)) {
        producers.shutdownNow();
      }

      for (int i = 0; i < CONSUMERS; i++) {
        queue.put(POISON_PILL);
      }
    }

    System.out.println();
    System.out.println("produced = " + produced.get()
        + ", consumed = " + consumed.get()
        + ", failed = " + failed.get()
        + ", queue left = " + queue.size());

    // The invariant that matters: every item was accounted for exactly once, either
    // handled or explicitly failed. Nothing silently disappeared.
    if (produced.get() != consumed.get() + failed.get()) {
      throw new AssertionError("items were lost");
    }
  }

  static void produce(BlockingQueue<Integer> queue, AtomicInteger produced, int producerId) {
    try {
      for (int i = 1; i <= ITEMS_PER_PRODUCER; i++) {
        int item = producerId * 100 + i;
        queue.put(item);                     // blocks while full
        produced.incrementAndGet();
        System.out.println("[producer-" + producerId + "] put " + item
            + " (queued=" + queue.size() + "/" + CAPACITY + ")");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static void consume(BlockingQueue<Integer> queue, AtomicInteger consumed,
      AtomicInteger failed, String name) {
    try {
      while (true) {
        Integer item = queue.take();         // blocks while empty
        if (POISON_PILL.equals(item)) {
          System.out.println("[" + name + "] shutting down");
          return;
        }
        try {
          handle(item);
          consumed.incrementAndGet();
          System.out.println("[" + name + "] handled " + item);
        } catch (RuntimeException e) {
          // A consumer that dies on one bad item takes its share of throughput with it.
          // Log, count, carry on; in production this is where a dead-letter queue goes.
          failed.incrementAndGet();
          System.out.println("[" + name + "] failed on " + item + ": " + e.getMessage());
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static void handle(int item) throws InterruptedException {
    TimeUnit.MILLISECONDS.sleep(40);
    if (item % 7 == 0) {
      throw new IllegalStateException("cannot handle multiples of 7");
    }
  }
}
