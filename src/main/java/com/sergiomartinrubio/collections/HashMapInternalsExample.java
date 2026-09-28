package com.sergiomartinrubio.collections;

import java.util.HashMap;
import java.util.Map;

/// How `HashMap` is laid out, and what happens when it grows.
///
/// Interview questions:
/// - Q: What is a HashMap and how does it work internally?
/// - Q: Time complexity of get and put?
/// - Q: What are the default capacity and load factor?
/// - Q: How does a HashMap grow, and what does resizing cost?
/// - Q: How is the bucket index computed, and why the extra hash step?
/// - Q: How do you size one up front?
///
/// Run with the flag, or the internals sections are skipped:
///   java --add-opens java.base/java.util=ALL-UNNAMED \
///     -cp target/classes com.sergiomartinrubio.collections.HashMapInternalsExample
public class HashMapInternalsExample {

  static void main() {
    // A: an array of buckets ("the table"), where each bucket holds the entries whose hash
    //    maps to that index. An entry stores the key, the value, the cached hash and a
    //    pointer to the next entry in the same bucket. Collisions are handled by CHAINING:
    //    a linked list per bucket, which becomes a red-black tree if it gets long enough.
    //
    //    put(k,v): hash the key -> pick a bucket -> walk the bucket comparing hashes and
    //              then equals() -> replace the value if the key is there, else append.
    //    get(k):   hash the key -> pick a bucket -> walk it the same way.
    structure();
    lazyAllocation();
    resizeSchedule();
    bucketIndexing();
    presizing();

    System.out.println();
    // A: O(1) average for both get and put, because a good hash spreads keys across buckets
    //    so each bucket holds ~1 entry. The worst case is when everything lands in ONE
    //    bucket:
    //      before Java 8 : O(n)        -- a linked list walk
    //      Java 8+       : O(log n)    -- the bucket becomes a red-black tree
    //    See HashMapCollisionsExample, which measures the difference.
    //
    //    Resizing is O(n) but amortised: it happens on a shrinking fraction of insertions,
    //    so the average insert stays O(1).
    System.out.println("get/put: O(1) average, O(log n) worst case since Java 8 (O(n) before).");
  }

  static void structure() {
    System.out.println("--- structure ---");
    Map<String, Integer> map = new HashMap<>();
    map.put("alice", 1);
    map.put("bob", 2);
    map.put("carol", 3);

    if (!HashMapInspector.available()) {
      System.out.println(HashMapInspector.unavailableHint());
      return;
    }
    System.out.println("  size          = " + map.size() + "   (entries)");
    System.out.println("  capacity      = " + HashMapInspector.capacity(map) + "   (buckets)");
    System.out.println("  used buckets  = " + HashMapInspector.usedBuckets(map));
    System.out.println("  busiest bucket= " + HashMapInspector.longestChain(map) + " entries");
    System.out.println("  Note size and capacity are different things: size is how many");
    System.out.println("  entries you put in, capacity is how many buckets exist to hold them.");
  }

  /// A: the default capacity is 16 and the default load factor is 0.75. But the table is
  ///    allocated LAZILY, so a brand-new HashMap holds no array at all -- `new HashMap<>()`
  ///    is cheap, and 16 buckets only appear on the first put.
  static void lazyAllocation() {
    System.out.println("\n--- lazy allocation ---");
    if (!HashMapInspector.available()) {
      System.out.println(HashMapInspector.unavailableHint());
      return;
    }
    Map<String, Integer> map = new HashMap<>();
    System.out.println("  capacity before any put = " + HashMapInspector.capacity(map));
    map.put("first", 1);
    System.out.println("  capacity after first put = " + HashMapInspector.capacity(map)
        + "   (the default 16)");
  }

  /// A: when size exceeds capacity x loadFactor, the table DOUBLES and every entry is
  ///    rehashed into the new, larger table. Watch the exact points below.
  static void resizeSchedule() {
    System.out.println("\n--- growth ---");
    if (!HashMapInspector.available()) {
      System.out.println(HashMapInspector.unavailableHint());
      return;
    }
    Map<Integer, Integer> map = new HashMap<>();
    int previousCapacity = 0;

    for (int i = 0; i < 200; i++) {
      map.put(i, i);
      int capacity = HashMapInspector.capacity(map);
      if (capacity != previousCapacity) {
        if (previousCapacity != 0) {
          System.out.printf("  resized at size %-4d : %3d -> %3d buckets"
              + "   (threshold was %d x 0.75 = %.0f)%n",
              map.size(), previousCapacity, capacity,
              previousCapacity, previousCapacity * 0.75);
        }
        previousCapacity = capacity;
      }
    }
    System.out.println("  Resize happens when size EXCEEDS the threshold, so a 16-bucket table");
    System.out.println("  grows on the 13th entry, not the 12th.");
    System.out.println("  Cost: a new array plus a rehash of every entry. Doubling keeps it");
    System.out.println("  amortised O(1) per insert, but it is a latency spike on a big map.");
  }

  /// A: `index = (capacity - 1) & spread(hash)` where `spread(h) = h ^ (h >>> 16)`.
  ///
  ///    Capacity is always a power of two, which makes `(n-1) & h` a fast substitute for
  ///    `h % n`. But masking with `n-1` only looks at the LOW bits of the hash, so two keys
  ///    differing only in their high bits would always collide. XOR-ing the high 16 bits
  ///    down mixes them in, so high-bit differences still affect the bucket.
  static void bucketIndexing() {
    System.out.println("\n--- index = (capacity - 1) & (h ^ h >>> 16) ---");
    System.out.printf("  %-10s %-12s %-12s %s%n", "hashCode", "spread", "bucket(16)", "note");
    int[] hashes = {0, 1, 16, 17, 65536, 65537};
    for (int hash : hashes) {
      int spread = hash ^ (hash >>> 16);
      int bucket = (16 - 1) & spread;
      String note = switch (hash) {
        case 16 -> "collides with 0 in a 16-bucket table";
        case 65536 -> "high bits folded down by the XOR";
        default -> "";
      };
      System.out.printf("  %-10d %-12d %-12d %s%n", hash, spread, bucket, note);
    }
  }

  /// A: pass the expected size, but remember the argument is CAPACITY, not size. For n
  ///    entries you want capacity > n / 0.75, otherwise you still resize on the way.
  ///    Since Java 19 there is a factory method that does the arithmetic for you.
  ///
  ///    Note all three below FINISH at the same capacity. What differs is how many rehashes
  ///    they paid to get there, which is the whole point of pre-sizing.
  static void presizing() {
    System.out.println("\n--- sizing up front ---");
    if (!HashMapInspector.available()) {
      System.out.println(HashMapInspector.unavailableHint());
      return;
    }
    int expected = 100;

    report("new HashMap<>(100)", new HashMap<>(expected), expected);
    report("HashMap.newHashMap(100)", HashMap.newHashMap(expected), expected);
    report("new HashMap<>(100/0.75f + 1)", new HashMap<>((int) (expected / 0.75f) + 1), expected);

    System.out.println("  Same final capacity, but the naive version rehashed every entry once");
    System.out.println("  on the way. The constructor takes CAPACITY; newHashMap takes the");
    System.out.println("  number of entries you intend to store.");
  }

  /// Fills the map and counts how many times the table had to be reallocated.
  static void report(String label, Map<Integer, Integer> map, int entries) {
    int initialCapacity = 0;
    int resizes = 0;
    int previous = 0;

    for (int i = 0; i < entries; i++) {
      map.put(i, i);
      int capacity = HashMapInspector.capacity(map);
      if (capacity != previous) {
        if (previous == 0) {
          initialCapacity = capacity;
        } else {
          resizes++;
        }
        previous = capacity;
      }
    }

    System.out.printf("  %-30s start %3d -> final %3d, resizes: %d%n",
        label, initialCapacity, HashMapInspector.capacity(map), resizes);
  }
}
