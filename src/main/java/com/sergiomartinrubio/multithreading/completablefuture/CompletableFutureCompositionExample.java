package com.sergiomartinrubio.multithreading.completablefuture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Composing several async calls: the part that actually shows up in system design rounds.
///
/// Interview questions:
/// - Q: `thenApply()` vs `thenCompose()`?
/// - Q: How do you fan out N calls and wait for all of them?
/// - Q: `exceptionally()` vs `handle()` vs `whenComplete()`?
/// - Q: How do you bound an async call with a timeout?
///
/// Run: java -cp target/classes
///   com.sergiomartinrubio.multithreading.completablefuture.CompletableFutureCompositionExample
public class CompletableFutureCompositionExample {

  record User(long id, String name) { }

  record Order(long id, String item) { }

  static void main() {
    try (ExecutorService io = Executors.newFixedThreadPool(4)) {
      sequential(io);
      parallel(io);
      fanOut(io);
      errorHandling();
      timeouts();
    }
  }

  /// A: thenApply maps T -> U. thenCompose maps T -> CompletableFuture<U> and FLATTENS,
  ///    so use it whenever the next step is itself async. thenApply there would give you
  ///    a CompletableFuture<CompletableFuture<Order>>: the classic mistake.
  ///    (It is exactly map vs flatMap.)
  static void sequential(ExecutorService io) {
    String summary = fetchUser(1, io)
        .thenCompose(user -> fetchOrder(user, io))       // dependent call, needs the user
        .thenApply(order -> "order for item " + order.item())  // pure transform
        .join();
    log("sequential (dependent calls): " + summary);
  }

  /// Two independent calls, started together and joined with thenCombine.
  static void parallel(ExecutorService io) {
    long start = System.nanoTime();

    CompletableFuture<User> user = fetchUser(2, io);
    CompletableFuture<List<String>> permissions = fetchPermissions(2, io);

    String result = user.thenCombine(permissions,
        (u, perms) -> u.name() + " has " + perms).join();

    log("parallel (thenCombine): " + result
        + " in " + (System.nanoTime() - start) / 1_000_000 + " ms (not the sum of both)");

    // thenAcceptBoth consumes both without producing a value;
    // runAfterBoth ignores both values; applyToEither/acceptEither take the first to finish.
  }

  /// A: allOf. Note it returns CompletableFuture<Void>, so you collect the results by
  ///    joining the original futures after it completes (they are all done by then).
  static void fanOut(ExecutorService io) {
    List<CompletableFuture<User>> futures = List.of(
        fetchUser(1, io), fetchUser(2, io), fetchUser(3, io), fetchUser(4, io));

    List<String> names = CompletableFuture
        .allOf(futures.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> futures.stream()
            .map(CompletableFuture::join)
            .map(User::name)
            .toList())
        .join();

    log("fan-out (allOf): " + names);

    // anyOf completes with the first result of any of them (typed as Object, awkwardly).
    Object first = CompletableFuture.anyOf(
        delayed("slow", 200), delayed("fast", 20)).join();
    log("fan-out (anyOf): first to finish was " + first);

    // Caveat: allOf fails fast only in the sense that join() then throws. Every task
    // still runs to completion. Cancelling siblings is on you.
  }

  static void errorHandling() {
    CompletableFuture<String> failing =
        CompletableFuture.supplyAsync(() -> {
          throw new IllegalStateException("downstream unavailable");
        });

    // A: exceptionally - recover: runs ONLY on failure, returns a fallback value.
    log("exceptionally: " + failing
        .exceptionally(error -> "fallback (" + error.getCause().getMessage() + ")")
        .join());

    // A: handle - runs on BOTH paths and returns a value. One of the two args is null.
    log("handle: " + failing
        .handle((value, error) -> error != null ? "recovered" : value)
        .join());

    // A: whenComplete - runs on both paths but CANNOT change the outcome. It is the
    //    async finally block: logging, metrics, closing resources. The exception
    //    still propagates afterwards.
    CompletableFuture<String> observed = failing.whenComplete((value, error) ->
        log("whenComplete: observed error=" + (error != null)));
    log("still failed after whenComplete: " + observed.isCompletedExceptionally());

    // An exception short-circuits the whole chain: intermediate stages are skipped
    // and the error travels to the first handler that can absorb it.
    log("short-circuit: " + CompletableFuture.supplyAsync(() -> 1)
        .thenApply(value -> value / 0)
        .thenApply(value -> "never runs: " + value)
        .exceptionally(error -> "caught " + error.getCause().getClass().getSimpleName())
        .join());
  }

  static void timeouts() {
    // A: orTimeout fails the future with a TimeoutException after the deadline;
    //    completeOnTimeout substitutes a default instead. Both added in Java 9 and both
    //    far better than a blocking get(timeout), which ties up the calling thread.
    try {
      delayed("too-slow", 500).orTimeout(100, TimeUnit.MILLISECONDS).join();
    } catch (RuntimeException e) {
      log("orTimeout -> " + (e.getCause() instanceof TimeoutException ? "TimeoutException" : e));
    }

    log("completeOnTimeout -> " + delayed("too-slow", 500)
        .completeOnTimeout("default", 100, TimeUnit.MILLISECONDS)
        .join());

    // Note: the underlying task is NOT cancelled by either. It keeps running and its
    // result is discarded.
  }

  static CompletableFuture<User> fetchUser(long id, ExecutorService io) {
    return CompletableFuture.supplyAsync(() -> {
      sleep(100);
      return new User(id, "user-" + id);
    }, io);
  }

  static CompletableFuture<Order> fetchOrder(User user, ExecutorService io) {
    return CompletableFuture.supplyAsync(() -> {
      sleep(100);
      return new Order(user.id(), "item-for-" + user.name());
    }, io);
  }

  static CompletableFuture<List<String>> fetchPermissions(long userId, ExecutorService io) {
    return CompletableFuture.supplyAsync(() -> {
      sleep(100);
      return List.of("read", "write");
    }, io);
  }

  static CompletableFuture<String> delayed(String value, long millis) {
    return CompletableFuture.supplyAsync(() -> {
      sleep(millis);
      return value;
    });
  }

  static void sleep(long millis) {
    try {
      TimeUnit.MILLISECONDS.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static void log(String message) {
    System.out.println(message);
  }
}
