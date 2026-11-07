package org.aquilagraph.cli;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;
import org.aquilagraph.cli.CommandPresentationException.Phase;

/** Operational destination observations; completion does not establish external receipt. */
public sealed interface ChannelEmission {
  /** States with no observed failure. */
  enum Status implements ChannelEmission {
    /** No payload was required and no destination operation was performed. */
    UNUSED,
    /** Required work was prevented by an abrupt interruption. */
    UNATTEMPTED,
    /** Required adapter operations completed without observed failure. */
    COMPLETED
  }

  /** A writer operation retaining its exact original checked exception. */
  final class IoFailure extends Failure {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Retains the observed operation and the original checked failure.
     * 
     * @param phase
     *          operation that threw
     * @param cause
     *          original exception, with no delivered-byte guarantee
     */
    public IoFailure(Phase phase, IOException cause) {
      super(phase, cause);
    }

    /**
     * Returns the original mutable I/O failure through its cause relationship.
     * 
     * @return exact original checked exception
     */
    public IOException cause() {
      return original(IOException.class);
    }
  }

  /**
   * A stream flag was observed, without a recoverable original I/O cause.
   * 
   * @param phase
   *          preflight or post-write inspection that observed the flag
   */
  record StreamError(Phase phase) implements ChannelEmission {
    /** Requires the observation phase. */
    public StreamError {
      Objects.requireNonNull(phase, "phase");
    }
  }

  /** An abrupt nonfatal destination failure retained in interrupted progress. */
  final class AbruptFailure extends Failure {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Retains the observed operation and its original abrupt failure.
     * 
     * @param phase
     *          operation that threw
     * @param cause
     *          exact original failure
     */
    public AbruptFailure(Phase phase, Throwable cause) {
      super(phase, cause);
    }

    /**
     * Returns the original mutable failure through its cause relationship.
     * 
     * @return exact original abrupt failure
     */
    public Throwable cause() {
      return original(Throwable.class);
    }
  }

  /** Mutable operational failure relationship, separate from immutable report facts. */
  abstract sealed class Failure extends RuntimeException implements ChannelEmission permits IoFailure, AbruptFailure {
    @Serial
    private static final long serialVersionUID = 1L;
    private final transient Phase observedPhase;

    /**
     * Requires the operation and original failure before exposing an observation.
     * 
     * @param phase
     *          operation that threw
     * @param cause
     *          exact original failure
     */
    protected Failure(Phase phase, Throwable cause) {
      Objects.requireNonNull(phase, "phase");
      super("Command destination operation failed", Objects.requireNonNull(cause, "cause"));
      observedPhase = phase;
    }

    /**
     * Returns the actual adapter operation that threw.
     * 
     * @return observed failure phase
     */
    public final Phase phase() {
      return observedPhase;
    }

    /**
     * Returns the exact original cause in its declared exception family.
     * 
     * @param <T>
     *          original failure type
     * @param kind
     *          required original failure type
     * @return original mutable exception
     */
    protected final <T extends Throwable> T original(Class<T> kind) {
      return kind.cast(Objects.requireNonNull(super.getCause(), "cause"));
    }

    /**
     * Rejects restoration without the original operation observation.
     * 
     * @param input
     *          serialization stream
     * @throws InvalidObjectException
     *           always
     */
    @Serial
    private void readObject(ObjectInputStream input) throws InvalidObjectException {
      Objects.requireNonNull(input, "input");
      throw new InvalidObjectException("Destination failure requires its original operation context");
    }
  }
}
