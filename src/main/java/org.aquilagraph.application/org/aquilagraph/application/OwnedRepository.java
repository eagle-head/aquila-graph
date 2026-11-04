package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.internal.repository.RepositoryOperationBoundary;
import org.aquilagraph.application.port.RepositoryOperationException;
import org.jspecify.annotations.Nullable;

/** Tracks what the application can establish across a transferred repository lifetime. */
final class OwnedRepository {
  private final RepositoryOperationContext context;
  private Optional<CommitKnowledge> knowledge = Optional.of(CommitKnowledge.NOT_STARTED);

  OwnedRepository(RepositoryOperationContext context) {
    this.context = context;
  }

  <R, T extends @Nullable Object> T use(Function<RepositoryOperationContext, R> acquire, Function<R, T> work, Consumer<R> close) {
    var repository = observe(Boundary.ACQUISITION, () -> Objects.requireNonNull(acquire.apply(context), "acquired repository"));
    return RepositoryOperationBoundary.withResource(repository, work, value -> observe(Boundary.CLOSURE, () -> {
      close.accept(value);
      return null;
    }));
  }

  void save(Runnable work) {
    knowledge = Optional.empty();
    try {
      observe(Boundary.OPERATION, () -> {
        work.run();
        return null;
      });
      knowledge = Optional.of(CommitKnowledge.KNOWN_COMMIT);
    } catch (RepositoryOperationException failure) {
      knowledge = failure.knowledge();
      throw failure;
    }
  }

  <T extends @Nullable Object> T observe(Boundary boundary, Supplier<T> work) {
    return RepositoryOperationBoundary.observe(context, work,
        primary -> new ApplicationRepositoryOperationException(context, boundary, knowledge, primary));
  }
}
