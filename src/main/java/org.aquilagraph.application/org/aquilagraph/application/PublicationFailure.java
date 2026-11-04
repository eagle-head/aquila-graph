package org.aquilagraph.application;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.application.port.RepositoryOperationException;

/** Declared publication failure with its primary and ordered cleanup evidence. */
public final class PublicationFailure extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Preserves an admitted save failure with authoritative replacement knowledge.
   *
   * @param carrier
   *          original save carrier and its suppressed cleanup
   */
  public PublicationFailure(RepositoryOperationException carrier) {
    super("Publication failed", Objects.requireNonNull(carrier, "carrier"));
    if (carrier.context().kind() == RepositoryOperationKind.QUERY_AST || carrier.knowledge().isEmpty()
        || !(carrier.effectivePrimary() instanceof RepositoryException)) {
      throw new IllegalArgumentException("Publication failure requires a declared save failure and replacement knowledge");
    }
  }

  /**
   * Returns the typed operation detail and ordered cleanup evidence.
   *
   * @return original operation carrier
   */
  public RepositoryOperationException carrier() {
    return (RepositoryOperationException) Objects.requireNonNull(getCause(), "operation carrier");
  }

  /**
   * Returns the declared primary used for classification.
   *
   * @return original effective repository failure
   */
  public RepositoryException effectivePrimary() {
    if (carrier().effectivePrimary() instanceof RepositoryException primary) {
      return primary;
    }
    throw new IllegalStateException("Publication failure requires a declared repository primary");
  }

  /**
   * Returns the operation-wide replacement knowledge.
   *
   * @return authoritative replacement observation
   */
  public CommitKnowledge knowledge() {
    return carrier().knowledge().orElseThrow();
  }

  /**
   * Rejects native restoration without an admitted save failure.
   *
   * @param input
   *          serialization stream
   * @throws InvalidObjectException
   *           always, because declared publication failure requires an admitted save observation
   */
  @Serial
  private void readObject(ObjectInputStream input) throws InvalidObjectException {
    Objects.requireNonNull(input, "input");
    throw new InvalidObjectException("Publication failure requires an admitted exact operation");
  }
}
