package com.sergiomartinrubio.collections;

import java.lang.reflect.Field;
import java.util.Map;

/// Reads the private internals of a `java.util.HashMap` so the examples can show what is
/// actually happening rather than assert it.
///
/// This needs the `java.util` package to be opened for reflection:
///
///   java --add-opens java.base/java.util=ALL-UNNAMED ...
///
/// Without the flag every method here returns "unavailable" instead of throwing, so the
/// examples still run and print their behavioural sections.
public final class HashMapInspector {

  private static final Field TABLE = findTableField();

  private HashMapInspector() {
  }

  private static Field findTableField() {
    try {
      Field field = java.util.HashMap.class.getDeclaredField("table");
      field.setAccessible(true);
      return field;
    } catch (RuntimeException | ReflectiveOperationException e) {
      // InaccessibleObjectException when --add-opens is missing.
      return null;
    }
  }

  public static boolean available() {
    return TABLE != null;
  }

  public static String unavailableHint() {
    return "  (internals unavailable -- rerun with "
        + "--add-opens java.base/java.util=ALL-UNNAMED to see them)";
  }

  /// Length of the internal bucket array. 0 before the first insertion, because the table
  /// is allocated lazily.
  public static int capacity(Map<?, ?> map) {
    Object[] table = table(map);
    return table == null ? 0 : table.length;
  }

  /// Number of entries in each bucket, indexed by bucket.
  public static int[] bucketSizes(Map<?, ?> map) {
    Object[] table = table(map);
    if (table == null) {
      return new int[0];
    }
    int[] sizes = new int[table.length];
    for (int i = 0; i < table.length; i++) {
      sizes[i] = chainLength(table[i]);
    }
    return sizes;
  }

  /// The runtime class of the first node in the busiest bucket: `Node` for a linked list,
  /// `TreeNode` once the bucket has been converted to a red-black tree.
  public static String busiestBucketType(Map<?, ?> map) {
    Object[] table = table(map);
    if (table == null) {
      return "none";
    }
    Object busiest = null;
    int best = 0;
    for (Object bucket : table) {
      int length = chainLength(bucket);
      if (length > best) {
        best = length;
        busiest = bucket;
      }
    }
    return busiest == null ? "none" : busiest.getClass().getSimpleName();
  }

  public static boolean hasTreeBucket(Map<?, ?> map) {
    Object[] table = table(map);
    if (table == null) {
      return false;
    }
    for (Object bucket : table) {
      if (bucket != null && bucket.getClass().getSimpleName().contains("TreeNode")) {
        return true;
      }
    }
    return false;
  }

  public static int usedBuckets(Map<?, ?> map) {
    int used = 0;
    for (int size : bucketSizes(map)) {
      if (size > 0) {
        used++;
      }
    }
    return used;
  }

  /// Entry count of the busiest bucket. Note this counts ENTRIES, not the cost of a
  /// lookup: once a bucket is treeified the search through it is O(log n), not linear.
  public static int longestChain(Map<?, ?> map) {
    int longest = 0;
    for (int size : bucketSizes(map)) {
      longest = Math.max(longest, size);
    }
    return longest;
  }

  private static Object[] table(Map<?, ?> map) {
    if (TABLE == null) {
      return null;
    }
    try {
      return (Object[]) TABLE.get(map);
    } catch (IllegalAccessException e) {
      return null;
    }
  }

  /// Walks a bucket. Works for both linked nodes and tree nodes, because `TreeNode` extends
  /// `Node` and keeps the `next` chain intact.
  private static int chainLength(Object bucket) {
    int length = 0;
    Object node = bucket;
    try {
      while (node != null) {
        length++;
        Field next = nextField(node.getClass());
        next.setAccessible(true);
        node = next.get(node);
      }
    } catch (RuntimeException | ReflectiveOperationException e) {
      return length;
    }
    return length;
  }

  private static Field nextField(Class<?> type) throws NoSuchFieldException {
    Class<?> current = type;
    while (current != null) {
      try {
        return current.getDeclaredField("next");
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException("next");
  }
}
