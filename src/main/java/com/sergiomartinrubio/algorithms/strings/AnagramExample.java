package com.sergiomartinrubio.algorithms.strings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/// Anagram check -- the cheapest way for an interviewer to find out whether you reach for
/// sorting or for counting.
///
/// Interview questions:
/// - Q: Are these two strings anagrams of each other?
/// - Q: You sorted them. Can you do it in O(n)?
/// - Q: Why is `int[26]` a bad idea?
/// - Q: Group a list of words into anagram sets.
///
/// Run: java -cp target/classes com.sergiomartinrubio.algorithms.strings.AnagramExample
public class AnagramExample {

  static void main() {
    bySorting();
    byCounting();
    theAlphabetAssumption();
    groupThem();

    System.out.println();
    System.out.println("Sorting is the answer everyone gives; counting is the answer that gets");
    System.out.println("the follow-up right. Check the lengths first either way -- one line, and");
    System.out.println("it turns the common negative case into O(1).");
  }

  /// The first answer most people give: O(n log n), two array allocations, one line of logic.
  static boolean bySorting(String a, String b) {
    if (a.length() != b.length()) {
      return false;                           // cheap, and it rules out most negatives
    }
    char[] first = a.toCharArray();
    char[] second = b.toCharArray();
    Arrays.sort(first);
    Arrays.sort(second);
    return Arrays.equals(first, second);
  }

  /// The better answer: count up for one string, down for the other, and check the map is
  /// empty. O(n) time, O(k) space in the number of distinct characters.
  static boolean byCounting(String a, String b) {
    if (a.length() != b.length()) {
      return false;
    }
    Map<Integer, Integer> counts = new HashMap<>();
    a.codePoints().forEach(c -> counts.merge(c, 1, Integer::sum));
    for (int c : b.codePoints().toArray()) {
      Integer remaining = counts.merge(c, -1, Integer::sum);
      if (remaining < 0) {
        return false;                         // b has a character a did not have
      }
      if (remaining == 0) {
        counts.remove(c);
      }
    }
    return counts.isEmpty();
  }

  static void bySorting() {
    System.out.println("--- by sorting: O(n log n) ---");
    check(AnagramExample::bySorting);
  }

  static void byCounting() {
    System.out.println("\n--- by counting: O(n) ---");
    check(AnagramExample::byCounting);

    // A: a single pass, one counter per character. Counting down on the second string means
    //    one map instead of two, and lets you bail out the moment a count goes negative.
    System.out.println("  One map, counted up then down. Bails out at the first mismatch.");
  }

  static void check(BiPredicate<String, String> areAnagrams) {
    String[][] pairs = {
        {"listen", "silent"},
        {"anagram", "nagaram"},
        {"rat", "car"},
        {"aab", "abb"},                       // same letters, different counts
        {"", ""},
    };
    for (String[] pair : pairs) {
      boolean sorted = bySorting(pair[0], pair[1]);
      boolean result = areAnagrams.test(pair[0], pair[1]);
      if (sorted != result) {
        throw new AssertionError("implementations disagree on " + Arrays.toString(pair));
      }
      System.out.printf("  %-10s / %-10s -> %s%n", "\"" + pair[0] + "\"", "\"" + pair[1] + "\"",
          result);
    }
  }

  static void theAlphabetAssumption() {
    System.out.println("\n--- the int[26] trap ---");

    // A: int[26] assumes the input is lowercase ASCII letters. It is the fastest possible
    //    version when that holds -- and an ArrayIndexOutOfBoundsException or a silent wrong
    //    answer the moment it does not.
    System.out.println("  \"abc\" / \"cba\" with int[26] -> " + withArray26("abc", "cba"));
    try {
      withArray26("Abc", "cbA");
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("  \"Abc\" / \"cbA\" with int[26] -> " + e);
    }
    System.out.println("  'A' is 65 and 'a' is 97, so an uppercase letter indexes at -32.");

    // Accented and non-Latin text does not fit either, and emoji are two chars each.
    String[] pair = {"resumé", "́emuser"};
    System.out.println();
    System.out.printf("  \"%s\" / \"%s\" by counting -> %s%n", pair[0], pair[1],
        byCounting(pair[0], pair[1]));
    System.out.println("  Use int[26] only after the interviewer confirms the alphabet, and say");
    System.out.println("  that is what you are assuming. Otherwise count code points into a map.");
  }

  /// Fast and fragile: valid only for lowercase a-z.
  static boolean withArray26(String a, String b) {
    if (a.length() != b.length()) {
      return false;
    }
    int[] counts = new int[26];
    for (int i = 0; i < a.length(); i++) {
      counts[a.charAt(i) - 'a']++;
      counts[b.charAt(i) - 'a']--;
    }
    return Arrays.stream(counts).allMatch(count -> count == 0);
  }

  static void groupThem() {
    System.out.println("\n--- follow-up: group a word list into anagram sets ---");

    // A: build a canonical key for each word -- its sorted letters -- and group on it.
    //    O(n * k log k) for n words of length k, and the key is what the question is about:
    //    every anagram of a word produces the same one.
    List<String> words = List.of("eat", "tea", "tan", "ate", "nat", "bat");
    Map<String, List<String>> groups = new HashMap<>();
    for (String word : words) {
      char[] letters = word.toCharArray();
      Arrays.sort(letters);
      groups.computeIfAbsent(new String(letters), key -> new ArrayList<>()).add(word);
    }

    groups.forEach((key, group) -> System.out.printf("  %-5s -> %s%n", key, group));
    System.out.println("  A character-count string (\"a1e1t1\") works as a key too, and is O(k)");
    System.out.println("  instead of O(k log k) -- worth mentioning, rarely worth writing.");
  }
}
