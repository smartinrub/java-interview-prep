package com.sergiomartinrubio.multithreading.synchronization;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/// The same counter, four ways: one broken and three correct.
///
/// Extracted from the examples so `RaceConditionExample` can demonstrate the difference
/// and `CountersTest` can assert it.
public final class Counters {

  private Counters() {
  }

  /// One `count++` is three operations: read, add, write. Two threads can read the same
  /// value and both write back the same result, so an update is lost.
  public interface Counter {

    void increment();

    int value();
  }

  /// Broken on purpose: no mutual exclusion and no visibility guarantee.
  public static final class Unsafe implements Counter {

    private int count;

    @Override
    public void increment() {
      count++;
    }

    @Override
    public int value() {
      return count;
    }
  }

  /// Fix 1: `synchronized` gives both mutual exclusion and visibility.
  /// The lock is `this`, so it is exposed to any caller holding the object.
  public static final class Synchronized implements Counter {

    private int count;

    @Override
    public synchronized void increment() {
      count++;
    }

    /// The reader must synchronize too, otherwise it may not see the writes.
    @Override
    public synchronized int value() {
      return count;
    }
  }

  /// Fix 2: a private lock object. Same guarantees, but no caller can lock on it,
  /// and it leaves room for a `ReadWriteLock` or a timed `tryLock()` later.
  public static final class Locked implements Counter {

    private final Lock lock = new ReentrantLock();
    private int count;

    @Override
    public void increment() {
      lock.lock();
      try {
        count++;
      } finally {
        // Always in a finally block: the lock must be released even on exception.
        lock.unlock();
      }
    }

    @Override
    public int value() {
      lock.lock();
      try {
        return count;
      } finally {
        lock.unlock();
      }
    }
  }

  /// Fix 3: lock-free compare-and-swap. The cheapest option for a single variable,
  /// and the one to reach for when the whole critical section IS the update.
  public static final class Atomic implements Counter {

    private final AtomicInteger count = new AtomicInteger();

    @Override
    public void increment() {
      count.incrementAndGet();
    }

    @Override
    public int value() {
      return count.get();
    }
  }
}
