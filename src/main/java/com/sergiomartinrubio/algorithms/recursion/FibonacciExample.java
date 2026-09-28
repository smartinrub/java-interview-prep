package com.sergiomartinrubio.algorithms.recursion;

import java.math.BigInteger;

/// Fibonacci -- the most common warm-up question there is. Two answers: the recursion,
/// which is the definition, and the loop, which is the one to write.
///
/// Interview questions:
/// - Q: Write Fibonacci recursively. What is its complexity?
/// - Q: Why is the recursive version so slow?
/// - Q: Write it iteratively. What does that cost instead?
/// - Q: What is the largest Fibonacci number a `long` can hold?
///
/// Every number and timing printed below was produced by running this file.
///
/// Run: java -cp target/classes com.sergiomartinrubio.algorithms.recursion.FibonacciExample
public class FibonacciExample {

  /// Counts the calls the recursive version makes, to show the blow-up rather than assert it.
  /// Package-private so `FibonacciTest` can check the count is exactly 2*F(n+1) - 1.
  static long calls;

  static void main() {
    recursionIsExponential();
    theIterativeVersion();
    headToHead();
    whereItOverflows();

    System.out.println();
    System.out.println("Write the loop. Mention the recursion first, so it is clear you know");
    System.out.println("the definition -- then say it is exponential, and write the loop.");
  }

  // --------------------------------------------------------------------------------------
  // 1. The recursion

  /// The definition, transcribed. Correct, and unusable past about n = 40.
  static long recursive(int n) {
    if (n < 0) {
      throw new IllegalArgumentException("n must be >= 0, got " + n);
    }
    calls++;
    if (n < 2) {
      return n;                               // F(0) = 0, F(1) = 1
    }
    return recursive(n - 1) + recursive(n - 2);
  }

  static void recursionIsExponential() {
    System.out.println("--- 1. recursion: O(phi^n), phi = 1.618 ---");

    // A: exponential -- O(phi^n), not O(2^n). The recursion tree is not a full binary tree,
    //    because the right branch is always one level shorter than the left. The exact call
    //    count is 2 * F(n+1) - 1, which grows by a factor of phi per step.
    System.out.println("   n   result        calls   growth per +5");
    long previous = 0;
    for (int n = 10; n <= 35; n += 5) {
      calls = 0;
      long result = recursive(n);
      // phi^5 = 11.09, so the count multiplies by that every five steps.
      String ratio = previous == 0 ? "" : "%.1fx".formatted((double) calls / previous);
      System.out.printf("  %2d   %-10d   %8d   %s%n", n, result, calls, ratio);
      previous = calls;
    }

    // A: because the subproblems overlap. recursive(n-1) and recursive(n-2) both recompute
    //    the whole subtree below n-2, and so on all the way down. F(30) is computed once at
    //    the top and 832,040 times in total by the time the call finishes.
    calls = 0;
    recursive(30);
    System.out.printf("%n  F(30) alone costs %,d calls to compute a number below a million.%n", calls);
    System.out.println("  Overlapping subproblems -- that phrase is what the question is fishing for.");
    System.out.println("  It also uses O(n) stack, because the leftmost path is n frames deep.");
  }

  // --------------------------------------------------------------------------------------
  // 2. The loop

  /// The answer to write. Two variables, one loop, no allocation, no stack growth.
  /// O(n) time, O(1) space -- each value is computed exactly once.
  static long iterative(int n) {
    if (n < 0) {
      throw new IllegalArgumentException("n must be >= 0, got " + n);
    }
    long previous = 0;
    long current = 1;
    for (int i = 0; i < n; i++) {
      long next = previous + current;
      previous = current;
      current = next;
    }
    return previous;                          // after n steps, previous holds F(n)
  }

  static void theIterativeVersion() {
    System.out.println("\n--- 2. iteration: O(n) time, O(1) space ---");

    // A: keep only the last two values. The recursion recomputes the tree below it every
    //    time; the loop walks up the sequence once and never looks back more than two steps.
    System.out.print("  first 15:");
    for (int n = 0; n < 15; n++) {
      System.out.print(" " + iterative(n));
    }
    System.out.println();
    System.out.println("  n additions, three local variables, one stack frame whatever n is.");

    // A: reject negative n rather than returning 0 for it. "What about n < 0?" is the
    //    follow-up on every one of these questions.
    try {
      iterative(-1);
    } catch (IllegalArgumentException e) {
      System.out.println("  iterative(-1) -> " + e.getMessage());
    }
  }

  static void headToHead() {
    System.out.println("\n--- the two, measured ---");

    // Warm up, or the first measurement is interpreted bytecode and the numbers come out
    // in the wrong order.
    for (int i = 0; i < 1_000; i++) {
      recursive(15);
      iterative(15);
    }

    System.out.println("   n   recursive      iterative   same answer");
    for (int n : new int[] {20, 25, 30, 35, 40}) {
      long start = System.nanoTime();
      long fromRecursion = recursive(n);
      long recursiveNanos = System.nanoTime() - start;

      start = System.nanoTime();
      long fromLoop = iterative(n);
      long iterativeNanos = System.nanoTime() - start;

      System.out.printf("  %2d   %,10d us   %,6d ns   %s%n",
          n, recursiveNanos / 1_000, iterativeNanos, fromRecursion == fromLoop);
      if (fromRecursion != fromLoop) {
        throw new AssertionError("the two implementations disagree at n = " + n);
      }
    }
    System.out.println("  Every extra n multiplies the recursion's time by 1.618 and adds one");
    System.out.println("  addition to the loop. F(50) recursively would take minutes.");
  }

  // --------------------------------------------------------------------------------------
  // 3. Overflow

  /// The same loop over `BigInteger`, for when n goes past 92.
  static BigInteger big(int n) {
    BigInteger previous = BigInteger.ZERO;
    BigInteger current = BigInteger.ONE;
    for (int i = 0; i < n; i++) {
      BigInteger next = previous.add(current);
      previous = current;
      current = next;
    }
    return previous;
  }

  static void whereItOverflows() {
    System.out.println("\n--- 3. overflow: where each type gives up ---");

    // A: F(92) = 7,540,113,804,746,346,429 is the last one that fits in a signed long.
    //    F(93) does not overflow loudly -- it wraps and returns a negative number.
    int lastIntSafe = 0;
    for (int n = 0; n <= 92; n++) {
      if (iterative(n) <= Integer.MAX_VALUE) {
        lastIntSafe = n;
      }
    }
    System.out.printf("  int:  last exact F(%d) = %,d%n", lastIntSafe, iterative(lastIntSafe));
    System.out.printf("  long: last exact F(92) = %,d%n", iterative(92));
    System.out.printf("        F(93) silently wraps to %,d  <-- negative%n", iterative(93));

    // Math.addExact turns the silent wrap into an ArithmeticException.
    try {
      long previous = 0;
      long current = 1;
      for (int i = 0; i < 93; i++) {
        long next = Math.addExact(previous, current);
        previous = current;
        current = next;
      }
    } catch (ArithmeticException e) {
      System.out.println("        with Math.addExact: " + e.getMessage());
    }

    System.out.printf("  BigInteger: F(200) = %s%n", big(200));
    System.out.println("  \"What is the largest n you can support?\" is the standard follow-up.");
    System.out.println("  The answer is 92 for long, 46 for int, and unbounded for BigInteger.");
  }
}
