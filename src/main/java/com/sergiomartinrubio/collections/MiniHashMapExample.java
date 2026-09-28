package com.sergiomartinrubio.collections;

import java.util.HashMap;
import java.util.Map;

/// Drives `MiniHashMap` and checks it against `java.util.HashMap`.
///
/// Interview questions:
/// - Q: Implement a HashMap.
/// - Q: Why chaining rather than open addressing?
/// - Q: What did you leave out compared to the real one?
///
/// Run: java -cp target/classes com.sergiomartinrubio.collections.MiniHashMapExample
public class MiniHashMapExample {

  record Terrible(int id) {

    @Override
    public int hashCode() {
      return 42;
    }
  }

  static void main() {
    basics();
    growth();
    collisions();
    agreesWithHashMap();

    System.out.println();
    // A: chaining is what HashMap does, and it degrades gracefully -- a bad hash makes one
    //    bucket long but never breaks the invariants. Open addressing (probing for the next
    //    free slot) is more cache-friendly and uses less memory per entry, but deletion needs
    //    tombstones and clustering makes it collapse badly under a poor hash.
    //
    // A: what is missing versus java.util.HashMap:
    //      - red-black tree buckets (TREEIFY_THRESHOLD), so the worst case here is O(n)
    //      - the Map interface: entrySet/keySet/values views, iteration, equals/hashCode
    //      - modCount and fail-fast iterators
    //      - lazy table allocation, and a constructor taking an initial capacity
    //      - null-key special casing beyond hashing it to bucket 0
    //      - thread safety (it has none, exactly like HashMap)
    System.out.println("Worst case here is O(n): no tree buckets. That single omission is the");
    System.out.println("difference between this and the Java 8 rewrite of HashMap.");
  }

  static void basics() {
    System.out.println("--- basics ---");
    MiniHashMap<String, Integer> map = new MiniHashMap<>();

    System.out.println("  put(a,1) returns previous value: " + map.put("a", 1));
    System.out.println("  put(a,2) returns previous value: " + map.put("a", 2));
    System.out.println("  get(a)          = " + map.get("a"));
    System.out.println("  size            = " + map.size() + "   (replaced, not added)");
    System.out.println("  get(missing)    = " + map.get("missing"));
    System.out.println("  containsKey(a)  = " + map.containsKey("a"));
    System.out.println("  remove(a)       = " + map.remove("a"));
    System.out.println("  size after remove = " + map.size());

    // A null key hashes to 0, the same as the real HashMap.
    map.put(null, 99);
    System.out.println("  get(null)       = " + map.get(null));
  }

  static void growth() {
    System.out.println("\n--- growth ---");
    MiniHashMap<Integer, Integer> map = new MiniHashMap<>();
    int previous = map.capacity();
    System.out.println("  initial capacity = " + previous);

    for (int i = 0; i < 100; i++) {
      map.put(i, i);
      if (map.capacity() != previous) {
        System.out.println("  resized at size " + map.size() + ": " + previous
            + " -> " + map.capacity());
        previous = map.capacity();
      }
    }
    System.out.println("  final: size " + map.size() + ", capacity " + map.capacity()
        + ", used buckets " + map.usedBuckets());
  }

  static void collisions() {
    System.out.println("\n--- all keys colliding ---");
    MiniHashMap<Terrible, Integer> map = new MiniHashMap<>();
    for (int i = 1; i <= 10; i++) {
      map.put(new Terrible(i), i);
    }
    System.out.println("  size " + map.size() + ", used buckets " + map.usedBuckets()
        + ", longest bucket " + map.longestBucket());
    System.out.println("  get(Terrible(7)) = " + map.get(new Terrible(7))
        + "   (correct, just slow -- O(n) walk)");
  }

  /// The real check: same operations, same answers as java.util.HashMap.
  static void agreesWithHashMap() {
    System.out.println("\n--- agreement with java.util.HashMap ---");
    MiniHashMap<String, Integer> mine = new MiniHashMap<>();
    Map<String, Integer> reference = new HashMap<>();

    for (int i = 0; i < 2_000; i++) {
      String key = "key-" + i;
      mine.put(key, i);
      reference.put(key, i);
    }
    for (int i = 0; i < 2_000; i += 3) {
      String key = "key-" + i;
      mine.remove(key);
      reference.remove(key);
    }

    boolean sizesMatch = mine.size() == reference.size();
    boolean valuesMatch = true;
    for (int i = 0; i < 2_000; i++) {
      String key = "key-" + i;
      if (!java.util.Objects.equals(mine.get(key), reference.get(key))) {
        valuesMatch = false;
        break;
      }
    }

    System.out.println("  after 2,000 puts and ~667 removes");
    System.out.println("  sizes match  = " + sizesMatch + " (" + mine.size() + ")");
    System.out.println("  values match = " + valuesMatch);
    if (!sizesMatch || !valuesMatch) {
      throw new AssertionError("MiniHashMap disagrees with java.util.HashMap");
    }
  }
}
