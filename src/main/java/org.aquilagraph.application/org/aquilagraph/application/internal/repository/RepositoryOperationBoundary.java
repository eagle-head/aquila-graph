package org.aquilagraph.application.internal.repository;

import java.util.IdentityHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.Future.State;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.port.RepositoryOperationException;
import org.jspecify.annotations.Nullable;

/** Synchronous operation observation and ordered cleanup for repository owners. */
public final class RepositoryOperationBoundary {
  private RepositoryOperationBoundary() {
  }

  /**
   * Executes on the calling thread and admits evidence for the exact operation.
   *
   * @param <T>
   *          normal result type
   * @param context
   *          exact operation subject
   * @param action
   *          synchronous owned operation
   * @param carrier
   *          creating boundary's observation of a runtime failure
   * @return normal result
   */
  public static <T extends @Nullable Object> T observe(RepositoryOperationContext context, Supplier<T> action,
      Function<RuntimeException, RepositoryOperationException> carrier) {
    var completed = runSynchronously(action);
    if (completed.state() == State.FAILED) {
      throw failure(context, carrier, completed.exceptionNow());
    }
    return completed.resultNow();
  }

  private static <T extends @Nullable Object> Future<T> runSynchronously(Supplier<T> action) {
    var observation = new FutureTask<T>(action::get);
    observation.run();
    return observation;
  }

  private static RepositoryOperationException failure(RepositoryOperationContext context,
      Function<RuntimeException, RepositoryOperationException> carrier, Throwable primary) {
    return switch (primary) {
      case RepositoryOperationException reported when context.equals(reported.context()) -> reported;
      case RepositoryOperationException foreign ->
        carrier.apply(new IllegalArgumentException("Repository failure belongs to another exact operation", foreign));
      default -> carrier.apply(unchecked(primary));
    };
  }

  /**
   * Owns the resource through work and mandatory cleanup without replacing a work failure.
   *
   * @param <R>
   *          resource type
   * @param <T>
   *          normal result type
   * @param resource
   *          acquired resource
   * @param action
   *          synchronous work whose runtime failures carry their operation observation
   * @param cleanup
   *          mandatory cleanup
   * @return normal result after successful cleanup
   */
  public static <R, T extends @Nullable Object> T withResource(R resource, Function<R, T> action, Consumer<R> cleanup) {
    var result = transfer(resource, action, cleanup);
    cleanup.accept(resource);
    return result;
  }

  /**
   * Cleans up only when construction fails before ownership transfer.
   *
   * @param <R>
   *          resource type
   * @param <T>
   *          transferred result type
   * @param resource
   *          resource owned until construction succeeds
   * @param action
   *          construction whose runtime failures carry their operation observation
   * @param cleanup
   *          partial resource cleanup
   * @return constructed owner
   */
  public static <R, T extends @Nullable Object> T transfer(R resource, Function<R, T> action, Consumer<R> cleanup) {
    var completed = runSynchronously(() -> action.apply(resource));
    if (completed.state() != State.FAILED) {
      return completed.resultNow();
    }
    var primary = completed.exceptionNow();
    var closure = runSynchronously(() -> {
      cleanup.accept(resource);
      return null;
    });
    if (closure.state() == State.FAILED) {
      var secondary = closure.exceptionNow();
      var observed = new IdentityHashMap<Throwable, Boolean>(1);
      observed.put(primary, Boolean.TRUE);
      if (!observed.containsKey(secondary)) {
        primary.addSuppressed(secondary);
      }
    }
    switch (primary) {
      case RepositoryOperationException observed -> throw observed;
      case Error fatal -> throw fatal;
      default -> throw new IllegalStateException("Owned repository work failed without an operation observation", primary);
    }
  }

  private static RuntimeException unchecked(Throwable primary) {
    return switch (primary) {
      case Error fatal -> throw fatal;
      case RuntimeException runtime -> runtime;
      default -> new IllegalStateException("Repository supplier produced an undeclared checked failure", primary);
    };
  }
}
