package com.sergiomartinrubio.multithreading.locks;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/// Many concurrent readers, one exclusive writer.
///
/// Interview questions:
/// - Q: When does a `ReadWriteLock` pay off?
/// - Q: Can you upgrade a read lock to a write lock?
/// - Q: What is writer starvation?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.locks.ReadWriteLockExample
public class ReadWriteLockExample {

  static void main() throws InterruptedException {
    // A: read-heavy workloads with expensive reads. Readers do not block each other,
    //    so throughput scales with cores. With short reads or frequent writes the extra
    //    bookkeeping costs more than a plain lock, and a ConcurrentHashMap usually beats
    //    both. Measure before reaching for it.
    Cache cache = new Cache();
    cache.put("a", "1");

    try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
      for (int i = 0; i < 5; i++) {
        executor.submit(() -> System.out.println(
            "[" + Thread.currentThread().getName() + "] read a=" + cache.get("a")));
      }
      executor.submit(() -> cache.put("b", "2"));
    }

    System.out.println("size = " + cache.size());

    // A: no. Attempting to acquire the write lock while holding the read lock DEADLOCKS
    //    (with one thread, instantly). You must release the read lock, take the write
    //    lock, then re-check the condition, because the state can change in between.
    //    Downgrading write -> read is legal: take the read lock before releasing the write.
    System.out.println("computeIfAbsent(c) = " + cache.computeIfAbsent("c", () -> "3"));
  }

  static class Cache {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, String> data = new HashMap<>();

    String get(String key) {
      lock.readLock().lock();
      try {
        return data.get(key);
      } finally {
        lock.readLock().unlock();
      }
    }

    void put(String key, String value) {
      lock.writeLock().lock();
      try {
        System.out.println("[" + Thread.currentThread().getName() + "] write " + key);
        data.put(key, value);
      } finally {
        lock.writeLock().unlock();
      }
    }

    int size() {
      lock.readLock().lock();
      try {
        return data.size();
      } finally {
        lock.readLock().unlock();
      }
    }

    /// The correct read-then-write shape: no lock upgrade, and re-check under the write lock.
    String computeIfAbsent(String key, java.util.function.Supplier<String> supplier) {
      lock.readLock().lock();
      try {
        String existing = data.get(key);
        if (existing != null) {
          return existing;
        }
      } finally {
        lock.readLock().unlock();
      }

      lock.writeLock().lock();
      try {
        // Another thread may have inserted it while we held no lock at all.
        return data.computeIfAbsent(key, ignored -> supplier.get());
      } finally {
        lock.writeLock().unlock();
      }
    }

    // A: writer starvation is a steady stream of readers never leaving a gap for the
    //    writer. ReentrantReadWriteLock's non-fair mode does not queue readers behind a
    //    waiting writer by default; new ReentrantReadWriteLock(true) does, at a
    //    throughput cost. StampedLock offers optimistic reads as a faster alternative.
  }
}
