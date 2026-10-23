package org.aquilagraph.application;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;

/** Full primary and ordered cleanup evidence in an explicit exceptional cause envelope. */
public final class PublicationFailure extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Preserves the original operation carrier as the exact exceptional cause.
   *
   * @param carrier
   *          exact primary carrier and every retained suppressed cleanup
   */
  public PublicationFailure(RepositoryOperationException carrier) {
    super("AST publication failed", Objects.requireNonNull(carrier, "carrier"));
  }

  /**
   * Returns the exact primary operation carrier and its ordered cleanup evidence.
   *
   * @return original operation carrier
   */
  public RepositoryOperationException carrier() {
    return (RepositoryOperationException) Objects.requireNonNull(getCause(), "operation carrier");
  }

  /**
   * Returns the exact effective primary used for outward classification.
   *
   * @return original effective runtime failure
   */
  public RuntimeException effectivePrimary() {
    return carrier().effectivePrimary();
  }

  /**
   * Returns primary failure's creating boundary.
   *
   * @return retained primary phase
   */
  public RepositoryPhase phase() {
    return carrier().phase();
  }

  /**
   * Returns retained operation-wide subject replacement knowledge.
   *
   * @return conservative replacement observation
   */
  public CommitKnowledge knowledge() {
    return carrier().knowledge();
  }

  /**
   * Rejects native restoration outside exact operation admission.
   *
   * @param input
   *          serialization stream
   * @throws InvalidObjectException
   *           always, because exact operation admission is required
   */
  @Serial
  private void readObject(ObjectInputStream input) throws InvalidObjectException {
    Objects.requireNonNull(input, "input");
    throw new InvalidObjectException("Publication failure requires an admitted exact operation");
  }
}
