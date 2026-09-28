package com.sergiomartinrubio.multithreading.collections;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// `ConcurrentHashMap`: the default concurrent map, and its atomic compound operations.
///
/// Interview questions:
/// - Q: How is `ConcurrentHashMap` different from `Hashtable`?
/// - Q: What does its iterator guarantee?
/// - Q: Which operations are atomic?
/// - Q: Why does it reject `null` keys and values?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.collections.ConcurrentHashMapExample
public class ConcurrentHashMapExample {

  static void main() throws InterruptedException {
    Map<String, Integer> counts = new ConcurrentHashMap<>();

    // A: Hashtable locks the entire map on every operation. ConcurrentHashMap locks only
    //    the bin being written (CAS plus a per-bin lock since Java 8; lock striping
    //    before that), and reads are lock-free entirely. So reads never block and
    //    unrelated writes proceed in parallel.
    try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
      for (int t = 0; t < 4; t++) {
        executor.submit(() -> {
          for (int i = 0; i < 1_000; i++) {
            // A: merge/compute/computeIfAbsent/putIfAbsent/replace are atomic. The
            //    function runs while the bin is locked, so no update is lost.
            counts.merge("hits", 1, Integer::sum);
          }
        });
      }
    }
    System.out.println("hits = " + counts.get("hits") + " (expected 4000)");

    // The broken version of the same thing: two safe calls, one unsafe sequence.
    //   Integer current = counts.get("hits");
    //   counts.put("hits", current + 1);     // lost updates

    counts.putIfAbsent("new", 1);
    counts.putIfAbsent("new", 99);            // ignored, key exists
    counts.computeIfAbsent("lazy", key -> expensiveDefault());
    counts.computeIfPresent("new", (key, value) -> value + 10);
    System.out.println(counts);

    // A: weakly consistent. The iterator reflects the map at some point since creation,
    //    never throws ConcurrentModificationException, and may or may not show
    //    concurrent updates. size() is likewise an estimate under concurrent writes.
    counts.forEach((key, value) -> System.out.println("  " + key + " -> " + value));

    // A: null would be ambiguous. get() returning null could mean "absent" or
    //    "mapped to null", and in a concurrent map you cannot resolve that with a
    //    follow-up containsKey() because the answer may change in between.
    try {
      counts.put("bad", null);
    } catch (NullPointerException e) {
      System.out.println("null values rejected: " + e.getClass().getSimpleName());
    }

    // Also worth naming: ConcurrentSkipListMap for a sorted concurrent map,
    // and ConcurrentHashMap.newKeySet() for a concurrent Set.
  }

  static Integer expensiveDefault() {
    return 42;
  }
}
