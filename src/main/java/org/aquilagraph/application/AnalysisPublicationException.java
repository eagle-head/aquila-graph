package org.aquilagraph.application;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;
import java.util.Optional;

/** Abrupt unmatched publication failure retaining the computed subject and full cleanup evidence. */
public final class AnalysisPublicationException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  private final transient ComputedPublicationSubject computedSubject;
  private final transient Optional<PublicationFailure> operationFailure;

  /**
   * Keeps the exact effective primary as the cause and its full carrier separately.
   *
   * @param subject
   *          already computed independent or coordinated subject
   * @param failure
   *          full primary and suppressed operation evidence
   */
  public AnalysisPublicationException(ComputedPublicationSubject subject, PublicationFailure failure) {
    super("Analysis publication failed", Objects.requireNonNull(failure, "failure").effectivePrimary());
    computedSubject = Objects.requireNonNull(subject, "subject");
    operationFailure = Optional.of(failure);
    var identity = subject.ast().result().orElseThrow().identity();
    if (!identity.equals(failure.carrier().context().astIdentity())
        || failure.carrier().context().kind() != RepositoryOperationKind.SAVE_AST) {
      throw new IllegalArgumentException("Publication failure belongs to another computed source subject");
    }
  }

  /**
   * Returns the identical already computed source or coordinated result.
   *
   * @return retained computed subject
   */
  public ComputedPublicationSubject subject() {
    return computedSubject;
  }

  /**
   * Returns the full operation carrier, primary phase, and suppressed cleanup evidence.
   *
   * @return retained operation failure
   */
  public PublicationFailure failure() {
    return operationFailure.orElseThrow();
  }

  /**
   * Rejects native restoration without admitted computed domain owners.
   *
   * @param input
   *          serialization stream
   * @throws InvalidObjectException
   *           always, because domain admission is required
   */
  @Serial
  private void readObject(ObjectInputStream input) throws InvalidObjectException {
    Objects.requireNonNull(input, "input");
    throw new InvalidObjectException("Publication failure requires admitted computed results");
  }
}
