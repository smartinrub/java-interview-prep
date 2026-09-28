package com.sergiomartinrubio.multithreading.collections;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/// `BlockingQueue` is how producer-consumer should actually be written.
///
/// Interview questions:
/// - Q: Why a `BlockingQueue` instead of `wait()`/`notify()`?
/// - Q: `put`/`take` vs `offer`/`poll` vs `add`/`remove`?
/// - Q: Which implementation would you choose?
/// - Q: How do you shut a consumer down cleanly?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.collections.BlockingQueueExample
public class BlockingQueueExample {

  /// A: the poison pill: a sentinel that tells the consumer to stop, queued behind all
  ///    real work so nothing is dropped. One pill per consumer.
  private static final String POISON_PILL = "__STOP__";

  static void main() throws InterruptedException {
    // A: because the blocking, the bound, the signalling and the correct while-loop
    //    re-checks are all already written and tested. Hand-rolled wait/notify is
    //    where the bugs come from. The bound also gives you backpressure for free:
    //    a slow consumer slows the producer instead of exhausting memory.
    BlockingQueue<String> queue = new ArrayBlockingQueue<>(5);

    try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
      executor.submit(() -> producer(queue, 10));
      executor.submit(() -> consumer(queue, "consumer-1"));
      executor.submit(() -> consumer(queue, "consumer-2"));
    }

    System.out.println("queue drained, remaining = " + queue.size());
    describeApi();
    describeImplementations();
  }

  static void producer(BlockingQueue<String> queue, int items) {
    try {
      for (int i = 1; i <= items; i++) {
        // put() blocks while the queue is full: this is the backpressure.
        queue.put("item-" + i);
        System.out.println("[producer] put item-" + i + " (queued=" + queue.size() + ")");
      }
      queue.put(POISON_PILL);
      queue.put(POISON_PILL);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static void consumer(BlockingQueue<String> queue, String name) {
    try {
      while (true) {
        // take() blocks while the queue is empty: no polling, no busy-waiting.
        String item = queue.take();
        if (POISON_PILL.equals(item)) {
          System.out.println("[" + name + "] got the pill, exiting");
          return;
        }
        System.out.println("[" + name + "] handling " + item);
        TimeUnit.MILLISECONDS.sleep(60);
      }
    } catch (InterruptedException e) {
      // The other clean shutdown: interrupt the consumer and let take() throw.
      Thread.currentThread().interrupt();
    }
  }

  static void describeApi() {
    // A: three families, distinguished by what they do when they cannot proceed:
    //    put / take            - BLOCK until they can
    //    offer / poll          - return false / null immediately
    //    offer(t, timeout) /
    //      poll(timeout)       - block, but give up after a while
    //    add / remove / element- THROW (IllegalStateException, NoSuchElementException)
    BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
    System.out.println("offer to empty queue -> " + queue.offer("a"));
    System.out.println("offer to full queue  -> " + queue.offer("b"));
    System.out.println("poll                 -> " + queue.poll());
    System.out.println("poll when empty      -> " + queue.poll());
  }

  static void describeImplementations() {
    // A: pick by the shape of the problem.
    //    ArrayBlockingQueue     - bounded, array-backed, single lock. The safe default.
    //    LinkedBlockingQueue    - optionally bounded; separate head/tail locks so
    //                             producers and consumers contend less. UNBOUNDED by
    //                             default, which is how you get an OOM.
    //    SynchronousQueue       - capacity zero: a handoff, each put waits for a take.
    //                             Used by newCachedThreadPool.
    //    PriorityBlockingQueue  - unbounded, ordered by comparator, not FIFO.
    //    DelayQueue             - elements only become available once their delay expires.
    //    LinkedTransferQueue    - LinkedBlockingQueue plus transfer(); usually the fastest.
    System.out.println("ArrayBlockingQueue capacity 5 remaining -> "
        + new ArrayBlockingQueue<>(5).remainingCapacity());
    System.out.println("LinkedBlockingQueue default capacity -> "
        + new LinkedBlockingQueue<>().remainingCapacity() + " (Integer.MAX_VALUE)");
    System.out.println("SynchronousQueue offer with no waiting taker -> "
        + new SynchronousQueue<>().offer("x"));
  }
}
