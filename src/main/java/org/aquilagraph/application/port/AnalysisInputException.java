package org.aquilagraph.application.port;

import java.io.Serial;
import java.util.Objects;

/** Reports source input or compiler configuration rejected at the analysis boundary. */
public final class AnalysisInputException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates an input failure while preserving its cause.
   *
   * @param message
   *          stable application-facing failure description
   * @param cause
   *          input or configuration failure
   */
  public AnalysisInputException(String message, Exception cause) {
    super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
  }
}
