package com.sergiomartinrubio.multithreading.synchronization;

import java.util.ArrayList;
import java.util.List;

/// Narrowing the critical section, and choosing the right lock object.
///
/// Interview questions:
/// - Q: Why use a block instead of marking the whole method `synchronized`?
/// - Q: Why lock on a private final object rather than on `this`?
/// - Q: Why never lock on a `String` literal or a boxed primitive?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.synchronization.SynchronizedBlockExample
public class SynchronizedBlockExample {

  static void main() throws InterruptedException {
    EventBuffer buffer = new EventBuffer();

    Thread a = new Thread(() -> buffer.record("a"));
    Thread b = new Thread(() -> buffer.record("b"));
    a.start();
    b.start();
    a.join();
    b.join();

    System.out.println("recorded " + buffer.size() + " events (expected 2000)");
  }

  static class EventBuffer {

    // A: a private final lock cannot be acquired by outside code, so no caller can
    //    accidentally (or maliciously) hold your lock and stall you. Locking on `this`
    //    publishes your lock as part of your API.
    //
    //    Never lock on a String literal (interned and shared JVM-wide) or a boxed
    //    primitive (Integer.valueOf caches small values, so unrelated classes can
    //    end up sharing one lock).
    private final Object lock = new Object();
    private final List<String> events = new ArrayList<>();

    void record(String name) {
      for (int i = 0; i < 1_000; i++) {
        // A: only the shared-state mutation needs the lock. Formatting the payload is
        //    thread-local work, so keeping it outside shortens the critical section and
        //    cuts contention. Locks are about the narrowest region that must be atomic.
        String event = name + "-" + i;

        synchronized (lock) {
          events.add(event);
        }
      }
    }

    int size() {
      synchronized (lock) {
        return events.size();
      }
    }
  }
}
