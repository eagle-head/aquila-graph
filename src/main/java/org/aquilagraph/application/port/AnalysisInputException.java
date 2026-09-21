package org.aquilagraph.application.port;

import java.io.Serial;
import java.util.Objects;

/** Reports source input or compiler configuration rejected at the analysis boundary. */
public final class AnalysisInputException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  /** Stable description exposed without relying on the nullable {@link #getMessage()} contract. */
  private final String failureDescription;

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
    failureDescription = message;
  }

  /**
   * Returns the non-null application-facing failure description.
   *
   * @return failure description
   */
  public String description() {
    return failureDescription;
  }
}
