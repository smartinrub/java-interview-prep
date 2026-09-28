package com.sergiomartinrubio.algorithms.arrays;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/// Two-sum -- the canonical "can you trade space for time" question. If you have seen one
/// hash-map optimisation in an interview, it was this one.
///
/// Interview questions:
/// - Q: Find two numbers in the array that add up to a target. Return their indices.
/// - Q: Your solution is O(n^2). Do better.
/// - Q: What if the array is already sorted?
/// - Q: Can the same element be used twice?
///
/// Run: java -cp target/classes com.sergiomartinrubio.algorithms.arrays.TwoSumExample
public class TwoSumExample {

  static void main() {
    bruteForce();
    withAHashMap();
    whenSorted();
    theEdgeCases();

    System.out.println();
    System.out.println("The one-pass map is the expected answer, and the reason to know it is");
    System.out.println("the pattern, not the puzzle: \"have I already seen what I need?\" turns a");
    System.out.println("nested loop into a single pass in a dozen different questions.");
  }

  /// Every pair, once. O(n^2) time, O(1) space. Starting the inner loop at `i + 1` is what
  /// stops an element pairing with itself.
  static int[] bruteForce(int[] numbers, int target) {
    for (int i = 0; i < numbers.length; i++) {
      for (int j = i + 1; j < numbers.length; j++) {
        if (numbers[i] + numbers[j] == target) {
          return new int[] {i, j};
        }
      }
    }
    return null;
  }

  /// One pass. For each element, ask whether its complement has already been seen. O(n)
  /// time, O(n) space -- and looking *back* rather than forward is why one pass is enough:
  /// every pair is checked exactly once, at its second element.
  static int[] onePass(int[] numbers, int target) {
    Map<Integer, Integer> seen = new HashMap<>();     // value -> index
    for (int i = 0; i < numbers.length; i++) {
      Integer complement = seen.get(target - numbers[i]);
      if (complement != null) {
        return new int[] {complement, i};
      }
      // Put after the lookup, so numbers[i] can never match itself.
      seen.put(numbers[i], i);
    }
    return null;
  }

  /// If the input is sorted, the map is unnecessary: converge from both ends. O(n) time and
  /// O(1) space, which beats the map. The indices are into the sorted array, though -- if
  /// the caller wanted the original positions, sorting has already destroyed them.
  static int[] twoPointer(int[] sorted, int target) {
    int left = 0;
    int right = sorted.length - 1;
    while (left < right) {
      int sum = sorted[left] + sorted[right];
      if (sum == target) {
        return new int[] {left, right};
      } else if (sum < target) {
        left++;                               // the only way to get a bigger sum
      } else {
        right--;
      }
    }
    return null;
  }

  static void bruteForce() {
    System.out.println("--- brute force: O(n^2) time, O(1) space ---");
    int[] numbers = {2, 7, 11, 15};
    System.out.println("  array " + Arrays.toString(numbers) + ", target 9");
    System.out.println("  indices " + Arrays.toString(bruteForce(numbers, 9)));
    System.out.println("  Correct, and worth writing first if you are stuck -- a working O(n^2)");
    System.out.println("  beats a broken O(n).");
  }

  static void withAHashMap() {
    System.out.println("\n--- one pass with a map: O(n) time, O(n) space ---");
    int[] numbers = {3, 2, 4, 6};
    System.out.println("  array " + Arrays.toString(numbers) + ", target 6");
    System.out.println("  indices " + Arrays.toString(onePass(numbers, 6)));

    // Same answers, on a large random array, plus what the trade actually buys.
    int size = 40_000;
    int[] large = new int[size];
    for (int i = 0; i < size; i++) {
      large[i] = i;
    }
    int target = 2 * size - 3;                // the last two elements: worst case for both

    long start = System.nanoTime();
    int[] slow = bruteForce(large, target);
    long quadratic = System.nanoTime() - start;

    start = System.nanoTime();
    int[] fast = onePass(large, target);
    long linear = System.nanoTime() - start;

    if (!Arrays.equals(slow, fast)) {
      throw new AssertionError("the two versions disagree");
    }
    System.out.printf("%n  %,d elements, answer at the end: O(n^2) %,d us vs O(n) %,d us%n",
        size, quadratic / 1000, linear / 1000);
    System.out.println("  The map is the whole trick: a lookup replaces the inner loop.");
  }

  static void whenSorted() {
    System.out.println("\n--- sorted input: two pointers, O(n) time and O(1) space ---");
    int[] sorted = {1, 3, 4, 5, 7, 11};
    System.out.println("  array " + Arrays.toString(sorted) + ", target 9");
    int[] result = twoPointer(sorted, 9);
    System.out.printf("  indices %s -> values %d + %d%n", Arrays.toString(result),
        sorted[result[0]], sorted[result[1]]);

    // A: only if it arrives sorted. Sorting it yourself costs O(n log n), which is worse
    //    than the map -- and renumbers the indices the question asked for.
    System.out.println("  Better than the map only if the array is ALREADY sorted: sorting it");
    System.out.println("  costs O(n log n) and loses the original indices.");
  }

  static void theEdgeCases() {
    System.out.println("\n--- the edge cases they are actually testing ---");

    // A: no -- and `seen.put` after the lookup is what enforces it. Put before, and
    //    [3] with target 6 would report index 0 twice.
    int[] single = {3};
    System.out.println("  [3], target 6            -> " + Arrays.toString(onePass(single, 6))
        + "   (an element may not pair with itself)");

    // Duplicates are fine, and are a legitimate pair.
    int[] duplicates = {3, 3};
    System.out.println("  [3, 3], target 6         -> " + Arrays.toString(onePass(duplicates, 6))
        + "   (two equal values at different indices are a pair)");
    System.out.println("  Note the map holds one index per value, so [3, 3] would be a problem");
    System.out.println("  if it were built up front -- one more reason the lookup comes first.");

    int[] negatives = {-1, -2, -3, 4};
    System.out.println("  [-1, -2, -3, 4], target 1 -> " + Arrays.toString(onePass(negatives, 1)));

    System.out.println("  no pair                  -> " + Arrays.toString(onePass(new int[] {1, 2}, 100)));
    System.out.println("  Decide with the interviewer what \"no answer\" means: null, an empty");
    System.out.println("  array, an Optional or an exception. Do not just pick one silently.");
  }
}
