package com.sergiomartinrubio.algorithms.strings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/// Pins the string examples, including the Unicode claims -- the ones most likely to be
/// wrong in an answer given from memory.
class StringProblemsTest {

  @ParameterizedTest
  @ValueSource(strings = {"", "a", "ab", "interview", "aaaa", "  spaced  "})
  @DisplayName("all three reversals agree on plain text")
  void reversalsAgree(String input) {
    String expected = new StringBuilder(input).reverse().toString();

    assertEquals(expected, ReverseStringExample.withStringBuilder(input));
    assertEquals(expected, ReverseStringExample.twoPointer(input));
    assertEquals(expected, ReverseStringExample.recursive(input));
  }

  @Test
  @DisplayName("reversing twice is the identity, for 500 random strings")
  void reversingTwiceIsTheIdentity() {
    Random random = new Random(7);
    for (int i = 0; i < 500; i++) {
      String input = random.ints(random.nextInt(0, 40), 'a', 'z' + 1)
          .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
          .toString();
      assertEquals(input, ReverseStringExample.twoPointer(ReverseStringExample.twoPointer(input)));
    }
  }

  @Test
  @DisplayName("a char loop splits a surrogate pair, StringBuilder.reverse does not")
  void surrogatePairs() {
    String emoji = "ab😀cd";        // 6 chars, 5 code points

    assertEquals(6, emoji.length());
    assertEquals(5, emoji.codePointCount(0, emoji.length()));

    String byChar = ReverseStringExample.twoPointer(emoji);
    assertFalse(Character.isSurrogatePair(byChar.charAt(2), byChar.charAt(3)),
        "the two-pointer swap leaves the low surrogate before the high one");

    String byBuilder = ReverseStringExample.withStringBuilder(emoji);
    assertTrue(Character.isSurrogatePair(byBuilder.charAt(2), byBuilder.charAt(3)),
        "StringBuilder.reverse is documented to keep surrogate pairs intact");
    assertEquals("dc😀ba", byBuilder);
  }

  @Test
  @DisplayName("only the grapheme-aware reversal survives a combining accent")
  void combiningMarks() {
    String combining = "café";          // "cafe" + combining acute

    assertNotEquals("́efac", ReverseStringExample.byGrapheme(combining));
    assertEquals("́efac", ReverseStringExample.withStringBuilder(combining),
        "reverse() moves the accent onto the wrong letter");
    assertEquals("éfac", java.text.Normalizer.normalize(
        ReverseStringExample.byGrapheme(combining), java.text.Normalizer.Form.NFC),
        "by grapheme, the accent stays on its own letter");
  }

  @ParameterizedTest
  @CsvSource({
      "racecar, true",
      "abba, true",
      "abc, false",
      "a, true",
      "'', true",
  })
  @DisplayName("both palindrome checks agree")
  void palindromesAgree(String input, boolean expected) {
    assertEquals(expected, PalindromeExample.byReversing(input));
    assertEquals(expected, PalindromeExample.twoPointer(input));
  }

  @Test
  @DisplayName("case and punctuation are ignored when asked for")
  void ignoringNoise() {
    assertTrue(PalindromeExample.ignoringNoise("A man, a plan, a canal: Panama"));
    assertTrue(PalindromeExample.ignoringNoise("No 'x' in Nixon"));
    assertTrue(PalindromeExample.ignoringNoise("Was it a car or a cat I saw?"));
    assertTrue(PalindromeExample.ignoringNoise(",,, ... !!!"), "no letters at all");
    assertFalse(PalindromeExample.ignoringNoise("hello world"));
    assertFalse(PalindromeExample.ignoringNoise("ab!ba!c"));
  }

  @ParameterizedTest
  @CsvSource({"121, true", "1221, true", "123, false", "0, true", "10, false", "-121, false"})
  @DisplayName("the numeric palindrome check needs no String")
  void numericPalindromes(int input, boolean expected) {
    assertEquals(expected, PalindromeExample.isNumericPalindrome(input));
  }

  @Test
  @DisplayName("reversing only half the digits cannot overflow")
  void numericPalindromeDoesNotOverflow() {
    // 2,147,483,647 reversed is 7,463,847,412, which is past Integer.MAX_VALUE. Reversing
    // the back half only never produces a value larger than the input.
    assertFalse(PalindromeExample.isNumericPalindrome(Integer.MAX_VALUE));
    assertTrue(PalindromeExample.isNumericPalindrome(1_000_000_001));
  }

  @ParameterizedTest
  @CsvSource({
      "listen, silent, true",
      "anagram, nagaram, true",
      "rat, car, false",
      "aab, abb, false",
      "abc, ab, false",
  })
  @DisplayName("sorting and counting give the same anagram answer")
  void anagramsAgree(String a, String b, boolean expected) {
    assertEquals(expected, AnagramExample.bySorting(a, b));
    assertEquals(expected, AnagramExample.byCounting(a, b));
    assertEquals(expected, AnagramExample.withArray26(a, b), "all inputs here are a-z");
  }

  @Test
  @DisplayName("int[26] breaks on anything outside lowercase ASCII, counting does not")
  void theAlphabetAssumption() {
    assertTrue(AnagramExample.byCounting("Abc", "cbA"));
    assertThrows(ArrayIndexOutOfBoundsException.class,
        () -> AnagramExample.withArray26("Abc", "cbA"),
        "'A' - 'a' is -32, which is not an index into a 26-element array");
  }

  @Test
  @DisplayName("counting handles code points above the BMP")
  void anagramsOfEmoji() {
    assertTrue(AnagramExample.byCounting("a😀b", "b😀a"));
    assertFalse(AnagramExample.byCounting("a😀b", "ab😁"));
  }
}
