package com.sergiomartinrubio.collections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// The `equals`/`hashCode` contract, and what each way of breaking it does to a HashMap.
///
/// Interview questions:
/// - Q: What is the equals/hashCode contract?
/// - Q: What breaks if you override equals but not hashCode?
/// - Q: What happens if you mutate a key after putting it in a map?
/// - Q: Why should map keys be immutable?
///
/// Run: java -cp target/classes
///   com.sergiomartinrubio.collections.EqualsHashCodeContractExample
public class EqualsHashCodeContractExample {

  /// Overrides equals but NOT hashCode -- the single most common version of this bug.
  static final class EqualsOnly {

    private final int id;

    EqualsOnly(int id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof EqualsOnly o && o.id == id;
    }
    // No hashCode(), so Object's identity hash is used: two equal objects get
    // different hashes, and therefore usually land in different buckets.
  }

  /// Correct: equal objects always produce the same hash.
  record Correct(int id) {
  }

  /// Mutable, with a hash derived from the mutable field. Legal Java, broken as a key.
  static final class Mutable {

    private int id;

    Mutable(int id) {
      this.id = id;
    }

    void setId(int id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Mutable o && o.id == id;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }

  static void main() {
    // A: two rules, and only one of them is symmetric:
    //      1. if a.equals(b) then a.hashCode() == b.hashCode()   -- REQUIRED
    //      2. if a.hashCode() == b.hashCode(), a.equals(b) may still be false  -- fine,
    //         that is just a collision
    //    Plus equals itself must be reflexive, symmetric, transitive and consistent.
    //    Breaking rule 1 is what breaks hash-based collections.
    equalsWithoutHashCode();
    correctKey();
    mutatedKey();
    mutableKeyInASet();

    System.out.println();
    // A: because the map files the entry under the hash it had AT INSERTION TIME. It never
    //    re-files anything. Change the key and you are looking in a different bucket from
    //    the one the entry is in, so the entry is unreachable -- but still there, still
    //    counted in size(), still holding memory. A silent leak plus a silent lookup failure.
    System.out.println("Use immutable keys. Records, String, Integer, enums, or a value object");
    System.out.println("with final fields -- never something whose hashCode can change.");
  }

  /// A: lookups fail. The value is in the map, `size()` proves it, and `get` cannot find it,
  ///    because the two equal keys hash to different buckets.
  static void equalsWithoutHashCode() {
    System.out.println("--- equals overridden, hashCode not ---");
    Map<EqualsOnly, String> map = new HashMap<>();
    map.put(new EqualsOnly(1), "stored");

    EqualsOnly lookupKey = new EqualsOnly(1);
    System.out.println("  the two keys are equal : " + new EqualsOnly(1).equals(lookupKey));
    System.out.println("  their hashCodes match  : "
        + (new EqualsOnly(1).hashCode() == lookupKey.hashCode()));
    System.out.println("  map.size()             = " + map.size());
    System.out.println("  map.get(equal key)     = " + map.get(lookupKey) + "   <- lost");
    System.out.println("  map.containsKey(...)   = " + map.containsKey(lookupKey));

    // And because every instance hashes differently, duplicates accumulate.
    map.put(new EqualsOnly(1), "again");
    map.put(new EqualsOnly(1), "and again");
    System.out.println("  after 3 puts of an \"equal\" key, size = " + map.size()
        + "   <- should be 1");
  }

  static void correctKey() {
    System.out.println("\n--- a correct key (record) ---");
    Map<Correct, String> map = new HashMap<>();
    map.put(new Correct(1), "stored");
    map.put(new Correct(1), "replaced");

    System.out.println("  map.size()          = " + map.size() + "   (replaced, not duplicated)");
    System.out.println("  map.get(Correct(1)) = " + map.get(new Correct(1)));
    System.out.println("  records generate equals, hashCode and toString from the components,");
    System.out.println("  which is why they make such good map keys.");
  }

  /// The subtler failure: the key was correct when inserted, then changed.
  static void mutatedKey() {
    System.out.println("\n--- mutating a key after insertion ---");
    Map<Mutable, String> map = new HashMap<>();
    Mutable key = new Mutable(1);
    map.put(key, "stored");

    System.out.println("  before mutation: get(key) = " + map.get(key));

    key.setId(2);   // the hash changes; the map is not told

    System.out.println("  after  mutation: get(key) = " + map.get(key) + "   <- unreachable");
    System.out.println("  map.size()                = " + map.size() + "   <- still there");
    System.out.println("  containsKey(new Mutable(2)) = " + map.containsKey(new Mutable(2)));
    System.out.println("  containsKey(new Mutable(1)) = " + map.containsKey(new Mutable(1)));

    // The entry is only reachable by iteration now, which is how you find these in a heap dump.
    List<String> reachableByIteration = new ArrayList<>();
    map.forEach((k, v) -> reachableByIteration.add(v));
    System.out.println("  reachable by iteration    = " + reachableByIteration
        + "   <- the value is in there, just not findable by key");

    // Restoring the field makes it findable again, which proves nothing was re-filed.
    key.setId(1);
    System.out.println("  after restoring the field : get(key) = " + map.get(key)
        + "   <- the map never moved it");
  }

  /// The same bug in a Set, where it shows up as a duplicate that should be impossible.
  static void mutableKeyInASet() {
    System.out.println("\n--- and in a HashSet ---");
    Set<Mutable> set = new HashSet<>();
    Mutable first = new Mutable(1);
    set.add(first);
    first.setId(2);
    set.add(new Mutable(2));

    System.out.println("  set contains two elements that are equal to each other: "
        + (set.size() == 2));
    List<Integer> ids = new ArrayList<>();
    set.forEach(m -> ids.add(m.id));
    System.out.println("  ids in the set = " + ids + "   <- a Set with a duplicate");
  }
}
