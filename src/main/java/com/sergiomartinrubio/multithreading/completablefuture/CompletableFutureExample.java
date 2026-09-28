package com.sergiomartinrubio.multithreading.completablefuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/// The basics: creating, completing and reading a `CompletableFuture`.
///
/// Interview questions:
/// - Q: `CompletableFuture` vs `Future`?
/// - Q: `runAsync()` vs `supplyAsync()`?
/// - Q: Which thread runs a `thenApply()` callback?
/// - Q: `join()` vs `get()`?
///
/// Run: java -cp target/classes com.sergiomartinrubio.multithreading.completablefuture.CompletableFutureExample
public class CompletableFutureExample {

  static void main() throws Exception {
    // A: a plain Future only lets you block on get(), poll isDone(), or cancel. You
    //    cannot chain it, combine two of them, or attach a callback. CompletableFuture
    //    adds composition, completion callbacks, exception recovery and timeouts, and it
    //    can be completed manually from anywhere.

    // A: runAsync takes a Runnable and yields CompletableFuture<Void>;
    //    supplyAsync takes a Supplier and yields a value.
    CompletableFuture<Void> noResult =
        CompletableFuture.runAsync(() -> log("runAsync: no result"));

    CompletableFuture<Integer> withResult =
        CompletableFuture.supplyAsync(() -> {
          log("supplyAsync: computing");
          return 10 + 20;
        });

    noResult.join();
    log("result = " + withResult.join());

    // A: whichever thread is available, and it depends on the variant:
    //    thenApply(fn)            - may run on the completing thread OR the caller,
    //                               whichever gets there first. Do not rely on it.
    //    thenApplyAsync(fn)       - on the default executor (ForkJoinPool.commonPool)
    //    thenApplyAsync(fn, pool) - on the executor you name
    //    The default pool is the shared common pool, so blocking in a callback starves
    //    everyone. Pass your own executor for anything that blocks.
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      CompletableFuture.supplyAsync(() -> "value", executor)
          .thenApplyAsync(value -> {
            log("thenApplyAsync on a named executor: " + value);
            return value.toUpperCase();
          }, executor)
          .thenAccept(value -> log("thenAccept: " + value))
          .join();
    }

    // The three consumption shapes, easy to mix up:
    //   thenApply  : T -> U            (transform)
    //   thenAccept : T -> void         (consume)
    //   thenRun    : ()  -> void       (ignore the value entirely)
    CompletableFuture.supplyAsync(() -> 21)
        .thenApply(value -> value * 2)
        .thenAccept(value -> log("thenApply then thenAccept: " + value))
        .thenRun(() -> log("thenRun: value not visible here"))
        .join();

    // Manual completion: the bridge from a callback-based API into the CF world.
    CompletableFuture<String> manual = new CompletableFuture<>();
    Thread.ofPlatform().start(() -> {
      sleep(100);
      manual.complete("completed by another thread");
    });
    log(manual.get(1, TimeUnit.SECONDS));

    // A: join() throws the unchecked CompletionException, so it works in lambdas and
    //    streams. get() throws checked ExecutionException/InterruptedException. Both
    //    block; both wrap the original cause, which you unwrap with getCause().
    CompletableFuture<String> failed =
        CompletableFuture.failedFuture(new IllegalStateException("nope"));
    try {
      failed.join();
    } catch (RuntimeException e) {
      log("join threw " + e.getClass().getSimpleName() + " caused by " + e.getCause());
    }
  }

  static void sleep(long millis) {
    try {
      TimeUnit.MILLISECONDS.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static void log(String message) {
    System.out.println("[" + Thread.currentThread().getName() + "] " + message);
  }
}
