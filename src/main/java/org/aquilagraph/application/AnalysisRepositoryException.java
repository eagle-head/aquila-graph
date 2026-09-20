package org.aquilagraph.application;

import java.io.Serial;
import java.util.Objects;

/** Reports a persistence adapter failure through the application port. */
public final class AnalysisRepositoryException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a repository failure with its adapter-specific cause.
   *
   * @param message
   *          persistence operation description
   * @param cause
   *          adapter failure
   */
  public AnalysisRepositoryException(String message, RuntimeException cause) {
    super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
  }
}
