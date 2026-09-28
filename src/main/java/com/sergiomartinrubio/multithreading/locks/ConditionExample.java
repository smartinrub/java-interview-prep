package com.sergiomartinrubio.multithreading.locks;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/// `Condition` is `wait()`/`notify()` for explicit locks, with one key advantage.
///
/// Interview questions:
/// - Q: `Condition` vs `wait()`/`notify()`?
/// - Q: Why is having several conditions per lock useful?
/// - Q: `signal()` vs `signalAll()`?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.locks.ConditionExample
public class ConditionExample {

  static void main() throws InterruptedException {
    // A: a monitor has exactly ONE wait set, so wait()/notifyAll() lumps producers and
    //    consumers together and wakes threads that cannot possibly proceed. A Lock can
    //    hand out as many Conditions as you like, so you signal precisely the right group.
    BoundedBuffer<String> buffer = new BoundedBuffer<>(2);

    Thread producer = new Thread(() -> {
      for (int i = 1; i <= 5; i++) {
        buffer.put("item-" + i);
      }
    }, "producer");

    Thread consumer = new Thread(() -> {
      for (int i = 1; i <= 5; i++) {
        System.out.println("[consumer] took " + buffer.take());
      }
    }, "consumer");

    producer.start();
    consumer.start();
    producer.join();
    consumer.join();
  }

  static class BoundedBuffer<T> {

    private final Lock lock = new ReentrantLock();
    // A: two conditions, two distinct wait sets. A put only ever wakes takers and vice
    //    versa, so no thread wakes up to find the condition it needs is still false.
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    private final Queue<T> queue = new ArrayDeque<>();
    private final int capacity;

    BoundedBuffer(int capacity) {
      this.capacity = capacity;
    }

    void put(T item) {
      lock.lock();
      try {
        // Still a while loop: spurious wakeups apply to Condition too.
        while (queue.size() == capacity) {
          notFull.await();
        }
        queue.add(item);
        System.out.println("[producer] put " + item);
        // A: signal() wakes one waiter, signalAll() wakes all. With one condition per
        //    role every waiter is interchangeable, so signal() is both correct and
        //    cheaper. That is the real payoff of splitting the conditions.
        notEmpty.signal();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        lock.unlock();
      }
    }

    T take() {
      lock.lock();
      try {
        while (queue.isEmpty()) {
          notEmpty.await();
        }
        T item = queue.poll();
        notFull.signal();
        return item;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted", e);
      } finally {
        lock.unlock();
      }
    }

    // Note: await() must be called while holding the lock, exactly like wait(), and it
    // releases the lock while parked. Condition also offers await(timeout) and
    // awaitUninterruptibly(). In production, prefer ArrayBlockingQueue: it is this class,
    // already written and tested.
  }
}
