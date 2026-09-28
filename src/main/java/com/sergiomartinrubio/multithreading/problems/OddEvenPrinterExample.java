package com.sergiomartinrubio.multithreading.problems;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/// Classic whiteboard problem: two threads print 1..N alternately, one odds, one evens.
///
/// It tests whether you can make two threads take strict turns. Three idiomatic answers
/// below; any one of them is a complete answer.
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.problems.OddEvenPrinterExample
public class OddEvenPrinterExample {

  private static final int LIMIT = 10;

  static void main() throws InterruptedException {
    System.out.println("--- wait/notify ---");
    withWaitNotify();

    System.out.println("\n--- Lock + two Conditions ---");
    withConditions();

    System.out.println("\n--- two Semaphores ---");
    withSemaphores();
  }

  /// The version most interviewers expect. Note the `while` loop: an `if` here is a bug,
  /// because notifyAll() can wake a thread whose turn it still is not.
  static void withWaitNotify() throws InterruptedException {
    Object monitor = new Object();
    int[] next = {1};

    Runnable printer = () -> {
      boolean wantOdd = Thread.currentThread().getName().equals("odd");
      synchronized (monitor) {
        while (next[0] <= LIMIT) {
          if (next[0] % 2 == 1 != wantOdd) {
            try {
              monitor.wait();               // releases the monitor and parks
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }
            continue;                        // re-check, never assume
          }
          System.out.println("[" + Thread.currentThread().getName() + "] " + next[0]++);
          monitor.notifyAll();
        }
        // Wake the other thread so it can observe next[0] > LIMIT and exit.
        monitor.notifyAll();
      }
    };

    runBoth(printer);
  }

  /// One condition per role, so each signal reaches exactly the thread that can proceed.
  static void withConditions() throws InterruptedException {
    Lock lock = new ReentrantLock();
    Condition oddTurn = lock.newCondition();
    Condition evenTurn = lock.newCondition();
    int[] next = {1};

    Runnable printer = () -> {
      boolean wantOdd = Thread.currentThread().getName().equals("odd");
      Condition mine = wantOdd ? oddTurn : evenTurn;
      Condition theirs = wantOdd ? evenTurn : oddTurn;

      lock.lock();
      try {
        while (next[0] <= LIMIT) {
          while (next[0] <= LIMIT && (next[0] % 2 == 1) != wantOdd) {
            mine.await();
          }
          if (next[0] > LIMIT) {
            break;
          }
          System.out.println("[" + Thread.currentThread().getName() + "] " + next[0]++);
          theirs.signal();
        }
        theirs.signalAll();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        lock.unlock();
      }
    };

    runBoth(printer);
  }

  /// The cleanest of the three: no shared condition to re-check, the permits themselves
  /// encode whose turn it is. Each thread acquires its own permit and releases the other's.
  static void withSemaphores() throws InterruptedException {
    Semaphore oddPermit = new Semaphore(1);    // odds go first
    Semaphore evenPermit = new Semaphore(0);
    int[] next = {1};

    Runnable printer = () -> {
      boolean isOdd = Thread.currentThread().getName().equals("odd");
      Semaphore mine = isOdd ? oddPermit : evenPermit;
      Semaphore theirs = isOdd ? evenPermit : oddPermit;

      try {
        while (true) {
          mine.acquire();
          if (next[0] > LIMIT) {
            theirs.release();                 // let the other thread exit too
            return;
          }
          System.out.println("[" + Thread.currentThread().getName() + "] " + next[0]++);
          theirs.release();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    };

    runBoth(printer);
  }

  static void runBoth(Runnable printer) throws InterruptedException {
    Thread odd = new Thread(printer, "odd");
    Thread even = new Thread(printer, "even");
    odd.start();
    even.start();
    odd.join();
    even.join();
  }
}
