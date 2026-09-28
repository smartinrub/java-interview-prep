package com.sergiomartinrubio.algorithms.strings;

import java.text.BreakIterator;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// Reverse a string -- trivial until the interviewer types an emoji into your test case.
///
/// Interview questions:
/// - Q: Reverse a string. Now do it without `StringBuilder`.
/// - Q: What is the complexity, and how many copies does it make?
/// - Q: Does it still work for any input? (it does not)
/// - Q: Reverse the words in a sentence instead.
///
/// Run: java -cp target/classes com.sergiomartinrubio.algorithms.strings.ReverseStringExample
public class ReverseStringExample {

  static void main() {
    theThreeAnswers();
    theUnicodeTrap();
    reverseTheWords();

    System.out.println();
    System.out.println("Say `new StringBuilder(s).reverse()` first, then offer the two-pointer");
    System.out.println("loop -- that order shows you know the library before you reimplement it.");
  }

  /// The real-world answer. One allocation, one pass, and it already handles surrogate pairs.
  static String withStringBuilder(String s) {
    return new StringBuilder(s).reverse().toString();
  }

  /// The answer they actually want: swap inwards from both ends. O(n) time, O(n) space for
  /// the array (a Java `String` is immutable, so in-place reversal is not possible).
  static String twoPointer(String s) {
    char[] chars = s.toCharArray();
    int left = 0;
    int right = chars.length - 1;
    while (left < right) {
      char temp = chars[left];
      chars[left++] = chars[right];
      chars[right--] = temp;
    }
    return new String(chars);
  }

  /// The recursive answer, and a good example of an accidentally quadratic algorithm:
  /// `substring` copies since Java 7, so this is O(n^2) time and O(n) stack.
  static String recursive(String s) {
    if (s.length() <= 1) {
      return s;
    }
    return recursive(s.substring(1)) + s.charAt(0);
  }

  static void theThreeAnswers() {
    System.out.println("--- three answers to the same question ---");
    String input = "interview";
    System.out.println("  input           : " + input);
    System.out.println("  StringBuilder   : " + withStringBuilder(input));
    System.out.println("  two-pointer     : " + twoPointer(input));
    System.out.println("  recursive       : " + recursive(input));

    // A: O(n) for the first two. The recursive one is O(n^2) -- each level copies the rest
    //    of the string and concatenates, which is the point of asking for the complexity.
    //    Doubling the input should therefore roughly quadruple the time.
    System.out.println();
    warmUp();
    long previous = 0;
    for (int size : new int[] {2_000, 4_000, 8_000}) {
      String sample = "x".repeat(size);
      long linear = fastest(() -> twoPointer(sample));
      long quadratic = fastest(() -> recursive(sample));

      String growth = previous == 0 ? "" : "%.1fx".formatted((double) quadratic / previous);
      System.out.printf("  %,6d chars: two-pointer %,7d ns, recursive %,9d ns   %s%n",
          size, linear, quadratic, growth);
      previous = quadratic;
    }

    // And the recursion has a second limit the loop does not have: one frame per character.
    try {
      recursive("x".repeat(100_000));
    } catch (StackOverflowError e) {
      System.out.println("  100,000 chars: two-pointer fine, recursive -> StackOverflowError");
    }
    System.out.println("  Both are \"O(n) space\", but only one of them is O(n) time -- and only");
    System.out.println("  one of them survives a long string.");
  }

  /// Give the JIT something to compile before anything is timed. Without this the first
  /// measurement is interpreted bytecode and the numbers come out backwards.
  static void warmUp() {
    String sample = "x".repeat(1_000);
    for (int i = 0; i < 200; i++) {
      twoPointer(sample);
      recursive(sample);
      withStringBuilder(sample);
    }
  }

  /// Keeps the result reachable. Without it the JIT can delete a call whose value is
  /// never used, and the "faster" version times at zero.
  private static int blackhole;

  /// Best of five. A mean would include whatever GC pause happened to land in the window.
  static long fastest(Supplier<String> work) {
    long best = Long.MAX_VALUE;
    for (int i = 0; i < 5; i++) {
      long start = System.nanoTime();
      blackhole += work.get().length();
      best = Math.min(best, System.nanoTime() - start);
    }
    return best;
  }

  static void theUnicodeTrap() {
    System.out.println("\n--- the trap: a char is not a character ---");

    // A: no. A Java char is a 16-bit UTF-16 code unit, and anything outside the Basic
    //    Multilingual Plane -- emoji, some CJK, musical symbols -- is stored as a surrogate
    //    PAIR of chars. Reversing the code units splits the pair and produces garbage.
    String emoji = "ab😀cd";        // "ab<grinning face>cd"
    System.out.println("  input            : " + emoji + "   (" + emoji.length() + " chars, "
        + emoji.codePointCount(0, emoji.length()) + " code points)");
    System.out.println("  two-pointer      : " + twoPointer(emoji) + "   <-- broken pair");
    System.out.println("  StringBuilder    : " + withStringBuilder(emoji)
        + "   <-- reverse() puts surrogate pairs back together");

    // A: StringBuilder.reverse is documented to keep surrogate pairs intact, so it survives
    //    emoji. It still does not survive a grapheme cluster -- a base letter plus combining
    //    marks, or a flag, or a skin-tone modifier -- because those are several code points
    //    that render as one character.
    String combining = "café";          // "cafe" + combining acute = "café"
    System.out.println();
    System.out.println("  input            : " + combining + "   (e + combining acute)");
    System.out.println("  StringBuilder    : " + withStringBuilder(combining)
        + "   <-- the accent moved to the wrong letter");
    System.out.println("  by grapheme      : " + byGrapheme(combining) + "   <-- correct");

    String flag = "🇪🇸!";  // regional indicators E + S = the Spanish flag
    System.out.println();
    System.out.println("  input            : " + flag);
    System.out.println("  StringBuilder    : " + withStringBuilder(flag)
        + "   <-- a different country: ES became SE");
    System.out.println("  by grapheme      : " + byGrapheme(flag));

    System.out.println();
    System.out.println("  Know the three levels: code unit (char), code point (int), grapheme");
    System.out.println("  cluster (what a human calls a character). Almost no interviewer");
    System.out.println("  expects BreakIterator -- they expect you to know the problem exists.");
  }

  /// The only version that reverses what a reader would call characters. `BreakIterator`
  /// walks grapheme clusters, so an accented letter or a flag stays in one piece.
  static String byGrapheme(String s) {
    BreakIterator it = BreakIterator.getCharacterInstance();
    it.setText(s);
    StringBuilder out = new StringBuilder(s.length());
    int end = it.last();
    for (int start = it.previous(); start != BreakIterator.DONE; end = start, start = it.previous()) {
      out.append(s, start, end);
    }
    return out.toString();
  }

  static void reverseTheWords() {
    System.out.println("\n--- follow-up: reverse the words, not the letters ---");
    String sentence = "the quick   brown fox";

    // split("\\s+") leaves an empty first element if the input starts with a space, and
    // collapses the runs of spaces. Both are usually what you want; say so out loud.
    String reversed = Arrays.stream(sentence.trim().split("\\s+"))
        .collect(Collectors.collectingAndThen(Collectors.toList(), words -> {
          java.util.Collections.reverse(words);
          return String.join(" ", words);
        }));

    System.out.println("  input   : \"" + sentence + "\"");
    System.out.println("  reversed: \"" + reversed + "\"");
    System.out.println("  Note it also normalised the run of spaces -- ask whether that is wanted");
    System.out.println("  before you write the code, not after.");
  }
}
