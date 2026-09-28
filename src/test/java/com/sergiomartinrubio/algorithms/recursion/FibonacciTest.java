package com.sergiomartinrubio.algorithms.recursion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// Pins the claims `FibonacciExample` prints: the two implementations agree, the recursion
/// costs exactly 2*F(n+1) - 1 calls, and `long` gives up at F(92).
class FibonacciTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 5, 10, 20, 30})
  @DisplayName("the recursion and the loop return the same F(n)")
  void bothImplementationsAgree(int n) {
    assertEquals(FibonacciExample.iterative(n), FibonacciExample.recursive(n));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 10, 30, 50, 70, 90, 92})
  @DisplayName("the loop matches a BigInteger loop wherever a long can hold the answer")
  void matchesBigInteger(int n) {
    assertEquals(BigInteger.valueOf(FibonacciExample.iterative(n)), FibonacciExample.big(n));
  }

  @Test
  @DisplayName("the base cases are F(0) = 0 and F(1) = 1")
  void baseCases() {
    assertEquals(0, FibonacciExample.iterative(0));
    assertEquals(1, FibonacciExample.iterative(1));
    assertEquals(1, FibonacciExample.iterative(2));
    assertEquals(0, FibonacciExample.recursive(0));
    assertEquals(1, FibonacciExample.recursive(1));
  }

  @Test
  @DisplayName("each definition follows F(n) = F(n-1) + F(n-2)")
  void theRecurrenceHolds() {
    for (int n = 2; n <= 90; n++) {
      assertEquals(FibonacciExample.iterative(n - 1) + FibonacciExample.iterative(n - 2),
          FibonacciExample.iterative(n), "F(" + n + ")");
    }
  }

  @Test
  @DisplayName("negative n is rejected rather than silently returning 0")
  void negativeIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> FibonacciExample.iterative(-1));
    assertThrows(IllegalArgumentException.class, () -> FibonacciExample.recursive(-1));
  }

  @Test
  @DisplayName("the recursion makes exactly 2*F(n+1) - 1 calls")
  void recursiveCallCount() {
    // The call count is itself a Fibonacci number, which is why the runtime is exponential:
    // it grows by a factor of phi for every extra n.
    for (int n = 0; n <= 20; n++) {
      long expected = 2 * FibonacciExample.iterative(n + 1) - 1;
      assertEquals(expected, countRecursiveCalls(n), "call count for n = " + n);
    }
  }

  @Test
  @DisplayName("F(92) is the last one that fits in a long, and F(93) wraps negative")
  void longOverflowsAtNinetyThree() {
    assertEquals(7540113804746346429L, FibonacciExample.iterative(92));
    assertTrue(FibonacciExample.iterative(93) < 0, "F(93) wraps to a negative long");

    // BigInteger keeps going, and disagrees with the long version from 93 on.
    assertEquals(BigInteger.valueOf(FibonacciExample.iterative(92)), FibonacciExample.big(92));
    assertNotEquals(BigInteger.valueOf(FibonacciExample.iterative(93)), FibonacciExample.big(93));
  }

  @Test
  @DisplayName("F(46) is the last one that fits in an int")
  void intOverflowsAtFortySeven() {
    assertTrue(FibonacciExample.iterative(46) <= Integer.MAX_VALUE);
    assertTrue(FibonacciExample.iterative(47) > Integer.MAX_VALUE);
  }

  private long countRecursiveCalls(int n) {
    FibonacciExample.calls = 0;
    FibonacciExample.recursive(n);
    return FibonacciExample.calls;
  }
}
