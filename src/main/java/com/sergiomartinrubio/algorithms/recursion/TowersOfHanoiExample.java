package com.sergiomartinrubio.algorithms.recursion;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/// Towers of Hanoi -- the question that checks whether you can trust a recursive call.
///
/// Interview questions:
/// - Q: Move n disks from A to C, one at a time, never a larger disk onto a smaller one.
/// - Q: How many moves does it take, and can you prove it?
/// - Q: Can you do it without recursion?
///
/// Run: java -cp target/classes com.sergiomartinrubio.algorithms.recursion.TowersOfHanoiExample
public class TowersOfHanoiExample {

  static void main() {
    theRecursion();
    theMoveCount();
    iterativeVersion();

    System.out.println();
    System.out.println("The whole trick is refusing to trace the recursion in your head.");
    System.out.println("Assume the n-1 case works, write the three lines, and stop.");
  }

  /// Three lines, and the reason people find it hard is that there is nothing else to say:
  /// move n-1 disks out of the way, move the big one, move the n-1 back on top of it.
  /// Trying to follow the calls by hand is what makes it look impossible.
  static void solve(int disks, char from, char to, char via, List<String> moves) {
    if (disks == 0) {
      return;                                 // nothing to move: the real base case
    }
    solve(disks - 1, from, via, to, moves);   // trust it
    moves.add(disks + ": " + from + " -> " + to);
    solve(disks - 1, via, to, from, moves);   // trust it again
  }

  static void theRecursion() {
    System.out.println("--- 3 disks, every move ---");
    List<String> moves = new ArrayList<>();
    solve(3, 'A', 'C', 'B', moves);
    moves.forEach(move -> System.out.println("  " + move));
    System.out.println("  " + moves.size() + " moves");
  }

  static void theMoveCount() {
    System.out.println("\n--- the move count: T(n) = 2*T(n-1) + 1 = 2^n - 1 ---");

    // A: T(n) = 2^n - 1, straight from the recurrence: the two recursive calls cost T(n-1)
    //    each and the middle line costs 1. It is also provably optimal -- the largest disk
    //    has to move at least once, and every other disk must be off its peg first.
    for (int n = 1; n <= 10; n++) {
      List<String> moves = new ArrayList<>();
      solve(n, 'A', 'C', 'B', moves);
      long predicted = (1L << n) - 1;
      if (moves.size() != predicted) {
        throw new AssertionError("expected " + predicted + " moves, got " + moves.size());
      }
      if (n >= 8) {
        System.out.printf("  n = %2d -> %,6d moves (2^%d - 1)%n", n, moves.size(), n);
      }
    }
    // 2^64 - 1 does not fit in a long either -- (1L << 64) - 1 evaluates to -1, because
    // the shift distance wraps modulo 64 and 1L << 64 is 1L.
    BigInteger legend = BigInteger.TWO.pow(64).subtract(BigInteger.ONE);
    System.out.printf("  n = 64 -> %s moves -- at one a second, ~585 billion years%n", legend);
    System.out.printf("           (1L << 64) - 1 would tell you %d: a shift distance wraps%n",
        (1L << 64) - 1);
    System.out.println("           modulo 64, so 1L << 64 is 1L, not 2^64.");
    System.out.println("  Exponential, and not because the code is bad: the output itself is");
    System.out.println("  exponential in size, so no algorithm can be faster.");
  }

  static void iterativeVersion() {
    System.out.println("\n--- without recursion: an explicit stack ---");

    // A: yes -- every recursion can be rewritten with an explicit stack holding what the
    //    call frames held. It is longer and harder to read, which is the point: the stack
    //    is not magic, and a recursive method is just one the JVM keeps the stack for.
    List<String> recursive = new ArrayList<>();
    solve(4, 'A', 'C', 'B', recursive);
    List<String> iterative = solveWithStack(4, 'A', 'C', 'B');

    System.out.println("  4 disks, recursive : " + recursive.size() + " moves");
    System.out.println("  4 disks, with stack: " + iterative.size() + " moves");
    System.out.println("  identical sequence : " + recursive.equals(iterative));
    if (!recursive.equals(iterative)) {
      throw new AssertionError("the two versions disagree");
    }
  }

  /// A frame, pushed by hand. `expanded` records whether this frame has already pushed its
  /// children -- the equivalent of "where in the method body the program counter was".
  static final class Frame {

    final int disks;
    final char from;
    final char to;
    final char via;
    boolean expanded;

    Frame(int disks, char from, char to, char via) {
      this.disks = disks;
      this.from = from;
      this.to = to;
      this.via = via;
    }
  }

  static List<String> solveWithStack(int disks, char from, char to, char via) {
    List<String> moves = new ArrayList<>();
    Deque<Frame> stack = new ArrayDeque<>();
    stack.push(new Frame(disks, from, to, via));

    while (!stack.isEmpty()) {
      Frame frame = stack.peek();
      if (frame.disks == 0) {
        stack.pop();
      } else if (!frame.expanded) {
        frame.expanded = true;
        // Pushed in reverse, because a stack returns them the other way round.
        stack.push(new Frame(frame.disks - 1, frame.from, frame.via, frame.to));
      } else {
        stack.pop();
        moves.add(frame.disks + ": " + frame.from + " -> " + frame.to);
        stack.push(new Frame(frame.disks - 1, frame.via, frame.to, frame.from));
      }
    }
    return moves;
  }
}
