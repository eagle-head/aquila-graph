package org.aquilagraph.application;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;
import org.aquilagraph.application.port.RepositoryOperationException;

/** Abrupt publication failure retaining the computed result and operation evidence. */
public final class AnalysisPublicationException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  private final transient ComputedPublicationSubject computedSubject;

  AnalysisPublicationException(ComputedPublicationSubject subject, RepositoryOperationException failure) {
    super("Analysis publication failed", Objects.requireNonNull(failure, "failure"));
    computedSubject = Objects.requireNonNull(subject, "subject");
    if (!failure.context().equals(PublicationCoordinator.contextFor(subject, failure.context().kind()))) {
      throw new IllegalArgumentException("Publication failure belongs to another computed subject");
    }
  }

  /**
   * Returns the already computed source or coordinated result by identity.
   *
   * @return retained computed subject
   */
  public ComputedPublicationSubject subject() {
    return computedSubject;
  }

  /**
   * Returns the requested operation and subject.
   *
   * @return exact requested context
   */
  public RepositoryOperationContext context() {
    return failure().context();
  }

  /**
   * Returns the operation observation and ordered cleanup evidence.
   *
   * @return retained carrier, possibly with absent replacement knowledge
   */
  public RepositoryOperationException failure() {
    if (super.getCause() instanceof RepositoryOperationException failure) {
      return failure;
    }
    throw new IllegalStateException("Publication failure requires an operation observation");
  }

  /**
   * Returns the effective cause of the abrupt publication.
   *
   * @return exact effective runtime cause
   */
  @Override
  public synchronized RuntimeException getCause() {
    return failure().effectivePrimary();
  }

  /**
   * Rejects native restoration without the admitted computed subject.
   *
   * @param input
   *          serialization stream
   * @throws InvalidObjectException
   *           always, because publication requires an admitted computed subject
   */
  @Serial
  private void readObject(ObjectInputStream input) throws InvalidObjectException {
    Objects.requireNonNull(input, "input");
    throw new InvalidObjectException("Publication failure requires admitted computed results");
  }
}
