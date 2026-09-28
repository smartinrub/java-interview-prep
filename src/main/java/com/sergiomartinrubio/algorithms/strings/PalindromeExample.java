package com.sergiomartinrubio.algorithms.strings;

import java.util.Locale;

/// Palindrome check -- the question is never "can you reverse a string", it is "did you
/// ask what counts as a character".
///
/// Interview questions:
/// - Q: Is this string a palindrome?
/// - Q: Do it without allocating a second string.
/// - Q: Now ignore case, spaces and punctuation.
/// - Q: What about numbers -- is 121 a palindrome without converting it to a string?
///
/// Run: java -cp target/classes com.sergiomartinrubio.algorithms.strings.PalindromeExample
public class PalindromeExample {

  static void main() {
    theObviousAnswer();
    theBetterAnswer();
    ignoringCaseAndPunctuation();
    withoutStrings();

    System.out.println();
    System.out.println("Ask \"does case matter? do spaces?\" before writing anything. Half of");
    System.out.println("what this question tests is whether you clarify or assume.");
  }

  /// Correct, allocates a whole second string, and reads as a one-liner. Fine as a first
  /// answer as long as you say what it costs.
  static boolean byReversing(String s) {
    return s.equals(new StringBuilder(s).reverse().toString());
  }

  /// The version to write: two pointers walking inwards. O(n) time, O(1) extra space, and
  /// it exits on the first mismatch instead of building anything.
  static boolean twoPointer(String s) {
    int left = 0;
    int right = s.length() - 1;
    while (left < right) {
      if (s.charAt(left++) != s.charAt(right--)) {
        return false;
      }
    }
    return true;                              // "" and single characters fall through: both true
  }

  /// The usual real requirement: compare letters and digits only, ignoring case. Still one
  /// pass, still no allocation -- skip what does not count instead of stripping it first.
  static boolean ignoringNoise(String s) {
    int left = 0;
    int right = s.length() - 1;
    while (left < right) {
      while (left < right && !Character.isLetterOrDigit(s.charAt(left))) {
        left++;
      }
      while (left < right && !Character.isLetterOrDigit(s.charAt(right))) {
        right--;
      }
      // Compare in a fixed locale. toLowerCase() with the default locale turns 'I' into a
      // dotless i in Turkish, which is the kind of bug that only appears in production.
      char a = Character.toLowerCase(s.charAt(left++));
      char b = Character.toLowerCase(s.charAt(right--));
      if (a != b) {
        return false;
      }
    }
    return true;
  }

  static void theObviousAnswer() {
    System.out.println("--- the one-liner ---");
    for (String s : new String[] {"racecar", "abba", "abc", "", "a"}) {
      System.out.printf("  %-10s -> %s%n", "\"" + s + "\"", byReversing(s));
    }
    System.out.println("  Correct, but it allocates a copy of the string to answer a question");
    System.out.println("  that needs no copy at all.");
  }

  static void theBetterAnswer() {
    System.out.println("\n--- two pointers: O(n) time, O(1) space ---");

    // A: walk in from both ends and stop at the first mismatch. An odd-length string needs
    //    no special case -- the middle character is compared with itself, or skipped when
    //    left == right ends the loop.
    for (String s : new String[] {"racecar", "abba", "abc", "", "a"}) {
      boolean expected = byReversing(s);
      boolean actual = twoPointer(s);
      if (expected != actual) {
        throw new AssertionError("the two implementations disagree on \"" + s + "\"");
      }
      System.out.printf("  %-10s -> %s%n", "\"" + s + "\"", actual);
    }
    System.out.println("  Empty and single-character strings are palindromes. Say it out loud;");
    System.out.println("  the edge cases are most of the marks on a question this easy.");
  }

  static void ignoringCaseAndPunctuation() {
    System.out.println("\n--- the real question: \"A man, a plan, a canal: Panama\" ---");

    String[] inputs = {
        "A man, a plan, a canal: Panama",
        "No 'x' in Nixon",
        "Was it a car or a cat I saw?",
        "hello world",
    };
    for (String s : inputs) {
      System.out.printf("  %-32s -> %s%n", "\"" + s + "\"", ignoringNoise(s));
    }

    // A: never trust the default locale for case folding. In a Turkish locale
    //    "I".toLowerCase() is a dotless i, so a string that is a palindrome in English
    //    stops being one depending on where the JVM is running.
    String turkish = "Ii";
    System.out.println();
    System.out.printf("  \"%s\".toLowerCase(ENGLISH) = \"%s\"%n", turkish,
        turkish.toLowerCase(Locale.ENGLISH));
    System.out.printf("  \"%s\".toLowerCase(TURKISH) = \"%s\"   <-- different string%n", turkish,
        turkish.toLowerCase(Locale.forLanguageTag("tr")));
    System.out.println("  Always pass a Locale, or use Character.toLowerCase, which is locale-free.");
  }

  static void withoutStrings() {
    System.out.println("\n--- follow-up: a numeric palindrome, no String allocated ---");

    // A: rebuild the number backwards with %10 and /10 and compare. The trap is that the
    //    reversed value can overflow for a large input, so reverse only half the digits
    //    and compare the halves -- the version below does exactly that.
    for (int n : new int[] {121, 1221, 123, 0, 10, -121}) {
      System.out.printf("  %-5d -> %s%n", n, isNumericPalindrome(n));
    }
    System.out.println("  Negative numbers are not palindromes: the minus sign is only on one end.");
    System.out.printf("  Reversing all of %d would overflow an int; halving avoids it.%n",
        Integer.MAX_VALUE);
  }

  /// Reverses only the back half of the number, so the reversed value can never be larger
  /// than the original and there is nothing to overflow.
  static boolean isNumericPalindrome(int n) {
    if (n < 0 || (n % 10 == 0 && n != 0)) {
      return false;                           // a trailing zero cannot match a leading one
    }
    int reversedHalf = 0;
    while (n > reversedHalf) {
      reversedHalf = reversedHalf * 10 + n % 10;
      n /= 10;
    }
    // Even length: the halves are equal. Odd length: drop the middle digit.
    return n == reversedHalf || n == reversedHalf / 10;
  }
}
