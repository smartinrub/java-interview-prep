package com.sergiomartinrubio.collections;

import java.util.Objects;

/// A HashMap built from scratch: separate chaining, power-of-two table, resize on load factor.
///
/// This is the "now implement one" follow-up to the HashMap internals question. It is
/// deliberately the real algorithm, minus the red-black tree optimisation, so the core is
/// visible in one screen.
///
/// Not thread-safe, and not a drop-in `Map` -- the point is the mechanism, not the API
/// surface. See `MiniHashMapExample` for a runnable demo and `MiniHashMapTest` for the
/// assertions that it matches `java.util.HashMap`.
public class MiniHashMap<K, V> {

  private static final int DEFAULT_CAPACITY = 16;
  private static final float LOAD_FACTOR = 0.75f;

  /// One entry. The hash is cached because recomputing it on every comparison and every
  /// resize would be wasteful, and for a String key it is the expensive part.
  private static final class Node<K, V> {

    final int hash;
    final K key;
    V value;
    Node<K, V> next;

    Node(int hash, K key, V value, Node<K, V> next) {
      this.hash = hash;
      this.key = key;
      this.value = value;
      this.next = next;
    }
  }

  private Node<K, V>[] table;
  private int size;

  @SuppressWarnings("unchecked")
  public MiniHashMap() {
    this.table = new Node[DEFAULT_CAPACITY];
  }

  /// Spreads the high bits down, exactly as HashMap does. Without this, masking with
  /// `capacity - 1` would ignore the top half of the hash entirely.
  private static int spread(Object key) {
    if (key == null) {
      return 0;
    }
    int h = key.hashCode();
    return h ^ (h >>> 16);
  }

  /// Capacity is a power of two, so `& (n - 1)` replaces the slower `% n`.
  private int indexFor(int hash, int capacity) {
    return hash & (capacity - 1);
  }

  public V put(K key, V value) {
    int hash = spread(key);
    int index = indexFor(hash, table.length);

    // Walk the bucket. Compare the cheap cached hash FIRST and only then call equals,
    // which is the optimisation that makes a bad-but-not-terrible hashCode survivable.
    for (Node<K, V> node = table[index]; node != null; node = node.next) {
      if (node.hash == hash && Objects.equals(node.key, key)) {
        V previous = node.value;
        node.value = value;
        return previous;
      }
    }

    // Not present: prepend. HashMap appends to keep insertion order within a bucket;
    // prepending is simpler and equally correct, since bucket order is not part of the
    // contract.
    table[index] = new Node<>(hash, key, value, table[index]);
    size++;

    if (size > table.length * LOAD_FACTOR) {
      resize();
    }
    return null;
  }

  public V get(Object key) {
    Node<K, V> node = findNode(key);
    return node == null ? null : node.value;
  }

  public boolean containsKey(Object key) {
    return findNode(key) != null;
  }

  private Node<K, V> findNode(Object key) {
    int hash = spread(key);
    for (Node<K, V> node = table[indexFor(hash, table.length)]; node != null; node = node.next) {
      if (node.hash == hash && Objects.equals(node.key, key)) {
        return node;
      }
    }
    return null;
  }

  public V remove(Object key) {
    int hash = spread(key);
    int index = indexFor(hash, table.length);

    Node<K, V> previous = null;
    for (Node<K, V> node = table[index]; node != null; node = node.next) {
      if (node.hash == hash && Objects.equals(node.key, key)) {
        if (previous == null) {
          table[index] = node.next;
        } else {
          previous.next = node.next;
        }
        size--;
        return node.value;
      }
      previous = node;
    }
    return null;
  }

  /// Doubles the table and re-files every entry. O(n), but it happens rarely enough that
  /// insertion stays amortised O(1).
  @SuppressWarnings("unchecked")
  private void resize() {
    Node<K, V>[] old = table;
    int newCapacity = old.length << 1;
    Node<K, V>[] replacement = new Node[newCapacity];

    for (Node<K, V> head : old) {
      Node<K, V> node = head;
      while (node != null) {
        Node<K, V> next = node.next;           // remember it before we repoint node.next
        int index = indexFor(node.hash, newCapacity);
        node.next = replacement[index];
        replacement[index] = node;
        node = next;
      }
    }
    this.table = replacement;
  }

  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  /// Exposed so the example can show the same internals the real HashMap hides.
  public int capacity() {
    return table.length;
  }

  public int usedBuckets() {
    int used = 0;
    for (Node<K, V> head : table) {
      if (head != null) {
        used++;
      }
    }
    return used;
  }

  public int longestBucket() {
    int longest = 0;
    for (Node<K, V> head : table) {
      int length = 0;
      for (Node<K, V> node = head; node != null; node = node.next) {
        length++;
      }
      longest = Math.max(longest, length);
    }
    return longest;
  }
}
