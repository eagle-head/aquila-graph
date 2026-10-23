package org.aquilagraph.application.port;

import java.io.Serial;
import java.util.Objects;

/** Reports a failure at the technology-neutral repository boundary. */
public final class RepositoryException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  /** Stable description exposed without relying on the nullable {@link #getMessage()} contract. */
  private final String failureDescription;

  /**
   * Creates a repository failure while preserving its implementation-specific cause.
   *
   * @param message
   *          repository operation description
   * @param cause
   *          implementation-specific failure
   */
  public RepositoryException(String message, Exception cause) {
    super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    failureDescription = message;
  }

  /**
   * Returns the non-null repository failure description.
   *
   * @return failure description
   */
  public String description() {
    return failureDescription;
  }
}
