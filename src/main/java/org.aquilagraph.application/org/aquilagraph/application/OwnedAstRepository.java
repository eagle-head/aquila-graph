package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.application.port.AstResultRepository;
import org.aquilagraph.application.port.AstResultRepositoryFactory;
import org.aquilagraph.application.port.RepositoryOperationBoundary;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.api.AstOutcome;
import org.jspecify.annotations.Nullable;

/** Application enclosing ownership, created before acquisition and retained through every work path. */
final class OwnedAstRepository implements AutoCloseable {
  private final RepositoryOperationContext operationContext;
  private @Nullable AstResultRepository transferredRepository;
  private CommitKnowledge replacementKnowledge = CommitKnowledge.NOT_STARTED;

  OwnedAstRepository(RepositoryOperationContext context) {
    operationContext = Objects.requireNonNull(context, "context");
  }

  void acquire(AstResultRepositoryFactory factory) {
    transferredRepository = RepositoryOperationBoundary.observe(operationContext, RepositoryPhase.ACQUISITION,
        () -> CommitKnowledge.NOT_STARTED, () -> Objects.requireNonNull(factory.open(operationContext), "acquired AST repository"),
        primary -> primary);
  }

  void saveComplete(AstOutcome outcome) {
    try {
      repository().saveComplete(outcome);
      replacementKnowledge = CommitKnowledge.KNOWN_COMMIT;
    } catch (RepositoryOperationException exception) {
      requireContext(exception);
      replacementKnowledge = exception.knowledge();
      throw exception;
    }
  }

  AstLookup find(AstIdentity identity) {
    try {
      var result = Objects.requireNonNull(repository().find(identity), "AST lookup");
      if (!identity.equals(result.requestedIdentity())) {
        throw new RepositoryOperationException(operationContext, RepositoryPhase.VALIDATION, CommitKnowledge.NOT_STARTED,
            new IllegalArgumentException("AST repository returned another requested identity"));
      }
      return result;
    } catch (RepositoryOperationException exception) {
      requireContext(exception);
      throw exception;
    }
  }

  @Override
  public void close() {
    if (transferredRepository == null) {
      return;
    }
    RepositoryOperationBoundary.observe(operationContext, RepositoryPhase.REPOSITORY_CLOSE, () -> replacementKnowledge, () -> {
      repository().close();
      return null;
    }, primary -> primary);
  }

  private AstResultRepository repository() {
    return Objects.requireNonNull(transferredRepository, "transferred AST repository");
  }

  private void requireContext(RepositoryOperationException exception) {
    if (!operationContext.equals(exception.context())) {
      throw new IllegalArgumentException("Repository failure belongs to another exact operation", exception);
    }
  }
}
