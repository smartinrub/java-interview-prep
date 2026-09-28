package com.sergiomartinrubio.multithreading.collections;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/// `CopyOnWriteArrayList`: free reads, expensive writes.
///
/// Interview questions:
/// - Q: How does copy-on-write work, and when is it the right choice?
/// - Q: Why can you remove from it while iterating?
/// - Q: What does its iterator see?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.collections.CopyOnWriteExample
public class CopyOnWriteExample {

  static void main() {
    // A: every mutation copies the entire backing array under a lock and swaps the
    //    reference. Reads take no lock at all and never block. So it wins only when
    //    reads vastly outnumber writes and the list is small: listener/observer
    //    registries, cached config, feature flags. O(n) per write makes it a bad choice
    //    for anything write-heavy.
    List<String> listeners = new CopyOnWriteArrayList<>(List.of("a", "b", "c"));

    // A: the iterator holds a snapshot of the array as it was when created, so
    //    concurrent modification is simply invisible to it. No
    //    ConcurrentModificationException is possible, which is exactly what you want
    //    when firing events into a registry that handlers may mutate.
    Iterator<String> iterator = listeners.iterator();
    listeners.add("d");
    listeners.remove("a");

    System.out.print("snapshot taken before the changes: ");
    while (iterator.hasNext()) {
      System.out.print(iterator.next() + " ");
    }
    System.out.println();
    System.out.println("current list: " + listeners);

    // The same reason means this is legal, where an ArrayList would throw:
    for (String listener : listeners) {
      if ("b".equals(listener)) {
        listeners.remove(listener);
      }
    }
    System.out.println("after removing during iteration: " + listeners);

    // The flip side: the iterator is read-only. remove() on it throws.
    try {
      Iterator<String> readOnly = listeners.iterator();
      readOnly.next();
      readOnly.remove();
    } catch (UnsupportedOperationException e) {
      System.out.println("iterator.remove() is unsupported: it would modify a snapshot");
    }

    // CopyOnWriteArraySet exists too, backed by the same array.
  }
}
