package com.sergiomartinrubio.multithreading.problems;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.ReentrantLock;

/// Classic whiteboard problem: five philosophers, five forks, no deadlock.
///
/// The point of the exercise is to name a deadlock-prevention strategy and justify it.
/// Two are implemented here; both break the circular-wait condition.
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.problems.DiningPhilosophersExample
public class DiningPhilosophersExample {

  private static final int PHILOSOPHERS = 5;
  private static final int MEALS = 3;

  static void main() throws InterruptedException {
    System.out.println("--- strategy 1: global fork ordering ---");
    dine(DiningPhilosophersExample::eatWithOrderedForks);

    System.out.println("\n--- strategy 2: tryLock and back off ---");
    dine(DiningPhilosophersExample::eatWithTryLock);

    // A third common answer: allow at most PHILOSOPHERS - 1 to sit down at once, using a
    // Semaphore(4). With one seat always free, at least one philosopher can always get
    // both forks, so the cycle never closes.
  }

  interface Strategy {

    void eat(ReentrantLock[] forks, AtomicIntegerArray meals, int id) throws InterruptedException;
  }

  static void dine(Strategy strategy) throws InterruptedException {
    ReentrantLock[] forks = new ReentrantLock[PHILOSOPHERS];
    for (int i = 0; i < PHILOSOPHERS; i++) {
      forks[i] = new ReentrantLock();
    }
    AtomicIntegerArray meals = new AtomicIntegerArray(PHILOSOPHERS);

    try (ExecutorService table = Executors.newFixedThreadPool(PHILOSOPHERS)) {
      for (int i = 0; i < PHILOSOPHERS; i++) {
        int id = i;
        table.submit(() -> {
          try {
            while (meals.get(id) < MEALS) {
              strategy.eat(forks, meals, id);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return null;
        });
      }
    }

    System.out.print("meals eaten: ");
    for (int i = 0; i < PHILOSOPHERS; i++) {
      System.out.print(meals.get(i) + " ");
    }
    System.out.println("(everyone ate " + MEALS + ", nobody starved)");
  }

  /// Strategy 1: always take the lower-numbered fork first. Since every philosopher
  /// acquires in the same global order, no cycle of waits can form. The asymmetry is
  /// what saves you: the last philosopher reaches for fork 0 first, not fork 4.
  static void eatWithOrderedForks(ReentrantLock[] forks, AtomicIntegerArray meals, int id)
      throws InterruptedException {
    int left = id;
    int right = (id + 1) % PHILOSOPHERS;
    ReentrantLock first = forks[Math.min(left, right)];
    ReentrantLock second = forks[Math.max(left, right)];

    first.lock();
    try {
      second.lock();
      try {
        meals.incrementAndGet(id);
        System.out.println("philosopher " + id + " ate meal " + meals.get(id));
        TimeUnit.MILLISECONDS.sleep(10);
      } finally {
        second.unlock();
      }
    } finally {
      first.unlock();
    }
  }

  /// Strategy 2: grab the left fork, then try for the right one with a timeout. Failing
  /// that, put the left one back and think for a while. No thread ever holds one fork
  /// indefinitely, so hold-and-wait is broken instead of circular wait.
  /// The random back-off matters: without it, threads can retry in lockstep (livelock).
  static void eatWithTryLock(ReentrantLock[] forks, AtomicIntegerArray meals, int id)
      throws InterruptedException {
    ReentrantLock left = forks[id];
    ReentrantLock right = forks[(id + 1) % PHILOSOPHERS];

    if (!left.tryLock(50, TimeUnit.MILLISECONDS)) {
      return;
    }
    try {
      if (!right.tryLock(50, TimeUnit.MILLISECONDS)) {
        TimeUnit.MILLISECONDS.sleep(1 + (long) (Math.random() * 10));
        return;
      }
      try {
        meals.incrementAndGet(id);
        System.out.println("philosopher " + id + " ate meal " + meals.get(id));
        TimeUnit.MILLISECONDS.sleep(10);
      } finally {
        right.unlock();
      }
    } finally {
      left.unlock();
    }
  }
}
