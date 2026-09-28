package com.sergiomartinrubio.multithreading.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// A bounded queue must lose nothing and must apply backpressure. Both are asserted here.
class ProducerConsumerTest {

  private static final Integer POISON_PILL = Integer.MIN_VALUE;

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  @DisplayName("every produced item is consumed exactly once")
  void nothingIsLostOrDuplicated() throws InterruptedException {
    int producers = 3;
    int consumers = 4;
    int itemsEach = 500;
    BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(8);
    ConcurrentLinkedQueue<Integer> received = new ConcurrentLinkedQueue<>();

    try (ExecutorService pool = Executors.newFixedThreadPool(producers + consumers)) {
      for (int c = 0; c < consumers; c++) {
        pool.submit(() -> {
          while (true) {
            Integer item = queue.take();
            if (POISON_PILL.equals(item)) {
              return null;
            }
            received.add(item);
          }
        });
      }

      List<java.util.concurrent.Future<?>> producerTasks = new java.util.ArrayList<>();
      for (int p = 0; p < producers; p++) {
        int producerId = p;
        producerTasks.add(pool.submit(() -> {
          for (int i = 0; i < itemsEach; i++) {
            queue.put(producerId * itemsEach + i);
          }
          return null;
        }));
      }

      for (var task : producerTasks) {
        task.get(20, TimeUnit.SECONDS);
      }
      for (int i = 0; i < consumers; i++) {
        queue.put(POISON_PILL);
      }
    } catch (Exception e) {
      throw new AssertionError(e);
    }

    assertEquals(producers * itemsEach, received.size(), "items were lost or duplicated");
    assertEquals(producers * itemsEach, received.stream().distinct().count(),
        "an item was consumed twice");
    assertTrue(queue.isEmpty(), "the queue should be drained");
  }

  @Test
  @DisplayName("a bounded queue blocks the producer instead of growing without limit")
  void boundedQueueAppliesBackpressure() throws InterruptedException {
    BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);
    queue.put(1);
    queue.put(2);

    // offer() with a timeout is the non-destructive way to observe the bound.
    assertTrue(queue.remainingCapacity() == 0, "queue should be full");
    assertTrue(!queue.offer(3, 50, TimeUnit.MILLISECONDS),
        "offer must fail while the queue is full");

    assertEquals(1, queue.take());
    assertTrue(queue.offer(3), "space freed, offer must now succeed");
  }
}
