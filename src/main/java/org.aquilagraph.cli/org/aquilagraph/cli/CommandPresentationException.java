package org.aquilagraph.cli;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;
import java.util.Optional;

/** Abrupt presentation failure retaining the already established operational outcome. */
public final class CommandPresentationException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;
  private final transient AnalyzeCommandOutcome establishedOutcome;
  private final transient Phase failurePhase;
  private final transient Optional<OutputChannel> failedChannel;
  private final transient Optional<CommandEmission> observedProgress;

  CommandPresentationException(AnalyzeCommandOutcome outcome, Phase phase, Throwable cause) {
    this(outcome, phase, cause, Optional.empty(), Optional.empty());
  }

  CommandPresentationException(AnalyzeCommandOutcome outcome, Phase phase, Throwable cause, Optional<OutputChannel> channel,
      Optional<CommandEmission> progress) {
    super("Command output presentation failed", Objects.requireNonNull(cause, "cause"));
    establishedOutcome = Objects.requireNonNull(outcome, "outcome");
    failurePhase = Objects.requireNonNull(phase, "phase");
    failedChannel = Objects.requireNonNull(channel, "channel");
    observedProgress = Objects.requireNonNull(progress, "progress");
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
   * Returns the actual preparation or destination operation that failed.
   *
   * @return failed presentation phase
   */
  public Phase phase() {
    return failurePhase;
  }

  /**
   * Reports whether destination observation had started.
   *
   * @return whether a destination operation was attempted
   */
  public boolean reportEmissionStarted() {
    return observedProgress.isPresent();
  }

  /**
   * Returns the failed destination, absent for preparation failures.
   * 
   * @return failed channel when emission was attempted
   */
  public Optional<OutputChannel> channel() {
    return failedChannel;
  }

  /**
   * Returns prior observations, the abrupt operation and required unattempted work.
   * 
   * @return emission progress, absent before destination observation
   */
  public Optional<CommandEmission> progress() {
    return observedProgress;
  }

  /** Preparation collaborations and actual destination operations. */
  public enum Phase {
    /** Capturing detached semantic facts. */
    PROJECTION,
    /** Arranging the immutable document. */
    COMPOSITION,
    /** Preparing complete channel payloads. */
    FORMATTING,
    /** Inspecting a borrowed stream before report writing. */
    PREFLIGHT,
    /** Writing the complete prepared text. */
    WRITE,
    /** Flushing a throwing writer after a successful write. */
    FLUSH,
    /** Inspecting and flushing a borrowed stream after report writing. */
    POST_WRITE_INSPECTION
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
    throw new InvalidObjectException("Presentation failure requires an established command outcome");
  }
}
