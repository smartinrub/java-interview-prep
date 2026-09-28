package com.sergiomartinrubio.algorithms.arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Differential tests: the hand-written search must agree with `Arrays.binarySearch`, and
/// the optimised two-sum with brute force, on random input.
class ArrayProblemsTest {

  @Test
  @DisplayName("binary search agrees with Arrays.binarySearch on 1,000 random arrays")
  void agreesWithTheJdk() {
    Random random = new Random(11);
    for (int trial = 0; trial < 1_000; trial++) {
      int[] sorted = random.ints(random.nextInt(0, 50), -100, 100).sorted().distinct().toArray();
      for (int k = -105; k <= 105; k += 7) {
        int key = k;
        assertEquals(Arrays.binarySearch(sorted, key), BinarySearchExample.search(sorted, key),
            () -> "key " + key + " in " + Arrays.toString(sorted));
      }
    }
  }

  @Test
  @DisplayName("a missing key returns -(insertion point) - 1")
  void insertionPoint() {
    int[] sorted = {10, 20, 30};

    assertEquals(-1, BinarySearchExample.search(sorted, 5), "insert at 0");
    assertEquals(-2, BinarySearchExample.search(sorted, 15), "insert at 1");
    assertEquals(-4, BinarySearchExample.search(sorted, 35), "insert at 3");

    // The offset exists so that "found at 0" and "insert at 0" are distinguishable.
    assertEquals(0, BinarySearchExample.search(sorted, 10));
  }

  @Test
  @DisplayName("(low + high) / 2 overflows where (low + high) >>> 1 does not")
  void theMidpointOverflow() {
    int low = 1_500_000_000;
    int high = 2_000_000_000;

    assertTrue((low + high) / 2 < 0, "the sum wraps, so the midpoint is negative");
    assertEquals(1_750_000_000, (low + high) >>> 1);
    assertEquals(1_750_000_000, low + (high - low) / 2);
  }

  @Test
  @DisplayName("first and last occurrence bracket a run of duplicates")
  void duplicates() {
    int[] withDuplicates = {1, 2, 2, 2, 2, 3, 4};

    assertEquals(1, BinarySearchExample.firstIndexOf(withDuplicates, 2));
    assertEquals(4, BinarySearchExample.lastIndexOf(withDuplicates, 2));
    assertEquals(0, BinarySearchExample.firstIndexOf(withDuplicates, 1));
    assertEquals(6, BinarySearchExample.lastIndexOf(withDuplicates, 4));
    assertEquals(-1, BinarySearchExample.firstIndexOf(withDuplicates, 99), "absent");
  }

  @Test
  @DisplayName("all three two-sum strategies find the same pair on random arrays")
  void twoSumStrategiesAgree() {
    Random random = new Random(3);
    for (int trial = 0; trial < 500; trial++) {
      int[] numbers = random.ints(random.nextInt(2, 30), -20, 20).toArray();
      int target = random.nextInt(-40, 40);

      int[] expected = TwoSumExample.bruteForce(numbers, target);
      int[] actual = TwoSumExample.onePass(numbers, target);

      if (expected == null) {
        assertNull(actual, () -> Arrays.toString(numbers) + " target " + target);
        int[] justSorted = numbers.clone();
        Arrays.sort(justSorted);
        assertNull(TwoSumExample.twoPointer(justSorted, target),
            "sorting cannot create a pair that does not exist");
        continue;
      }
      // The two may pick different pairs; what matters is that the pair they pick is valid.
      assertEquals(target, numbers[actual[0]] + numbers[actual[1]]);
      assertTrue(actual[0] != actual[1], "an element may not pair with itself");

      int[] sorted = numbers.clone();
      Arrays.sort(sorted);
      int[] fromPointers = TwoSumExample.twoPointer(sorted, target);
      assertEquals(target, sorted[fromPointers[0]] + sorted[fromPointers[1]]);
    }
  }

  @Test
  @DisplayName("an element is never paired with itself, but equal values at two indices are")
  void theSelfPairingTrap() {
    assertNull(TwoSumExample.onePass(new int[] {3}, 6), "3 + 3 needs two threes");
    assertArrayEquals(new int[] {0, 1}, TwoSumExample.onePass(new int[] {3, 3}, 6));
  }
}
