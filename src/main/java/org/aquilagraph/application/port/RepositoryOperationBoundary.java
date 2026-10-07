package org.aquilagraph.application.port;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationException;
import org.aquilagraph.application.RepositoryPhase;

/** Synchronous observation retaining the original unchecked primary and exact creating phase. */
public final class RepositoryOperationBoundary {
  private RepositoryOperationBoundary() {
  }

  /**
   * Executes on the calling thread and observes its terminal result before returning.
   *
   * @param <T>
   *          normal result type
   * @param context
   *          exact operation subject
   * @param phase
   *          observed creating boundary
   * @param knowledge
   *          operation-wide observation at the failure boundary
   * @param action
   *          synchronous owned operation
   * @param translation
   *          narrowly owned effective-primary translation
   * @return normal result without a background operation
   */
  public static <T> T observe(RepositoryOperationContext context, RepositoryPhase phase, Supplier<CommitKnowledge> knowledge,
      Supplier<T> action, UnaryOperator<RuntimeException> translation) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(phase, "phase");
    Objects.requireNonNull(knowledge, "knowledge");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(translation, "translation");
    var observation = new FutureTask<T>(action::get);
    observation.run();
    var completed = completion(observation);
    if (completed.failure().isPresent()) {
      var failure = completed.failure().orElseThrow();
      var primary = failure instanceof ExecutionException observed
          ? Objects.requireNonNull(observed.getCause(), "operation primary")
          : failure;
      throw failed(context, phase, knowledge.get(), translation, primary);
    }
    return completed.result().orElseThrow().value();
  }

  private static <T> Completion<T> completion(FutureTask<T> observation) {
    try {
      return new Completion<>(Optional.of(new Value<>(observation.get())), Optional.empty());
    } catch (ExecutionException observed) {
      return new Completion<>(Optional.empty(), Optional.of(observed));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return new Completion<>(Optional.empty(), Optional.of(interrupted));
    }
  }

  private static RepositoryOperationException failed(RepositoryOperationContext context, RepositoryPhase phase, CommitKnowledge knowledge,
      UnaryOperator<RuntimeException> translation, Throwable primary) {
    if (primary instanceof Error fatal) {
      throw fatal;
    }
    if (primary instanceof RepositoryOperationException declared) {
      if (!context.equals(declared.context())) {
        throw new IllegalArgumentException("Repository failure belongs to another exact operation", declared);
      }
      return declared;
    }
    if (primary instanceof RuntimeException runtime) {
      return new RepositoryOperationException(context, phase, knowledge, translation.apply(runtime));
    }
    return new RepositoryOperationException(context, phase, knowledge,
        new IllegalStateException("Repository supplier produced an undeclared checked failure", primary));
  }

  private record Completion<T>(Optional<Value<T>> result, Optional<Throwable> failure) {
  }

  private record Value<T>(T value) {
  }
}
