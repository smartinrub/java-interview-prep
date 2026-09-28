package com.sergiomartinrubio.algorithms.arrays;

import java.util.Arrays;

/// Binary search -- easy to describe, and famously hard to write correctly. The bug in the
/// JDK's own version survived nine years.
///
/// Interview questions:
/// - Q: Write binary search.
/// - Q: What is wrong with `(low + high) / 2`?
/// - Q: What does `Arrays.binarySearch` return when the key is absent?
/// - Q: The array has duplicates -- find the FIRST index of the key.
///
/// Run: java -cp target/classes com.sergiomartinrubio.algorithms.arrays.BinarySearchExample
public class BinarySearchExample {

  static void main() {
    theSearch();
    theOverflowBug();
    whatTheJdkReturns();
    firstAndLastOccurrence();
    unsortedInput();

    System.out.println();
    System.out.println("Two things to say unprompted: the midpoint overflow, and that the loop");
    System.out.println("condition is `low <= high` with `high = mid - 1`. Every off-by-one in");
    System.out.println("this function lives in those two lines.");
  }

  /// The correct version. `>>> 1` rather than `/ 2` because the sum is unsigned-shifted,
  /// so the midpoint stays right even when `low + high` has already overflowed.
  static int search(int[] sorted, int key) {
    int low = 0;
    int high = sorted.length - 1;
    while (low <= high) {                     // <=, or the last candidate is never examined
      int mid = (low + high) >>> 1;
      if (sorted[mid] < key) {
        low = mid + 1;
      } else if (sorted[mid] > key) {
        high = mid - 1;                       // mid - 1, or an absent key loops forever
      } else {
        return mid;
      }
    }
    return -(low + 1);                        // the JDK convention: -(insertion point) - 1
  }

  static void theSearch() {
    System.out.println("--- the search ---");
    int[] sorted = {2, 5, 8, 12, 16, 23, 38, 56, 72, 91};
    System.out.println("  array: " + Arrays.toString(sorted));
    for (int key : new int[] {2, 23, 91, 4}) {
      int mine = search(sorted, key);
      int jdk = Arrays.binarySearch(sorted, key);
      if (mine != jdk) {
        throw new AssertionError("disagrees with Arrays.binarySearch for " + key);
      }
      System.out.printf("  search(%2d) = %2d   %s%n", key, mine,
          mine >= 0 ? "found at that index" : "absent, insert at " + (-mine - 1));
    }
    System.out.printf("  %,d elements are searched in at most %d comparisons (log2)%n",
        1_000_000, 64 - Long.numberOfLeadingZeros(1_000_000));
  }

  static void theOverflowBug() {
    System.out.println("\n--- the bug: (low + high) / 2 ---");

    // A: for a large array, low + high can exceed Integer.MAX_VALUE and wrap negative, so
    //    the midpoint is negative and the array access throws. It is not theoretical: this
    //    was in java.util.Arrays until Java 6, and in Programming Pearls before that.
    //    >>> 1 fixes it because the shift treats the wrapped sum as unsigned.
    int low = 1_500_000_000;
    int high = 2_000_000_000;
    System.out.printf("  low = %,d, high = %,d%n", low, high);
    System.out.printf("  (low + high) / 2   = %,d   <-- negative index%n", (low + high) / 2);
    System.out.printf("  (low + high) >>> 1 = %,d   <-- correct%n", (low + high) >>> 1);
    System.out.printf("  low + (high - low) / 2 = %,d   <-- also correct, and works for longs%n",
        low + (high - low) / 2);
    System.out.println("  Needs an array of over a billion ints (~4 GB) to fire, which is why");
    System.out.println("  it hid for so long -- and why it is a favourite interview question.");
  }

  static void whatTheJdkReturns() {
    System.out.println("\n--- Arrays.binarySearch on a missing key ---");

    // A: -(insertion point) - 1. The offset is there because 0 would be ambiguous: "found
    //    at index 0" and "insert at index 0" both need a distinct answer.
    int[] sorted = {10, 20, 30};
    for (int key : new int[] {5, 15, 35}) {
      int result = Arrays.binarySearch(sorted, key);
      System.out.printf("  binarySearch(%2d) = %2d  ->  insert at %d%n",
          key, result, -result - 1);
    }
    System.out.println("  So the idiom is: if (i < 0) i = -i - 1; and insert there.");
    System.out.println("  Never test `if (result != -1)` -- -1 means \"insert at 0\", not \"absent\".");
  }

  static void firstAndLastOccurrence() {
    System.out.println("\n--- duplicates: find the first and last index ---");

    // A: plain binary search returns *an* index, not the first. Keep searching left after a
    //    hit instead of returning, and the same loop gives you the lower bound.
    int[] withDuplicates = {1, 2, 2, 2, 2, 3, 4};
    System.out.println("  array: " + Arrays.toString(withDuplicates));
    System.out.println("  binarySearch(2)       = " + Arrays.binarySearch(withDuplicates, 2)
        + "   <-- some index, not the first");
    System.out.println("  firstIndexOf(2)       = " + firstIndexOf(withDuplicates, 2));
    System.out.println("  lastIndexOf(2)        = " + lastIndexOf(withDuplicates, 2));
    System.out.println("  count of 2s           = "
        + (lastIndexOf(withDuplicates, 2) - firstIndexOf(withDuplicates, 2) + 1));
    System.out.println("  Counting occurrences in O(log n) is the point of the follow-up.");
  }

  /// Lower bound: on a hit, record it and keep going left.
  static int firstIndexOf(int[] sorted, int key) {
    int low = 0;
    int high = sorted.length - 1;
    int found = -1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (sorted[mid] < key) {
        low = mid + 1;
      } else {
        if (sorted[mid] == key) {
          found = mid;
        }
        high = mid - 1;
      }
    }
    return found;
  }

  /// Upper bound: the mirror image.
  static int lastIndexOf(int[] sorted, int key) {
    int low = 0;
    int high = sorted.length - 1;
    int found = -1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (sorted[mid] > key) {
        high = mid - 1;
      } else {
        if (sorted[mid] == key) {
          found = mid;
        }
        low = mid + 1;
      }
    }
    return found;
  }

  static void unsortedInput() {
    System.out.println("\n--- unsorted input: no error, just a wrong answer ---");

    // A: binary search has no way to notice. It is O(log n) precisely because it never
    //    looks at most of the array, so "is it sorted?" is a precondition you assert or
    //    document, never something the search checks -- verifying it would be O(n).
    int[] unsorted = {5, 1, 9, 3, 7};
    System.out.println("  array: " + Arrays.toString(unsorted));
    System.out.printf("  1 is at index 1, and binarySearch(1) returns %d -- reported absent%n",
        Arrays.binarySearch(unsorted, 1));
    System.out.printf("  3 is at index 3, and binarySearch(3) returns %d%n",
        Arrays.binarySearch(unsorted, 3));
    System.out.println("  Sorting to enable one search is O(n log n); a linear scan is O(n).");
    System.out.println("  Binary search pays off when the array is searched repeatedly.");
  }
}
