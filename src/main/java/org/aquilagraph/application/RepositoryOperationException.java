package org.aquilagraph.application;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;

/** Exact operation, observed failure boundary, and actual effective runtime primary. */
public final class RepositoryOperationException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  private final transient RepositoryOperationContext operationContext;
  /** Observed primary creating boundary. */
  private final RepositoryPhase failurePhase;
  /** Conservative exact-subject replacement observation. */
  private final CommitKnowledge replacementKnowledge;

  /**
   * Preserves the exact effective primary and independently observed operation progress.
   *
   * @param context
   *          single owned operation
   * @param phase
   *          observed creating boundary
   * @param knowledge
   *          conservative subject-replacement observation
   * @param effectivePrimary
   *          original effective runtime failure
   */
  public RepositoryOperationException(RepositoryOperationContext context, RepositoryPhase phase, CommitKnowledge knowledge,
      RuntimeException effectivePrimary) {
    super("AST repository operation failed", Objects.requireNonNull(effectivePrimary, "effectivePrimary"));
    operationContext = Objects.requireNonNull(context, "context");
    failurePhase = Objects.requireNonNull(phase, "phase");
    replacementKnowledge = Objects.requireNonNull(knowledge, "knowledge");
    if (context.kind() == RepositoryOperationKind.QUERY_AST && knowledge != CommitKnowledge.NOT_STARTED) {
      throw new IllegalArgumentException("AST query failures cannot claim subject replacement");
    }
  }

  /**
   * Returns exact attempted or requested subject and operation.
   *
   * @return single owned operation
   */
  public RepositoryOperationContext context() {
    return operationContext;
  }

  /**
   * Returns the observed primary creating boundary.
   *
   * @return primary failure phase
   */
  public RepositoryPhase phase() {
    return failurePhase;
  }

  /**
   * Returns conservative operation-wide subject-replacement knowledge.
   *
   * @return retained replacement observation
   */
  public CommitKnowledge knowledge() {
    return replacementKnowledge;
  }

  /**
   * Returns the exact effective primary without promoting a suppressed cleanup failure.
   *
   * @return original effective runtime primary
   */
  public RuntimeException effectivePrimary() {
    return (RuntimeException) Objects.requireNonNull(getCause(), "effective primary");
  }

  /**
   * Rejects native restoration without an admitted exact subject.
   *
   * @param input
   *          serialization stream
   * @throws InvalidObjectException
   *           always, because domain admission is required
   */
  @Serial
  private void readObject(ObjectInputStream input) throws InvalidObjectException {
    Objects.requireNonNull(input, "input");
    throw new InvalidObjectException("Repository operation requires an admitted exact subject");
  }
}
