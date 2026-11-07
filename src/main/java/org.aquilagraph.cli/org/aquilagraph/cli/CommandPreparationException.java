package org.aquilagraph.cli;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;

/** Abrupt preparation failure retaining the already established operational outcome. */
public final class CommandPreparationException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;
  private final transient AnalyzeCommandOutcome establishedOutcome;
  private final transient Phase failurePhase;

  CommandPreparationException(AnalyzeCommandOutcome outcome, Phase phase, Throwable cause) {
    super("Command output preparation failed", Objects.requireNonNull(cause, "cause"));
    establishedOutcome = Objects.requireNonNull(outcome, "outcome");
    failurePhase = Objects.requireNonNull(phase, "phase");
  }

  /**
   * Returns the original outcome, including any established publication observation.
   *
   * @return original command outcome
   */
  public AnalyzeCommandOutcome outcome() {
    return establishedOutcome;
  }

  /**
   * Returns the collaboration that failed before report emission.
   *
   * @return failed preparation phase
   */
  public Phase phase() {
    return failurePhase;
  }

  /**
   * Reports that preparation precedes every CLI-owned destination operation.
   *
   * @return false, because report emission has not started
   */
  public boolean reportEmissionStarted() {
    return false;
  }

  /** Preparation collaborations, independent of execution and emission. */
  public enum Phase {
    /** Capturing detached semantic facts. */
    PROJECTION,
    /** Arranging the immutable document. */
    COMPOSITION,
    /** Preparing complete channel payloads. */
    FORMATTING
  }

  /**
   * Rejects restoration without the original operational context.
   *
   * @param input
   *          serialization stream
   * @throws InvalidObjectException
   *           always
   */
  @Serial
  private void readObject(ObjectInputStream input) throws InvalidObjectException {
    Objects.requireNonNull(input, "input");
    throw new InvalidObjectException("Preparation failure requires an established command outcome");
  }
}
