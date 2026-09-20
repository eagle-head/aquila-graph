package org.aquilagraph.application.port;

import java.io.Serial;
import java.util.Objects;

/** Reports a failure at the technology-neutral repository boundary. */
public final class RepositoryException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

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
  }
}
