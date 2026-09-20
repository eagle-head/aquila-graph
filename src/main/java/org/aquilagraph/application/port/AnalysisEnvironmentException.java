package org.aquilagraph.application.port;

import java.io.Serial;
import java.util.Objects;

/** Reports that the active compiler environment cannot complete source analysis. */
public final class AnalysisEnvironmentException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates an environment failure without a lower-level cause.
   *
   * @param message
   *          stable application-facing failure description
   */
  public AnalysisEnvironmentException(String message) {
    super(Objects.requireNonNull(message, "message"));
  }

  /**
   * Creates an environment failure while preserving its cause.
   *
   * @param message
   *          stable application-facing failure description
   * @param cause
   *          compiler or environment failure
   */
  public AnalysisEnvironmentException(String message, Exception cause) {
    super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
  }
}
