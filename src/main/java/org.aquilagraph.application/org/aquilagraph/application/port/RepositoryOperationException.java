package org.aquilagraph.application.port;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationKind;

/** Exact repository failure observation extended with typed boundary-specific detail. */
public abstract class RepositoryOperationException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  private final transient RepositoryOperationContext operationContext;
  private final transient Optional<CommitKnowledge> replacementKnowledge;

  /**
   * Admits the exact operation, observed replacement knowledge, and effective primary.
   *
   * @param context
   *          exact owned operation
   * @param knowledge
   *          authoritative observation, absent if a save exit supplies none
   * @param effectivePrimary
   *          original effective runtime failure
   */
  protected RepositoryOperationException(RepositoryOperationContext context, Optional<CommitKnowledge> knowledge,
      RuntimeException effectivePrimary) {
    requireObservation(context, knowledge);
    super("Repository operation failed", Objects.requireNonNull(effectivePrimary, "effectivePrimary"));
    operationContext = context;
    replacementKnowledge = knowledge;
  }

  private static void requireObservation(RepositoryOperationContext context, Optional<CommitKnowledge> knowledge) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(knowledge, "knowledge");
    if (context.kind() == RepositoryOperationKind.QUERY_AST && !knowledge.equals(Optional.of(CommitKnowledge.NOT_STARTED))) {
      throw new IllegalArgumentException("AST query failures require an observation of no replacement");
    }
  }

  /**
   * Returns the attempted or requested subject and operation.
   *
   * @return exact operation
   */
  public final RepositoryOperationContext context() {
    return operationContext;
  }

  /**
   * Returns the authoritative replacement knowledge.
   *
   * @return observed knowledge, if available
   */
  public final Optional<CommitKnowledge> knowledge() {
    return replacementKnowledge;
  }

  /**
   * Returns the original effective primary.
   *
   * @return effective runtime failure
   */
  public final RuntimeException effectivePrimary() {
    return getCause();
  }

  /**
   * Returns the admitted runtime cause without allowing adapter detail to replace it.
   *
   * @return exact effective runtime cause
   */
  @Override
  public final synchronized RuntimeException getCause() {
    if (super.getCause() instanceof RuntimeException primary) {
      return primary;
    }
    throw new IllegalStateException("Repository operation requires an effective runtime primary");
  }

  /**
   * Rejects native restoration without the admitted operation observation.
   *
   * @param input
   *          serialization stream
   * @throws InvalidObjectException
   *           always, because repository operation evidence requires an exact admitted subject
   */
  @Serial
  private void readObject(ObjectInputStream input) throws InvalidObjectException {
    Objects.requireNonNull(input, "input");
    throw new InvalidObjectException("Repository operation requires an admitted exact subject");
  }
}
