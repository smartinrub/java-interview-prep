package com.sergiomartinrubio.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Pins down the `java.util.HashMap` behaviour the examples claim.
///
/// The reflection-based tests are skipped rather than failed when the JVM was started
/// without `--add-opens java.base/java.util=ALL-UNNAMED`. Surefire is configured with the
/// flag, so they run under `./mvnw test`.
class HashMapBehaviourTest {

  record Terrible(int id) {

    @Override
    public int hashCode() {
      return 42;
    }
  }

  static final class Mutable {

    private int id;

    Mutable(int id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Mutable o && o.id == id;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }

  @Test
  @DisplayName("mutating a key after insertion makes the entry unreachable but not absent")
  void mutatedKeyIsStranded() {
    Map<Mutable, String> map = new HashMap<>();
    Mutable key = new Mutable(1);
    map.put(key, "stored");

    key.id = 2;

    assertNull(map.get(key), "the map never re-files an entry, so the bucket is wrong now");
    assertEquals(1, map.size(), "the entry is still present and still costing memory");
    assertFalse(map.containsKey(new Mutable(1)));
    assertFalse(map.containsKey(new Mutable(2)));

    key.id = 1;
    assertEquals("stored", map.get(key), "restoring the field makes it findable again");
  }

  @Test
  @DisplayName("a HashSet can hold duplicates if its elements are mutated")
  void mutatedElementBreaksSetInvariant() {
    Set<Mutable> set = new HashSet<>();
    Mutable first = new Mutable(1);
    set.add(first);
    first.id = 2;
    set.add(new Mutable(2));

    assertEquals(2, set.size(), "two elements that are equal to each other");
  }

  @Test
  @DisplayName("colliding keys coexist; hash picks the bucket, equals decides identity")
  void collisionIsNotOverwrite() {
    Map<Terrible, String> map = new HashMap<>();
    map.put(new Terrible(1), "one");
    map.put(new Terrible(2), "two");

    assertEquals(2, map.size());
    assertEquals("one", map.get(new Terrible(1)));

    map.put(new Terrible(1), "replaced");
    assertEquals(2, map.size(), "same hash AND equals -> a replacement");
    assertEquals("replaced", map.get(new Terrible(1)));
  }

  @Test
  @DisplayName("the table is allocated lazily and defaults to 16 buckets")
  void lazyTableAllocation() {
    assumeTrue(HashMapInspector.available(), "needs --add-opens java.base/java.util");

    Map<String, Integer> map = new HashMap<>();
    assertEquals(0, HashMapInspector.capacity(map), "no table before the first put");

    map.put("first", 1);
    assertEquals(16, HashMapInspector.capacity(map), "default capacity is 16");
  }

  @Test
  @DisplayName("the table doubles when size exceeds capacity x 0.75")
  void resizesWhenThresholdExceeded() {
    assumeTrue(HashMapInspector.available(), "needs --add-opens java.base/java.util");

    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < 12; i++) {
      map.put(i, i);
    }
    assertEquals(16, HashMapInspector.capacity(map), "12 is the threshold, not past it");

    map.put(12, 12);
    assertEquals(32, HashMapInspector.capacity(map), "the 13th entry triggers the resize");
  }

  @Test
  @DisplayName("treeification needs 8 collisions AND capacity 64, not just 8 collisions")
  void treeifyRequiresMinimumCapacity() {
    assumeTrue(HashMapInspector.available(), "needs --add-opens java.base/java.util");

    Map<Terrible, Integer> map = new HashMap<>();

    // Eight colliding keys: enough collisions, but the table is far smaller than 64.
    for (int i = 0; i < 8; i++) {
      map.put(new Terrible(i), i);
    }
    assertTrue(HashMapInspector.capacity(map) < 64, "table is still small");
    assertFalse(HashMapInspector.hasTreeBucket(map),
        "below MIN_TREEIFY_CAPACITY the map resizes instead of treeifying");

    // Keep going until the table reaches 64 buckets.
    for (int i = 8; i < 40; i++) {
      map.put(new Terrible(i), i);
    }
    assertTrue(HashMapInspector.capacity(map) >= 64);
    assertTrue(HashMapInspector.hasTreeBucket(map),
        "with capacity >= 64 and a long bucket, it becomes a red-black tree");

    // Correctness is unaffected either way.
    for (int i = 0; i < 40; i++) {
      assertNotNull(map.get(new Terrible(i)), "key " + i + " lost during treeification");
    }
  }

  @Test
  @DisplayName("HashMap.newHashMap avoids the resize that new HashMap<>(n) still pays")
  void presizingWithNewHashMap() {
    assumeTrue(HashMapInspector.available(), "needs --add-opens java.base/java.util");

    int entries = 100;
    Map<Integer, Integer> naive = new HashMap<>(entries);
    Map<Integer, Integer> sized = HashMap.newHashMap(entries);

    int naiveInitial = capacityAfterFirstPut(naive);
    int sizedInitial = capacityAfterFirstPut(sized);

    for (int i = 1; i < entries; i++) {
      naive.put(i, i);
      sized.put(i, i);
    }

    assertTrue(HashMapInspector.capacity(naive) > naiveInitial,
        "new HashMap<>(100) reserves 128 buckets, so 100 entries still force a resize");
    assertEquals(sizedInitial, HashMapInspector.capacity(sized),
        "newHashMap(100) reserves enough buckets up front");
  }

  private int capacityAfterFirstPut(Map<Integer, Integer> map) {
    map.put(0, 0);
    return HashMapInspector.capacity(map);
  }
}
