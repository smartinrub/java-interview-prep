package com.sergiomartinrubio.multithreading.forkjoin;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.stream.IntStream;

/// Divide and conquer with `ForkJoinPool` and work stealing.
///
/// Interview questions:
/// - Q: What problem does `ForkJoinPool` solve that a fixed pool does not?
/// - Q: What is work stealing?
/// - Q: Why `fork()` one half and compute the other directly?
/// - Q: How do you pick the sequential threshold?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.forkjoin.ForkJoinExample
public class ForkJoinExample {

  /// Below this, splitting costs more than it saves.
  private static final int THRESHOLD = 10_000;

  static void main() {
    long[] numbers = IntStream.rangeClosed(1, 1_000_000).asLongStream().toArray();

    // A: recursive tasks that spawn subtasks and wait for them. In a fixed pool a
    //    parent blocking on its children can exhaust the pool and deadlock. ForkJoin is
    //    built for it: each worker has its own deque, and a worker blocked in join()
    //    goes off and runs other pending tasks instead of idling.
    //
    // A: work stealing - a worker pushes/pops its own tasks LIFO (good cache locality)
    //    and, when its deque empties, STEALS from the tail of another worker's deque.
    //    Uneven splits self-balance with no central queue to contend on.
    ForkJoinPool pool = ForkJoinPool.commonPool();
    System.out.println("common pool parallelism = " + pool.getParallelism());

    long start = System.nanoTime();
    long forkJoinSum = pool.invoke(new SumTask(numbers, 0, numbers.length));
    long forkJoinMillis = (System.nanoTime() - start) / 1_000_000;

    start = System.nanoTime();
    long sequentialSum = 0;
    for (long number : numbers) {
      sequentialSum += number;
    }
    long sequentialMillis = (System.nanoTime() - start) / 1_000_000;

    System.out.printf("fork/join  = %,d in %d ms%n", forkJoinSum, forkJoinMillis);
    System.out.printf("sequential = %,d in %d ms%n", sequentialSum, sequentialMillis);
    System.out.println("steal count = " + pool.getStealCount());
    System.out.println();
    System.out.println("For a sum this cheap, sequential often wins: the work per element");
    System.out.println("has to be large enough to repay the splitting overhead.");

    // A: the threshold is empirical. Too low and you drown in task objects; too high and
    //    cores sit idle. Rule of thumb: enough elements that a leaf runs for tens of
    //    microseconds, and roughly 10x more leaf tasks than cores so stealing can balance.
    //
    // RecursiveTask<V> returns a value; RecursiveAction returns void.
    // Parallel streams run on this same common pool - see the parallel package.
  }

  static class SumTask extends RecursiveTask<Long> {

    private final long[] numbers;
    private final int from;
    private final int to;

    SumTask(long[] numbers, int from, int to) {
      this.numbers = numbers;
      this.from = from;
      this.to = to;
    }

    @Override
    protected Long compute() {
      int length = to - from;
      if (length <= THRESHOLD) {
        long sum = 0;
        for (int i = from; i < to; i++) {
          sum += numbers[i];
        }
        return sum;
      }

      int middle = from + length / 2;
      SumTask left = new SumTask(numbers, from, middle);
      SumTask right = new SumTask(numbers, middle, to);

      // A: fork() the left half onto this worker's deque, then compute the right half on
      //    the CURRENT thread. Forking both and joining both wastes a thread that would
      //    just sit in join(); this way the caller is always doing useful work.
      //    Order matters: fork, compute, then join.
      left.fork();
      long rightSum = right.compute();
      long leftSum = left.join();

      return leftSum + rightSum;
    }
  }
}
