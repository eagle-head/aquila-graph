package org.aquilagraph.cfg.api;

import java.io.Serial;
import java.util.Objects;

/** Declared construction failure whose mode determines normal or abrupt transport. */
public final class CfgConstructionException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  /** Requested transport for this declared failure family. */
  public enum Mode {
    /** Return a failed method outcome and continue the remaining selection. */
    RECOVERABLE,
    /** Abort derivation while preserving previously completed method outcomes. */
    ABRUPT
  }

  /** Declared transport retained with the effective construction cause. */
  private final Mode failureMode;

  /**
   * Preserves an actual effective runtime cause and its declared transport.
   *
   * @param mode
   *          recoverable or abrupt construction failure
   * @param cause
   *          original effective failure
   */
  public CfgConstructionException(Mode mode, RuntimeException cause) {
    super("Control-flow construction failed", Objects.requireNonNull(cause, "cause"));
    failureMode = Objects.requireNonNull(mode, "mode");
  }

  /**
   * Returns declared transport without classifying suppressed cleanup causes.
   *
   * @return declared failure transport
   */
  public Mode mode() {
    return failureMode;
  }
}
