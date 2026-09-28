package com.sergiomartinrubio.multithreading.synchronization;

import java.util.ArrayDeque;
import java.util.Queue;

/// The low-level `wait()` / `notify()` protocol. Worth understanding, rarely worth writing.
///
/// Interview questions:
/// - Q: Difference between `wait()` and `sleep()`?
/// - Q: Why must `wait()` be called inside a `synchronized` block?
/// - Q: Why is `wait()` always in a `while` loop, never an `if`?
/// - Q: `notify()` vs `notifyAll()`?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.synchronization.WaitNotifyExample
public class WaitNotifyExample {

  static void main() throws InterruptedException {
    // A: sleep() holds every lock it owns and just pauses. wait() RELEASES the monitor
    //    and parks until another thread signals it. sleep() is about time; wait() is
    //    about a condition.
    Mailbox mailbox = new Mailbox(3);

    Thread producer = new Thread(() -> {
      for (int i = 1; i <= 6; i++) {
        mailbox.put("msg-" + i);
      }
    }, "producer");

    Thread consumer = new Thread(() -> {
      for (int i = 1; i <= 6; i++) {
        System.out.println("[consumer] took " + mailbox.take());
      }
    }, "consumer");

    producer.start();
    consumer.start();
    producer.join();
    consumer.join();

    System.out.println("done - in real code use a BlockingQueue instead of this");
  }

  static class Mailbox {

    private final Queue<String> queue = new ArrayDeque<>();
    private final int capacity;

    Mailbox(int capacity) {
      this.capacity = capacity;
    }

    // A: wait() and notify() operate on the object's monitor, so you must hold that
    //    monitor to call them. Otherwise: IllegalMonitorStateException.
    synchronized void put(String message) {
      // A: always `while`, never `if`. Two reasons:
      //    1. spurious wakeups are permitted by the spec
      //    2. with notifyAll(), several waiters wake and race; by the time this one
      //       reacquires the lock another thread may have refilled the queue
      //    So you must re-check the condition after waking, not assume it holds.
      while (queue.size() == capacity) {
        await();
      }
      queue.add(message);
      System.out.println("[producer] put " + message + " (size=" + queue.size() + ")");
      // A: notify() wakes ONE arbitrary waiter. If producers and consumers share a
      //    monitor, it may wake the wrong kind and deadlock the whole thing.
      //    notifyAll() is the safe default; use notify() only when every waiter is
      //    provably interchangeable.
      notifyAll();
    }

    synchronized String take() {
      while (queue.isEmpty()) {
        await();
      }
      String message = queue.poll();
      notifyAll();
      return message;
    }

    private void await() {
      try {
        wait();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted while waiting", e);
      }
    }
  }
}
