# java-interview-prep

Runnable notes for technical interview preparation. Every file is a small program you can
execute: the comments hold the interview question, the code proves the answer.

**Topics so far**
1. Java multithreading and concurrency
2. Databases — transactions, locking, JDBC
3. Collections — HashMap internals
4. Algorithms — the classic whiteboard warm-ups

## How to use this

Each example follows the same shape:

```java
/// Topic — one-line summary.
///
/// Interview questions:
/// - Q: ...
///
/// Run: java -cp target/classes com.sergiomartinrubio...
public class SomeExample {

  static void main() {
    // A: the answer, right next to the code that demonstrates it.
  }
}
```

So you can read a file as notes, run it to see the behaviour, and answer out loud from the
`Q:` lines with the file closed.

## Running

```bash
./mvnw compile        # compiles, and copies dependencies to target/deps
./mvnw test           # run the assertions

# multithreading examples need no dependencies
java -cp target/classes \
  com.sergiomartinrubio.multithreading.synchronization.RaceConditionExample

# database examples need H2 on the classpath
java -cp "target/classes:target/deps/*" \
  com.sergiomartinrubio.database.locking.OptimisticLockingExample

# the HashMap examples read JDK internals by reflection, so they need one flag
java --add-opens java.base/java.util=ALL-UNNAMED \
  -cp target/classes com.sergiomartinrubio.collections.HashMapInternalsExample
```

Without the `--add-opens` flag the HashMap examples still run; they just skip the sections
that inspect bucket arrays and print a hint instead.

No build needed for a single file — the source launcher compiles on the fly, and resolves
classes from sibling files itself:

```bash
java src/main/java/com/sergiomartinrubio/multithreading/basics/ThreadExample.java
```

In IntelliJ, just hit the run gutter icon next to any `main()`.

Requires **JDK 25** (the examples use `void main()` without parameters, virtual threads and
Markdown doc comments). The only dependency is **H2**, an in-memory database used by the
`database` examples; the multithreading half has none.

## 1. Multithreading

### Suggested order

Later packages assume the earlier ones, so read top to bottom the first time through.

| # | Package | What it covers |
|---|---------|----------------|
| 1 | [`basics`](src/main/java/com/sergiomartinrubio/multithreading/basics) | `Thread`, `Runnable`, `Callable`, lifecycle, interruption |
| 2 | [`synchronization`](src/main/java/com/sergiomartinrubio/multithreading/synchronization) | Race conditions, `synchronized`, `volatile`, `wait`/`notify`, deadlock |
| 3 | [`locks`](src/main/java/com/sergiomartinrubio/multithreading/locks) | `ReentrantLock`, `ReadWriteLock`, `Condition`, `Semaphore` |
| 4 | [`atomic`](src/main/java/com/sergiomartinrubio/multithreading/atomic) | CAS, `AtomicInteger`, `AtomicReference`, `LongAdder` |
| 5 | [`collections`](src/main/java/com/sergiomartinrubio/multithreading/collections) | Synchronized wrappers, `ConcurrentHashMap`, `BlockingQueue`, copy-on-write |
| 6 | [`executors`](src/main/java/com/sergiomartinrubio/multithreading/executors) | Pools, queueing, rejection policies, scheduling |
| 7 | [`coordination`](src/main/java/com/sergiomartinrubio/multithreading/coordination) | `CountDownLatch`, `CyclicBarrier` |
| 8 | [`completablefuture`](src/main/java/com/sergiomartinrubio/multithreading/completablefuture) | Async composition, error handling, timeouts |
| 9 | [`forkjoin`](src/main/java/com/sergiomartinrubio/multithreading/forkjoin) | Divide and conquer, work stealing |
| 10 | [`parallel`](src/main/java/com/sergiomartinrubio/multithreading/parallel) | Parallel streams and how they go wrong |
| 11 | [`threadlocal`](src/main/java/com/sergiomartinrubio/multithreading/threadlocal) | Per-thread state, pool leaks, `ScopedValue` |
| 12 | [`virtualthreads`](src/main/java/com/sergiomartinrubio/multithreading/virtualthreads) | Virtual threads, when they help and when they don't |
| 13 | [`problems`](src/main/java/com/sergiomartinrubio/multithreading/problems) | Whiteboard classics: producer-consumer, odd/even, dining philosophers |

### The four examples worth running first

```bash
# Watch a race condition lose updates, then watch three fixes prevent it
java -cp target/classes com.sergiomartinrubio.multithreading.synchronization.RaceConditionExample

# See tasks queue, then overflow, then get rejected
java -cp target/classes com.sergiomartinrubio.multithreading.executors.ThreadPoolTuningExample

# 50,000 concurrent blocking tasks, finished in about the time of one
java -cp target/classes com.sergiomartinrubio.multithreading.virtualthreads.VirtualThreadExample

# A parallel stream corrupting an ArrayList, repeatably
java -cp target/classes com.sergiomartinrubio.multithreading.parallel.ParallelStreamExample
```

### Cheat sheet

Answers worth having ready verbatim.

**Pick the right tool**

| Problem | Reach for |
|---|---|
| One counter or flag, many writers | `AtomicInteger` / `AtomicLong` |
| Write-heavy metric, read rarely | `LongAdder` |
| A flag one thread writes, others read | `volatile` |
| Short critical section, a few fields | `synchronized` block on a private lock |
| Need a timeout, `tryLock` or fairness | `ReentrantLock` |
| Many readers, rare expensive writes | `ReadWriteLock`, or just `ConcurrentHashMap` |
| Cap concurrent access to a resource | `Semaphore` |
| Hand work between threads | `BlockingQueue` |
| Wait for N things to finish | `CountDownLatch` |
| Threads meet up every round | `CyclicBarrier` |
| Compose async calls | `CompletableFuture` |
| Lots of concurrent blocking I/O | Virtual threads |
| Recursive divide and conquer | `ForkJoinPool` |

**Traps that come up constantly**

- `count++` is read-modify-write. Never atomic, not even on a `volatile`.
- `volatile` gives visibility and ordering, never atomicity.
- A synchronized collection makes each *call* safe, not each *sequence* of calls.
- `Executors.newFixedThreadPool` has an unbounded queue; `newCachedThreadPool` has
  unbounded threads. Build a `ThreadPoolExecutor` yourself in production.
- `wait()` always goes in a `while` loop. Spurious wakeups are legal.
- `unlock()` always goes in a `finally` block.
- Catching `InterruptedException` clears the flag — restore it with
  `Thread.currentThread().interrupt()`.
- A `ThreadLocal` on a pooled thread leaks, and leaks the *previous request's* data into
  the next one. `remove()` in a `finally`.
- Parallel streams run on the shared common pool. Never block in one.
- Prevent deadlock with a global lock ordering, or `tryLock` with a timeout.

## 2. Databases

Transactions and locking, demonstrated against a real in-memory database (H2) rather than
simulated. Every table and number these examples print was produced by running them.

### Suggested order

| # | Example | What it covers |
|---|---------|----------------|
| 1 | [`LostUpdateExample`](src/main/java/com/sergiomartinrubio/database/locking/LostUpdateExample.java) | The problem both lock strategies exist to solve |
| 2 | [`OptimisticLockingExample`](src/main/java/com/sergiomartinrubio/database/locking/OptimisticLockingExample.java) | Version column, conflict detection, retry loop |
| 3 | [`PessimisticLockingExample`](src/main/java/com/sergiomartinrubio/database/locking/PessimisticLockingExample.java) | `SELECT ... FOR UPDATE`, lock duration, timeouts |
| 4 | [`OptimisticVsPessimisticExample`](src/main/java/com/sergiomartinrubio/database/locking/OptimisticVsPessimisticExample.java) | Head to head at low and high contention |
| 5 | [`LockTypesExample`](src/main/java/com/sergiomartinrubio/database/locking/LockTypesExample.java) | Shared vs exclusive, granularity, MVCC |
| 6 | [`DatabaseDeadlockExample`](src/main/java/com/sergiomartinrubio/database/locking/DatabaseDeadlockExample.java) | Cycle detection, victim rollback, lock ordering |
| 7 | [`IsolationLevelsExample`](src/main/java/com/sergiomartinrubio/database/transactions/IsolationLevelsExample.java) | 4 levels vs 3 anomalies, measured on H2 |
| 8 | [`SqlInjectionExample`](src/main/java/com/sergiomartinrubio/database/jdbc/SqlInjectionExample.java) | Statement attacked 6 ways, then the same input parameterised |
| 9 | [`PreparedStatementExample`](src/main/java/com/sergiomartinrubio/database/jdbc/PreparedStatementExample.java) | Reuse, NULLs, typed setters, `IN` clauses, `LIKE` |
| 10 | [`BatchUpdateExample`](src/main/java/com/sergiomartinrubio/database/jdbc/BatchUpdateExample.java) | `addBatch`/`executeBatch`, autocommit, partial failure |

```bash
# Watch an update vanish with no error at all
java -cp "target/classes:target/deps/*" \
  com.sergiomartinrubio.database.locking.LostUpdateExample

# The same workload under both strategies, with the cost of each
java -cp "target/classes:target/deps/*" \
  com.sergiomartinrubio.database.locking.OptimisticVsPessimisticExample

# A real deadlock, detected and resolved by the database
java -cp "target/classes:target/deps/*" \
  com.sergiomartinrubio.database.locking.DatabaseDeadlockExample

# Dump a table, read another one, and drop a third, all through a search box
java -cp "target/classes:target/deps/*" \
  com.sergiomartinrubio.database.jdbc.SqlInjectionExample
```

### Optimistic vs pessimistic

|  | Optimistic | Pessimistic |
|---|---|---|
| Mechanism | `WHERE version = ?`, check the row count | `SELECT ... FOR UPDATE` |
| Locks held | none | exclusive row lock until commit |
| Conflict is | detected after the fact | prevented up front |
| On conflict | 0 rows updated → re-read and retry | you waited, then proceeded |
| Cost | wasted work when conflicts are common | queueing, held connections |
| Spans requests | yes — the version travels with the data | no — the lock dies with the transaction |
| Deadlock risk | none | yes, fix with consistent lock ordering |
| JPA | `@Version` → `OptimisticLockException` | `LockModeType.PESSIMISTIC_WRITE` |
| Use when | conflicts are rare, or the edit spans requests | conflicts are likely, retry is unsafe |

If the new value is a pure function of the old one, you need neither — one atomic statement
(`UPDATE ... SET balance = balance + 100`) has no window to lose an update in.

### Cheat sheet

**Read anomalies, weakest to strongest guarantee needed**

| Anomaly | You read... | Prevented from |
|---|---|---|
| Dirty read | uncommitted data that may roll back | READ COMMITTED |
| Non-repeatable read | the same row twice, with different values | REPEATABLE READ |
| Phantom read | the same query twice, with different row counts | SERIALIZABLE (per the standard) |

Default isolation: **READ COMMITTED** in Postgres, Oracle, SQL Server and H2;
**REPEATABLE READ** in MySQL/InnoDB.

**Traps**

- A lost update is not an isolation problem. Isolation controls what you *see*; a lost
  update is about what you are allowed to *write*. Even SERIALIZABLE does not stop it when
  the read and the write are in separate transactions — `IsolationLevelsExample` proves this.
- Isolation level *names* are standard, the *behaviour* is not. H2 and Postgres prevent
  phantoms at REPEATABLE READ even though the standard permits them.
- Optimistic locking is only as good as the row-count check. Ignore the `0` and you are
  back to a silent lost update.
- Retry the *read*, not just the write. Replaying a stale value conflicts forever.
- A pessimistic lock is held until COMMIT or ROLLBACK, not until the statement ends. Never
  wait for a user or a remote call while holding one.
- Deadlock and lock timeout are different errors. Branch on `getErrorCode()`/`getSQLState()`,
  never on the message text. Deadlock is worth retrying; a timeout usually means something
  upstream is slow.
- Prevent deadlock with a consistent lock order — but acquire the locks in that order
  *without* letting it change which rows you then modify.
- A missing index widens a lock: an unindexed `WHERE` scans the table and locks far more
  than the one row the statement logically matched.
- Prefer a version integer over a timestamp: clocks have coarse resolution and move
  backwards.
- SQL injection is caused by *concatenation*, not by user input. A `PreparedStatement` sends
  SQL and values separately, so a parameter can never become an operator or a statement —
  it is not escaping.
- Placeholders only work where a *value* is allowed. Table names, column names and
  `ORDER BY` columns need an allow-list instead.
- `WHERE id IN (?)` bound to `"1,2"` matches nothing. Generate one placeholder per value.
- `getInt` returns `0` for SQL NULL — check `wasNull()`. Bind NULLs with
  `setNull(i, Types.X)`.
- For bulk writes, turning autocommit *off* is usually a bigger win than batching; do both.
- `executeBatch()` failure behaviour is driver-dependent, so run batches inside a
  transaction and roll the chunk back.

### Not covered yet

Locking and transactions are done. These are the obvious next database topics:

- [ ] Indexes — B-tree vs hash, composite and covering indexes, why a query ignores one
- [ ] Query plans — reading `EXPLAIN`, the common join strategies
- [ ] Normalisation and when to denormalise
- [ ] ACID in depth, and the write-ahead log
- [ ] Replication, sharding, and the CAP trade-off
- [ ] SQL vs NoSQL, and what each actually buys you

## 3. Collections

### HashMap

| Example | What it covers |
|---|---|
| [`HashMapInternalsExample`](src/main/java/com/sergiomartinrubio/collections/HashMapInternalsExample.java) | Buckets, lazy allocation, resize schedule, bucket indexing, pre-sizing |
| [`HashMapCollisionsExample`](src/main/java/com/sergiomartinrubio/collections/HashMapCollisionsExample.java) | Chaining, treeification thresholds, the cost of a bad `hashCode` |
| [`EqualsHashCodeContractExample`](src/main/java/com/sergiomartinrubio/collections/EqualsHashCodeContractExample.java) | The contract and all three ways of breaking it |
| [`MiniHashMap`](src/main/java/com/sergiomartinrubio/collections/MiniHashMap.java) / [`MiniHashMapExample`](src/main/java/com/sergiomartinrubio/collections/MiniHashMapExample.java) | Build one from scratch — the "now implement it" follow-up |

```bash
# Watch the table grow, and see exactly when a bucket becomes a tree
java --add-opens java.base/java.util=ALL-UNNAMED -cp target/classes \
  com.sergiomartinrubio.collections.HashMapCollisionsExample

# A key that gets lost, a Set that holds duplicates
java -cp target/classes \
  com.sergiomartinrubio.collections.EqualsHashCodeContractExample
```

### Cheat sheet

**The numbers**

| | Value |
|---|---|
| Default capacity | 16, allocated lazily on the first `put` |
| Load factor | 0.75 |
| Growth | capacity doubles when `size > capacity × 0.75` |
| Bucket index | `(capacity - 1) & (h ^ h >>> 16)` |
| `TREEIFY_THRESHOLD` | 8 entries in one bucket |
| `MIN_TREEIFY_CAPACITY` | 64 buckets — **both** conditions are required |
| `UNTREEIFY_THRESHOLD` | 6, on the way back down |

**Complexity**

| Operation | Average | Worst case |
|---|---|---|
| `get` / `put` / `remove` | O(1) | O(log n) since Java 8, O(n) before |
| Resize | O(n), amortised to O(1) per insert | |

**Traps**

- "8 collisions become a tree" is **wrong**. It needs 8 collisions *and* capacity ≥ 64;
  below that the map resizes instead. `HashMapCollisionsExample` prints the step-by-step
  proof.
- A 16-bucket table grows on the **13th** entry, not the 12th — the test is
  `size > threshold`.
- `new HashMap<>(100)` still resizes, because the argument is *capacity* and 100 > 128 × 0.75.
  Use `HashMap.newHashMap(100)` (Java 19+), which takes the expected entry count.
- Override `equals` without `hashCode` and lookups fail while `size()` keeps growing.
- Mutate a key after insertion and the entry becomes unreachable but stays in the map —
  a silent lookup failure and a silent leak. The map never re-files anything.
- A bad `hashCode` is a *performance* bug, not a correctness one, which is why it survives
  review. Measured above at ~1800× slower for 200k lookups.
- `HashMap` is not thread-safe, and a concurrent resize can corrupt it. See
  [`ConcurrentHashMapExample`](src/main/java/com/sergiomartinrubio/multithreading/collections/ConcurrentHashMapExample.java)
  in the multithreading topic for the fix.

### Not covered yet

- [ ] `ArrayList` vs `LinkedList` — growth, and why `LinkedList` is almost never right
- [ ] `TreeMap` / `TreeSet` — red-black trees, `Comparable` vs `Comparator`
- [ ] `LinkedHashMap` — access order, and a 10-line LRU cache
- [ ] `hashCode` distribution and `IdentityHashMap`, `WeakHashMap`, `EnumMap`
- [ ] Fail-fast iterators and `ConcurrentModificationException`

## 4. Algorithms

The questions that open an interview rather than decide it: Fibonacci, factorial, reverse a
string, is it a palindrome, binary search. They are asked because they take two minutes, and
they are failed on the follow-up — the overflow, the edge case, the second complexity
question — not on the algorithm.

So each example gives the answer, then every follow-up that comes after it.

| Example | What it covers |
|---|---|
| [`FibonacciExample`](src/main/java/com/sergiomartinrubio/algorithms/recursion/FibonacciExample.java) | The recursion vs the loop, measured; overlapping subproblems, `int`/`long` overflow |
| [`FactorialExample`](src/main/java/com/sergiomartinrubio/algorithms/recursion/FactorialExample.java) | The recursion vs the loop, why 0! = 1, the stack cost, `int`/`long` overflow |
| [`TowersOfHanoiExample`](src/main/java/com/sergiomartinrubio/algorithms/recursion/TowersOfHanoiExample.java) | Trusting the recursive call, `2^n - 1`, and the same thing with an explicit stack |
| [`ReverseStringExample`](src/main/java/com/sergiomartinrubio/algorithms/strings/ReverseStringExample.java) | Two pointers, accidentally quadratic recursion, surrogate pairs and grapheme clusters |
| [`PalindromeExample`](src/main/java/com/sergiomartinrubio/algorithms/strings/PalindromeExample.java) | Two pointers without allocating, ignoring punctuation, locale-safe case folding |
| [`AnagramExample`](src/main/java/com/sergiomartinrubio/algorithms/strings/AnagramExample.java) | Sorting vs counting, why `int[26]` is a trap, grouping by a canonical key |
| [`BinarySearchExample`](src/main/java/com/sergiomartinrubio/algorithms/arrays/BinarySearchExample.java) | The midpoint overflow, `-(insertion point) - 1`, first/last occurrence |
| [`TwoSumExample`](src/main/java/com/sergiomartinrubio/algorithms/arrays/TwoSumExample.java) | O(n²) → one pass with a map, the sorted two-pointer variant, self-pairing |
| [`PrimesExample`](src/main/java/com/sergiomartinrubio/algorithms/numbers/PrimesExample.java) | Trial division to √n, 6k±1, the sieve, why its inner loop starts at `i*i` |

```bash
# The recursive and iterative F(n), measured against each other
java -cp target/classes com.sergiomartinrubio.algorithms.recursion.FibonacciExample

# Reverse a string, then watch it break on an emoji, an accent and a flag
java -cp target/classes com.sergiomartinrubio.algorithms.strings.ReverseStringExample

# The midpoint overflow that hid in java.util.Arrays for nine years
java -cp target/classes com.sergiomartinrubio.algorithms.arrays.BinarySearchExample
```

### Cheat sheet

**Fibonacci, both ways**

| Approach | Time | Space | Say this about it |
|---|---|---|---|
| Recursion | O(φⁿ) | O(n) stack | The definition; overlapping subproblems, exactly `2·F(n+1) - 1` calls |
| Two variables in a loop | O(n) | O(1) | **The one to write** — each value computed once |

**The numbers worth memorising**

| | Limit |
|---|---|
| Largest Fibonacci in an `int` / `long` | F(46) / F(92) |
| Largest factorial in an `int` / `long` | 12! / 20! |
| Binary search comparisons over 1M items | 20 |
| Hanoi moves for n disks | 2ⁿ - 1 |

**Traps**

- Silent overflow is the point of most of these questions. `int` and `long` wrap without an
  error; `Math.addExact` / `Math.multiplyExact` throw instead.
- `(low + high) / 2` overflows in binary search. Use `(low + high) >>> 1`, or
  `low + (high - low) / 2` when the values may be `long`.
- `Arrays.binarySearch` returns `-(insertion point) - 1` for a missing key, so `-1` means
  "insert at 0", not "absent". Test `< 0`, never `== -1`.
- Binary search on unsorted input returns a wrong answer, not an error. Checking the
  precondition would be O(n) and defeat the point.
- A `char` is a UTF-16 code unit, not a character. A two-pointer reverse splits emoji;
  `StringBuilder.reverse()` keeps surrogate pairs but still breaks combining accents and
  flags. Only `BreakIterator` reverses grapheme clusters.
- `toLowerCase()` uses the default locale, where `"I"` becomes a dotless `ı` in Turkish.
  Pass a `Locale`, or use `Character.toLowerCase`.
- `int[26]` for letter counting throws on an uppercase letter (`'A' - 'a'` is -32) and
  silently ignores everything non-Latin.
- Recursion over a string with `substring` is O(n²): `substring` has copied since Java 7.
- Java has no tail-call elimination, so "make it tail-recursive" fixes nothing. A loop does.
- In two-sum, put into the map *after* the lookup — otherwise an element pairs with itself.
- Trial division stops at `factor * factor <= n`, not `factor <= Math.sqrt(n)`: integer
  arithmetic, no rounding, and no repeated `sqrt`.
- Decide what "no answer" means (`null`, empty, `Optional`, exception) out loud rather than
  picking one silently. Same for 0, negatives and the empty string.

### Not covered yet

- [ ] Sorting — quicksort vs mergesort, stability, what `Arrays.sort` actually uses
- [ ] Linked lists — reversal, cycle detection, the two-pointer family
- [ ] Trees — traversals, BST validation, depth and balance
- [ ] Graphs — BFS/DFS, shortest path, topological sort
- [ ] Sliding window and prefix sums
- [ ] Dynamic programming — memoisation vs bottom-up, knapsack, edit distance, LCS

## Layout

```
src/main/java/com/sergiomartinrubio/
  multithreading/     one package per concurrency topic
  database/           Db.java (H2 setup) + locking/, transactions/, jdbc/
  collections/        HashMap internals, plus a from-scratch implementation
  algorithms/         recursion/, strings/, arrays/, numbers/ -- the whiteboard classics
src/test/java/...     assertions that prove the examples' claims
```

Adding another subject (JVM internals, system design) means a new package under
`com.sergiomartinrubio` and a new section above.

**Deliberately deferred:** Hibernate (`LazyInitializationException`, N+1 selects). It is worth
covering, but `hibernate-core` pulls in 16 jars (~18 MB) and needs entity mappings and a
bootstrap, which would make this the first topic to bring a framework along. The optimistic
locking example already notes how `@Version` maps onto the raw SQL.
