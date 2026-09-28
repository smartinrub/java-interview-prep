package com.sergiomartinrubio.multithreading.parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/// Parallel streams: easy to switch on, easy to get wrong.
///
/// Interview questions:
/// - Q: Which thread pool does a parallel stream use, and why does that matter?
/// - Q: Should you always use `parallelStream()` for speed?
/// - Q: What breaks under parallelism?
/// - Q: How do you run a parallel stream on your own pool?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.parallel.ParallelStreamExample
public class ParallelStreamExample {

  static void main() throws Exception {
    List<Integer> numbers = IntStream.rangeClosed(1, 20).boxed().toList();

    // A: the ForkJoinPool.commonPool(), shared by the whole JVM, sized to
    //    availableProcessors() - 1 (plus the calling thread). That is the trap: one
    //    blocking parallel stream starves every other user of the common pool, including
    //    other libraries' parallel streams and CompletableFuture's default async
    //    execution. Never put blocking I/O in a parallel stream.
    System.out.println("common pool parallelism = " + ForkJoinPool.getCommonPoolParallelism());

    System.out.println("threads used: " + numbers.parallelStream()
        .map(n -> Thread.currentThread().getName())
        .distinct()
        .sorted()
        .collect(Collectors.joining(", ")));

    // A: no. Parallelism costs splitting, task dispatch and merging. It pays off when
    //    (elements x work per element) is large, the source splits evenly
    //    (ArrayList/array yes, LinkedList/Iterator-based no), and the operation is
    //    stateless and side-effect free. For small collections or cheap operations
    //    sequential is faster.
    compareTimings();

    unsafeSideEffects();
    orderingMatters();
    customPool();
  }

  static void compareTimings() {
    int[] data = IntStream.rangeClosed(1, 2_000_000).toArray();

    long start = System.nanoTime();
    long sequential = IntStream.of(data).asLongStream().map(ParallelStreamExample::work).sum();
    long sequentialMillis = (System.nanoTime() - start) / 1_000_000;

    start = System.nanoTime();
    long parallel = IntStream.of(data).parallel().asLongStream()
        .map(ParallelStreamExample::work).sum();
    long parallelMillis = (System.nanoTime() - start) / 1_000_000;

    System.out.printf("sequential %,d in %d ms | parallel %,d in %d ms%n",
        sequential, sequentialMillis, parallel, parallelMillis);
  }

  static long work(long value) {
    return value % 7 + value % 13;
  }

  static void unsafeSideEffects() {
    // A: shared mutable state. This is the single most common parallel-stream bug:
    //    ArrayList is not thread-safe, so the result is a lost element, a null hole,
    //    or an ArrayIndexOutOfBoundsException, non-deterministically.
    List<Integer> unsafe = new ArrayList<>();
    AtomicInteger anomalies = new AtomicInteger();
    for (int attempt = 0; attempt < 50; attempt++) {
      unsafe.clear();
      IntStream.range(0, 10_000).parallel().forEach(unsafe::add);   // BROKEN
      if (unsafe.size() != 10_000 || unsafe.contains(null)) {
        anomalies.incrementAndGet();
      }
    }
    System.out.println("unsafe forEach+add: " + anomalies.get()
        + "/50 runs produced a corrupt list");

    // The fix is to let collect() do the merging: it is designed for parallel reduction.
    List<Integer> safe = IntStream.range(0, 10_000).parallel().boxed().toList();
    System.out.println("collected safely: " + safe.size() + " elements");
  }

  static void orderingMatters() {
    List<Integer> numbers = IntStream.rangeClosed(1, 8).boxed().toList();

    // forEach gives no ordering guarantee under parallelism; forEachOrdered does,
    // at the cost of the parallelism you just asked for.
    System.out.print("forEach        : ");
    numbers.parallelStream().forEach(n -> System.out.print(n + " "));
    System.out.println();

    System.out.print("forEachOrdered : ");
    numbers.parallelStream().forEachOrdered(n -> System.out.print(n + " "));
    System.out.println();

    // reduce() needs an ASSOCIATIVE operator and an identity that really is one.
    // Addition is associative, so splitting the work cannot change the answer:
    System.out.println("sum,   sequential = " + numbers.stream().reduce(0, Integer::sum)
        + " | parallel = " + numbers.parallelStream().reduce(0, Integer::sum));

    // Subtraction is not, so the parallel result depends on how the stream happened to
    // split. Same input, different answer, and no error to tell you about it:
    System.out.println("minus, sequential = " + numbers.stream().reduce(0, (a, b) -> a - b)
        + " | parallel = " + numbers.parallelStream().reduce(0, (a, b) -> a - b)
        + "  <- wrong, and it varies run to run");
  }

  static void customPool() throws Exception {
    // A: submit the terminal operation to your own ForkJoinPool. The stream inherits the
    //    pool of the thread that runs it, which isolates you from the common pool.
    //    Undocumented but widely used; virtual threads are usually the better answer
    //    for anything I/O bound.
    try (ForkJoinPool pool = new ForkJoinPool(2)) {
      long sum = pool.submit(() -> IntStream.rangeClosed(1, 100).parallel().sum()).get();
      System.out.println("on a dedicated pool of 2: sum = " + sum);
    }
  }
}
