package com.sergiomartinrubio.algorithms.numbers;

import java.math.BigInteger;
import java.util.BitSet;
import java.util.Random;

/// Primality and the sieve -- a question about loop bounds disguised as a question about
/// maths.
///
/// Interview questions:
/// - Q: Is n prime?
/// - Q: Why can you stop at the square root?
/// - Q: Now count every prime below five million.
/// - Q: Why does the sieve's inner loop start at i*i?
///
/// Run: java -cp target/classes com.sergiomartinrubio.algorithms.numbers.PrimesExample
public class PrimesExample {

  static void main() {
    trialDivision();
    whyTheSquareRoot();
    theSieve();
    whyItStartsAtISquared();
    bigNumbers();

    System.out.println();
    System.out.println("One number: trial division to the square root. Every prime in a range:");
    System.out.println("the sieve. Giving the sieve for a single number, or trial division for a");
    System.out.println("range, is the mistake this pair of questions is built to catch.");
  }

  /// Trial division, with the two standard optimisations: stop at sqrt(n), and skip every
  /// candidate that is not of the form 6k +- 1. O(sqrt n) divisions.
  static boolean isPrime(long n) {
    if (n < 2) {
      return false;                           // 1 is not prime, and neither is 0 or anything below
    }
    if (n < 4) {
      return true;                            // 2 and 3
    }
    if (n % 2 == 0 || n % 3 == 0) {
      return false;
    }
    // Every prime above 3 is 6k-1 or 6k+1: the other four residues are divisible by 2 or 3.
    // `factor * factor <= n` rather than `factor <= Math.sqrt(n)` -- integer arithmetic, no
    // rounding, and no repeated sqrt call.
    for (long factor = 5; factor * factor <= n; factor += 6) {
      if (n % factor == 0 || n % (factor + 2) == 0) {
        return false;
      }
    }
    return true;
  }

  static void trialDivision() {
    System.out.println("--- is n prime? ---");
    System.out.print("  primes below 50:");
    for (int n = 0; n < 50; n++) {
      if (isPrime(n)) {
        System.out.print(" " + n);
      }
    }
    System.out.println();

    // The cases people get wrong under pressure.
    System.out.println("  isPrime(0) = " + isPrime(0) + ", isPrime(1) = " + isPrime(1)
        + ", isPrime(2) = " + isPrime(2) + "   <-- 2 is the only even prime");
    System.out.println("  isPrime(-7) = " + isPrime(-7) + "   (primes are defined above 1)");
    long mersenne = 2_147_483_647L;           // 2^31 - 1, prime
    System.out.printf("  isPrime(%,d) = %s%n", mersenne, isPrime(mersenne));
  }

  static void whyTheSquareRoot() {
    System.out.println("\n--- why you can stop at the square root ---");

    // A: if n = a * b then one of a and b is <= sqrt(n). So if no factor below sqrt(n)
    //    divides n, no factor above it can either -- its partner would already have been
    //    found. Checking to n instead of sqrt(n) is not wrong, it is just O(n) work for an
    //    O(sqrt n) question.
    int n = 36;
    System.out.print("  36 = ");
    for (int a = 1; a <= n; a++) {
      if (n % a == 0) {
        System.out.print(a + "x" + (n / a) + "  ");
      }
    }
    System.out.printf("%n  sqrt(36) = 6. Every factor pair has one member at or below it.%n");
    System.out.printf("  For n = 1,000,000,007 that is %,d divisions instead of %,d.%n",
        (long) Math.sqrt(1_000_000_007L), 1_000_000_007L);
  }

  /// Sieve of Eratosthenes. A `BitSet` rather than a `boolean[]` because a boolean costs a
  /// byte in an array: 1 MB versus 125 KB for a million candidates.
  static BitSet sieve(int limit) {
    BitSet composite = new BitSet(limit + 1);
    for (int i = 2; (long) i * i <= limit; i++) {
      if (!composite.get(i)) {
        // Start at i*i: every smaller multiple of i has a smaller prime factor and was
        // already crossed out by it. 2*i was handled by 2, 3*i by 3, and so on.
        for (int multiple = i * i; multiple <= limit; multiple += i) {
          composite.set(multiple);
        }
      }
    }
    return composite;
  }

  static void theSieve() {
    System.out.println("\n--- every prime in a range: the sieve ---");

    // A: do not test each number separately. Cross out the multiples of each prime instead;
    //    the total work is n log log n, which is very close to linear.
    int limit = 5_000_000;

    // Warm up, or the first of the two loops is timed as interpreted bytecode.
    sieve(100_000);
    for (int n = 2; n <= 100_000; n++) {
      isPrime(n);
    }

    long start = System.nanoTime();
    BitSet composite = sieve(limit);
    long sieveMillis = (System.nanoTime() - start) / 1_000_000;

    int count = 0;
    for (int n = 2; n <= limit; n++) {
      if (!composite.get(n)) {
        count++;
      }
    }

    start = System.nanoTime();
    int byTrialDivision = 0;
    for (int n = 2; n <= limit; n++) {
      if (isPrime(n)) {
        byTrialDivision++;
      }
    }
    long trialMillis = (System.nanoTime() - start) / 1_000_000;

    if (count != byTrialDivision) {
      throw new AssertionError("the sieve and trial division disagree");
    }
    System.out.printf("  %,d primes below %,d%n", count, limit);
    System.out.printf("  sieve           %,4d ms   O(n log log n)%n", sieveMillis);
    System.out.printf("  trial division  %,4d ms   O(n sqrt n)%n", trialMillis);
    System.out.printf("  memory: a BitSet holds %,d candidates in %,d KB; a boolean[] needs %,d KB%n",
        limit, (limit / 8) / 1024, limit / 1024);
  }

  static void whyItStartsAtISquared() {
    System.out.println("\n--- why the inner loop starts at i*i ---");

    // A: when the outer loop reaches i, every multiple of i below i*i has already been
    //    crossed out, because such a multiple k*i with k < i has the smaller factor k --
    //    and k's own pass already covered it. Starting at 2*i is correct but repeats work.
    System.out.println("  i = 5: 10, 15, 20 were crossed out by 2, 3 and 2. The first multiple");
    System.out.println("         of 5 with no smaller prime factor is 25 = 5*5.");

    int limit = 100_000;
    long fromSquare = countWrites(limit, true);
    long fromDouble = countWrites(limit, false);
    System.out.printf("  writes up to %,d: from i*i = %,d, from 2*i = %,d (%.0f%% more)%n",
        limit, fromSquare, fromDouble, 100.0 * (fromDouble - fromSquare) / fromSquare);
    System.out.println("  Both give the same primes, and the saving is only a few per cent --");
    System.out.println("  this is asked because the reasoning shows you understand the sieve");
    System.out.println("  rather than remember it.");
  }

  /// Counts how many times the sieve writes, to compare the two starting points.
  static long countWrites(int limit, boolean startAtSquare) {
    BitSet composite = new BitSet(limit + 1);
    long writes = 0;
    for (int i = 2; (long) i * i <= limit; i++) {
      if (!composite.get(i)) {
        for (int multiple = startAtSquare ? i * i : 2 * i; multiple <= limit; multiple += i) {
          composite.set(multiple);
          writes++;
        }
      }
    }
    return writes;
  }

  static void bigNumbers() {
    System.out.println("\n--- past a long: BigInteger.isProbablePrime ---");

    // A: for cryptographic sizes, deterministic trial division is hopeless -- sqrt of a
    //    2048-bit number is still 1024 bits. Real code uses Miller-Rabin, which is
    //    probabilistic: `certainty` n means the answer is wrong with probability < 2^-n.
    BigInteger candidate = BigInteger.probablePrime(256, new Random(42));
    System.out.printf("  a 256-bit prime: %s...%n", candidate.toString().substring(0, 40));
    System.out.println("  isProbablePrime(100) = " + candidate.isProbablePrime(100));
    System.out.println("  \"Probable\" means wrong with probability below 2^-100, which is far");
    System.out.println("  below the chance of a cosmic ray flipping the answer bit.");
  }
}
