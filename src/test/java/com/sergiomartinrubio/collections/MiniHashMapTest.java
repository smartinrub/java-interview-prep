package com.sergiomartinrubio.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Differential test: `MiniHashMap` must agree with `java.util.HashMap` on every operation.
///
/// This is the most useful shape of test for a from-scratch data structure -- rather than
/// guessing at edge cases, mirror every operation against the reference implementation and
/// compare.
class MiniHashMapTest {

  record Terrible(int id) {

    @Override
    public int hashCode() {
      return 42;     // forces every key into one bucket
    }
  }

  @Test
  @DisplayName("put returns the previous value and replaces rather than duplicates")
  void putSemantics() {
    MiniHashMap<String, Integer> map = new MiniHashMap<>();

    assertNull(map.put("a", 1), "first put has no previous value");
    assertEquals(1, map.put("a", 2), "second put returns the old value");
    assertEquals(1, map.size(), "an equal key replaces, it does not add");
    assertEquals(2, map.get("a"));
  }

  @Test
  @DisplayName("get, containsKey and remove handle missing keys")
  void missingKeys() {
    MiniHashMap<String, Integer> map = new MiniHashMap<>();
    map.put("present", 1);

    assertNull(map.get("absent"));
    assertFalse(map.containsKey("absent"));
    assertNull(map.remove("absent"));
    assertEquals(1, map.size(), "a failed remove must not change the size");
  }

  @Test
  @DisplayName("a null key is supported and hashes to bucket 0")
  void nullKey() {
    MiniHashMap<String, Integer> map = new MiniHashMap<>();

    assertNull(map.put(null, 1));
    assertEquals(1, map.get(null));
    assertTrue(map.containsKey(null));
    assertEquals(1, map.put(null, 2));
    assertEquals(1, map.size());
    assertEquals(2, map.remove(null));
    assertTrue(map.isEmpty());
  }

  @Test
  @DisplayName("the table doubles when size exceeds capacity x 0.75")
  void resizesOnLoadFactor() {
    MiniHashMap<Integer, Integer> map = new MiniHashMap<>();
    assertEquals(16, map.capacity());

    for (int i = 0; i < 12; i++) {
      map.put(i, i);
    }
    assertEquals(16, map.capacity(), "12 entries is exactly the threshold, no resize yet");

    map.put(12, 12);
    assertEquals(32, map.capacity(), "the 13th entry exceeds 12, so the table doubles");
  }

  @Test
  @DisplayName("every entry survives a resize")
  void resizePreservesEntries() {
    MiniHashMap<Integer, String> map = new MiniHashMap<>();
    int entries = 500;

    for (int i = 0; i < entries; i++) {
      map.put(i, "value-" + i);
    }

    assertEquals(entries, map.size());
    assertTrue(map.capacity() >= entries / 0.75, "should have grown past the load factor");
    for (int i = 0; i < entries; i++) {
      assertEquals("value-" + i, map.get(i), "entry " + i + " was lost in a resize");
    }
  }

  @Test
  @DisplayName("colliding keys all coexist and remain retrievable")
  void collisionsAreChainedNotOverwritten() {
    MiniHashMap<Terrible, Integer> map = new MiniHashMap<>();
    int keys = 20;

    for (int i = 0; i < keys; i++) {
      map.put(new Terrible(i), i);
    }

    assertEquals(keys, map.size(), "same hash, different keys -> all kept");
    assertEquals(1, map.usedBuckets(), "they must all be in one bucket");
    assertEquals(keys, map.longestBucket());
    for (int i = 0; i < keys; i++) {
      assertEquals(i, map.get(new Terrible(i)));
    }
  }

  @Test
  @DisplayName("removing from the middle of a chain keeps the rest reachable")
  void removeFromMiddleOfChain() {
    MiniHashMap<Terrible, Integer> map = new MiniHashMap<>();
    for (int i = 0; i < 5; i++) {
      map.put(new Terrible(i), i);
    }

    assertEquals(2, map.remove(new Terrible(2)));

    assertEquals(4, map.size());
    assertNull(map.get(new Terrible(2)));
    for (int i : new int[] {0, 1, 3, 4}) {
      assertEquals(i, map.get(new Terrible(i)), "key " + i + " lost when unlinking a neighbour");
    }
  }

  @Test
  @DisplayName("behaves identically to java.util.HashMap over a random operation sequence")
  void agreesWithHashMapUnderRandomOperations() {
    MiniHashMap<Integer, Integer> mine = new MiniHashMap<>();
    Map<Integer, Integer> reference = new HashMap<>();
    Random random = new Random(42);      // fixed seed: reproducible failures
    int keyRange = 300;

    for (int step = 0; step < 20_000; step++) {
      int key = random.nextInt(keyRange);
      switch (random.nextInt(3)) {
        case 0 -> {
          int value = random.nextInt();
          assertEquals(reference.put(key, value), mine.put(key, value),
              "put returned a different previous value at step " + step);
        }
        case 1 -> assertEquals(reference.remove(key), mine.remove(key),
            "remove returned a different value at step " + step);
        default -> assertEquals(reference.get(key), mine.get(key),
            "get disagreed at step " + step);
      }
      assertEquals(reference.size(), mine.size(), "sizes diverged at step " + step);
    }

    for (int key = 0; key < keyRange; key++) {
      assertTrue(Objects.equals(reference.get(key), mine.get(key)),
          "final state differs for key " + key);
    }
  }
}
