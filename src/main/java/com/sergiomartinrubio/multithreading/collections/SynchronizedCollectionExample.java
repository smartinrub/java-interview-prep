package com.sergiomartinrubio.multithreading.collections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// `Collections.synchronizedXxx()`: thread-safe per call, not per sequence of calls.
///
/// Interview questions:
/// - Q: Is a synchronized collection enough to make my code thread-safe?
/// - Q: Why does iterating one still need external synchronization?
/// - Q: `Vector`/`Hashtable` vs `Collections.synchronizedList` vs `ConcurrentHashMap`?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.collections.SynchronizedCollectionExample
public class SynchronizedCollectionExample {

  static void main() {
    List<Integer> list = Collections.synchronizedList(new ArrayList<>());
    Map<String, Integer> map = Collections.synchronizedMap(new HashMap<>());

    // A: no. Every INDIVIDUAL method is synchronized, so a single put() or get() is safe.
    //    A sequence of them is not atomic, and that is where the bugs live.
    //    Check-then-act is broken even though both calls are individually safe:
    if (!map.containsKey("key")) {   // thread B can insert right here
      map.put("key", 1);             // and this silently overwrites it
    }

    // The fix is either an explicit lock over the whole sequence...
    synchronized (map) {
      if (!map.containsKey("key")) {
        map.put("key", 1);
      }
    }
    // ...or a collection with a compound atomic operation, which is the better answer:
    //   concurrentMap.putIfAbsent("key", 1);
    //   concurrentMap.computeIfAbsent("key", k -> expensive());

    list.addAll(List.of(1, 2, 3));

    // A: the iterator itself is not synchronized. Another thread mutating the list
    //    during iteration triggers ConcurrentModificationException, so you must hold the
    //    collection's own monitor for the whole loop. Note this serialises all readers.
    synchronized (list) {
      for (Integer value : list) {
        System.out.println("value = " + value);
      }
    }

    // A: all three synchronize, but at different granularity:
    //    Vector / Hashtable        - legacy, every method synchronized on the object
    //    Collections.synchronizedX - a wrapper doing the same around any collection
    //    ConcurrentHashMap         - built for concurrency: no single global lock, so
    //                                readers do not block and writers contend per bin
    //    For new code the answer is almost always the java.util.concurrent collection.
    System.out.println("map = " + map + ", list = " + list);
  }
}
