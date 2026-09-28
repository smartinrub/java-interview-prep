package com.sergiomartinrubio.collections;

import java.util.HashMap;
import java.util.Map;

/// What happens when keys collide, and when the bucket becomes a tree.
///
/// Interview questions:
/// - Q: What happens on a hash collision?
/// - Q: When does a bucket become a red-black tree?
/// - Q: What does a terrible `hashCode()` cost you?
/// - Q: Why is `String` a good key?
///
/// The treeification result below contradicts the usual "8 collisions become a tree"
/// summary. That is measured, not assumed -- see the note.
///
/// Run with the flag, or the internals sections are skipped:
///   java --add-opens java.base/java.util=ALL-UNNAMED \
///     -cp target/classes com.sergiomartinrubio.collections.HashMapCollisionsExample
public class HashMapCollisionsExample {

  /// The worst possible key: every instance lands in the same bucket.
  record Terrible(int id) {

    @Override
    public int hashCode() {
      return 42;
    }
  }

  /// A sane key, for comparison.
  record Reasonable(int id) {
  }

  static void main() {
    // A: the entry is appended to that bucket's chain. Collision does NOT mean overwrite:
    //    the map compares hashes and then calls equals(), and only replaces the value if
    //    equals() says the keys are the same. Two different keys with the same hash simply
    //    coexist in one bucket.
    collisionIsNotOverwrite();
    whenDoesItTreeify();
    costOfABadHashCode();
    stringKeys();

    System.out.println();
    // A: a bad hashCode does not break correctness, only performance. Everything still
    //    works, because equals() decides identity. You just degrade from O(1) towards
    //    O(log n), silently, with no error to tell you.
    System.out.println("A bad hashCode is a performance bug, not a correctness bug -- which is");
    System.out.println("exactly why it survives code review and shows up under load.");
  }

  static void collisionIsNotOverwrite() {
    System.out.println("--- a collision is not an overwrite ---");
    Map<Terrible, String> map = new HashMap<>();
    map.put(new Terrible(1), "first");
    map.put(new Terrible(2), "second");
    map.put(new Terrible(3), "third");

    System.out.println("  3 keys, all with hashCode 42");
    System.out.println("  size = " + map.size() + "  (all three kept)");
    System.out.println("  get(Terrible(2)) = " + map.get(new Terrible(2)));
    if (HashMapInspector.available()) {
      System.out.println("  used buckets = " + HashMapInspector.usedBuckets(map)
          + ", entries in busiest bucket = " + HashMapInspector.longestChain(map));
    }

    // Same hash AND equals -> now it really is a replacement.
    map.put(new Terrible(2), "replaced");
    System.out.println("  after putting Terrible(2) again: size = " + map.size()
        + ", value = " + map.get(new Terrible(2)));
    System.out.println("  -> hash picks the bucket; equals decides identity");
  }

  /// A: the honest answer has TWO thresholds, and this is where most summaries are wrong.
  ///    A bucket converts to a red-black tree when it reaches TREEIFY_THRESHOLD (8) entries
  ///    **and** the table has at least MIN_TREEIFY_CAPACITY (64) buckets. Below that
  ///    capacity, HashMap RESIZES instead, because spreading the keys over more buckets is
  ///    cheaper than building a tree.
  static void whenDoesItTreeify() {
    System.out.println("\n--- when does a bucket become a tree? ---");
    if (!HashMapInspector.available()) {
      System.out.println(HashMapInspector.unavailableHint());
      return;
    }

    Map<Terrible, Integer> map = new HashMap<>();
    for (int i = 1; i <= 12; i++) {
      map.put(new Terrible(i), i);
      System.out.printf("  size %-3d capacity %-4d busiest bucket %-3d entries, type %s%n",
          map.size(), HashMapInspector.capacity(map), HashMapInspector.longestChain(map),
          HashMapInspector.busiestBucketType(map));
    }

    System.out.println();
    System.out.println("  Read that carefully: at 8 colliding keys the bucket is STILL a Node");
    System.out.println("  chain. The map resized (16 -> 32 -> 64) instead of treeifying,");
    System.out.println("  because a tree needs capacity >= 64 (MIN_TREEIFY_CAPACITY).");
    System.out.println("  Only once capacity reached 64 did the bucket convert to TreeNode.");
    System.out.println();
    System.out.println("  So \"8 collisions make a tree\" is wrong. It is 8 collisions AND");
    System.out.println("  capacity >= 64. Below that, resizing is preferred.");
    System.out.println("  (Trees convert back to lists at UNTREEIFY_THRESHOLD = 6.)");
  }

  /// A: it costs you the map's whole point. Same number of entries, same operations, but
  ///    every lookup walks or searches one overloaded bucket instead of going straight to it.
  static void costOfABadHashCode() {
    System.out.println("\n--- what a bad hashCode costs ---");
    int entries = 20_000;
    int lookups = 200_000;

    Map<Reasonable, Integer> good = new HashMap<>();
    Map<Terrible, Integer> bad = new HashMap<>();
    for (int i = 0; i < entries; i++) {
      good.put(new Reasonable(i), i);
      bad.put(new Terrible(i), i);
    }

    if (HashMapInspector.available()) {
      System.out.printf("  good key: capacity %d, used buckets %d, busiest bucket %d entries%n",
          HashMapInspector.capacity(good), HashMapInspector.usedBuckets(good),
          HashMapInspector.longestChain(good));
      System.out.printf("  bad key : capacity %d, used buckets %d, busiest bucket %d entries,"
              + " treeified? %b%n",
          HashMapInspector.capacity(bad), HashMapInspector.usedBuckets(bad),
          HashMapInspector.longestChain(bad), HashMapInspector.hasTreeBucket(bad));
    }

    long goodNanos = time(() -> {
      for (int i = 0; i < lookups; i++) {
        good.get(new Reasonable(i % entries));
      }
    });
    long badNanos = time(() -> {
      for (int i = 0; i < lookups; i++) {
        bad.get(new Terrible(i % entries));
      }
    });

    System.out.printf("  %,d lookups with a good hashCode: %,6d ms%n", lookups, goodNanos / 1_000_000);
    System.out.printf("  %,d lookups with hashCode() = 42 : %,6d ms%n", lookups, badNanos / 1_000_000);
    if (goodNanos > 0) {
      System.out.printf("  the bad key is roughly %dx slower%n", badNanos / Math.max(goodNanos, 1));
    }
    System.out.println("  Without treeification (pre-Java 8) this would be far worse still:");
    System.out.println("  a linear walk of 20,000 entries per lookup instead of a tree search.");
  }

  /// A: String caches its hash after the first computation, is immutable (so the hash can
  ///    never go stale), and has a well-spread hashCode. Immutability is the important part:
  ///    see EqualsHashCodeContractExample for what a mutable key does.
  static void stringKeys() {
    System.out.println("\n--- why String makes a good key ---");
    String key = "java-interview-prep";
    System.out.println("  \"" + key + "\".hashCode() = " + key.hashCode()
        + "  (cached after the first call, and immutable so it can never go stale)");
    System.out.println("  s[0]*31^(n-1) + s[1]*31^(n-2) + ... -- 31 is odd, prime, and");
    System.out.println("  31*i compiles to (i << 5) - i");
  }

  static long time(Runnable work) {
    work.run();                       // warm up, so the JIT is not part of the measurement
    long start = System.nanoTime();
    work.run();
    return System.nanoTime() - start;
  }
}
