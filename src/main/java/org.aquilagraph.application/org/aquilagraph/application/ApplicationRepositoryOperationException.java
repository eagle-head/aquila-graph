package org.aquilagraph.application;

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.port.RepositoryOperationException;

/** Failure evidence observed at a repository boundary owned by the application. */
public final class ApplicationRepositoryOperationException extends RepositoryOperationException {
  @Serial
  private static final long serialVersionUID = 1L;

  /** Application boundary that observed the failure. */
  private final Boundary failureBoundary;

  ApplicationRepositoryOperationException(RepositoryOperationContext context, Boundary boundary, Optional<CommitKnowledge> knowledge,
      RuntimeException primary) {
    super(context, knowledge, primary);
    failureBoundary = Objects.requireNonNull(boundary, "boundary");
  }

  /**
   * Identifies where the application observed the failure.
   *
   * @return application boundary
   */
  public Boundary boundary() {
    return failureBoundary;
  }

  /** Boundaries observed by the application around a repository port. */
  public enum Boundary {
    /** Ownership was not transferred by the factory. */
    ACQUISITION,
    /** The application rejected a returned value. */
    VALIDATION,
    /** The port operation exited without an admitted observation. */
    OPERATION,
    /** Closure of the transferred repository failed. */
    CLOSURE
  }
}
