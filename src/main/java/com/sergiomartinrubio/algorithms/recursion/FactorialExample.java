package com.sergiomartinrubio.algorithms.recursion;

import java.math.BigInteger;

/// Factorial -- the other recursion warm-up. Two answers again: the recursion, which is the
/// definition, and the loop, which is the one to write.
///
/// Interview questions:
/// - Q: Write factorial recursively, then iteratively.
/// - Q: What is 0! and why?
/// - Q: Which of the two would you ship, and why?
/// - Q: How large can n be before the answer is wrong?
///
/// Run: java -cp target/classes com.sergiomartinrubio.algorithms.recursion.FactorialExample
public class FactorialExample {

  static void main() {
    bothVersions();
    theDifferenceBetweenThem();
    whereItOverflows();

    System.out.println();
    System.out.println("Unlike Fibonacci, the recursion here is not slow -- it is O(n) either");
    System.out.println("way. It is still the wrong answer, because it spends a stack frame per");
    System.out.println("multiplication to buy nothing.");
  }

  // --------------------------------------------------------------------------------------
  // 1. The two versions

  /// The definition, transcribed. `n <= 1` rather than `n == 1`, so 0! terminates instead
  /// of recursing forever into the negatives.
  static long recursive(int n) {
    if (n < 0) {
      throw new IllegalArgumentException("factorial is undefined for " + n);
    }
    if (n <= 1) {
      return 1;                               // 0! = 1! = 1
    }
    return n * recursive(n - 1);
  }

  /// The version to prefer: one frame, no risk of a StackOverflowError.
  static long iterative(int n) {
    if (n < 0) {
      throw new IllegalArgumentException("factorial is undefined for " + n);
    }
    long result = 1;
    for (int i = 2; i <= n; i++) {
      result *= i;
    }
    return result;
  }

  static void bothVersions() {
    System.out.println("--- the two versions, and the base case ---");

    // A: 0! = 1. Not a convention picked for convenience: n! counts the orderings of n
    //    items, and there is exactly one ordering of nothing -- the empty one. It also
    //    keeps n! = n * (n-1)! true at n = 1.
    for (int n = 0; n <= 6; n++) {
      long fromRecursion = recursive(n);
      long fromLoop = iterative(n);
      if (fromRecursion != fromLoop) {
        throw new AssertionError("the two implementations disagree at n = " + n);
      }
      System.out.printf("  %d! = %-5d (recursive) = %-5d (iterative)%n", n, fromRecursion, fromLoop);
    }

    // A: negative n has no factorial, so reject it. A base case of `n == 1` instead of
    //    `n <= 1` turns factorial(0) into infinite recursion -- a classic off-by-one.
    try {
      recursive(-1);
    } catch (IllegalArgumentException e) {
      System.out.println("  recursive(-1) -> " + e.getMessage());
    }
  }

  // --------------------------------------------------------------------------------------
  // 2. Why the loop wins

  static void theDifferenceBetweenThem() {
    System.out.println("\n--- both are O(n); only one of them uses O(n) stack ---");

    // A: n multiplications either way, so there is no complexity argument here -- the
    //    recursion just needs one frame per level, and the JVM eliminates none of them.
    //    `n * recursive(n - 1)` is not even a tail call: the multiply happens after the
    //    call returns, so the frame has to stay alive until it does.
    System.out.println("  n multiplications either way -- the recursion adds n stack frames.");
    System.out.println("  n * recursive(n-1) is not a tail call: the multiply happens after,");
    System.out.println("  and the JVM performs no tail-call elimination in any case.");

    try {
      recursiveOverBigIntegers(50_000, BigInteger.ONE);
    } catch (StackOverflowError e) {
      System.out.println("  50,000! recursively        -> StackOverflowError");
    }
    System.out.printf("  50,000! as a loop          -> %,d digits, one stack frame%n",
        big(50_000).toString().length());
  }

  /// Only here to hit the stack ceiling with a number big enough to be worth computing.
  static BigInteger recursiveOverBigIntegers(int n, BigInteger accumulator) {
    if (n <= 1) {
      return accumulator;
    }
    return recursiveOverBigIntegers(n - 1, accumulator.multiply(BigInteger.valueOf(n)));
  }

  // --------------------------------------------------------------------------------------
  // 3. Overflow

  /// The same loop over `BigInteger`, for when n goes past 20.
  static BigInteger big(int n) {
    BigInteger result = BigInteger.ONE;
    for (int i = 2; i <= n; i++) {
      result = result.multiply(BigInteger.valueOf(i));
    }
    return result;
  }

  static void whereItOverflows() {
    System.out.println("\n--- overflow: 20! is the last one that fits ---");

    // A: 20! = 2,432,902,008,176,640,000 fits in a long. 21! does not, and Java wraps
    //    silently -- no exception, just a wrong number, and here a negative one.
    System.out.printf("  12! = %,d is the last one that fits in an int (max %,d)%n",
        iterative(12), Integer.MAX_VALUE);
    System.out.printf("  20! = %,d%n", iterative(20));
    System.out.printf("  21! = %,d  <-- wrong, and negative%n", iterative(21));
    System.out.printf("  21! = %s  (BigInteger, correct)%n", big(21));

    try {
      long result = 1;
      for (int i = 2; i <= 21; i++) {
        result = Math.multiplyExact(result, i);
      }
    } catch (ArithmeticException e) {
      System.out.println("  Math.multiplyExact turns the wrap into: " + e.getMessage());
    }

    System.out.printf("  100! has %d digits%n", big(100).toString().length());
    System.out.println("  Say the limit before you are asked: 20! for long, 12! for int.");
  }
}
