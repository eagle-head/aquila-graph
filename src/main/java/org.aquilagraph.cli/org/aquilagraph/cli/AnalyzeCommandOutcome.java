package org.aquilagraph.cli;

import java.util.Objects;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;

/** Operational command results retaining original failures independently of report output. */
public sealed interface AnalyzeCommandOutcome {
  /**
   * Rejected arguments without application execution.
   *
   * @param reason
   *          exact structured refusal
   */
  record UsageRejected(ArgumentRejection reason) implements AnalyzeCommandOutcome {
    /** Requires the exact refusal. */
    public UsageRejected {
      Objects.requireNonNull(reason, "reason");
    }
  }

  /**
   * Returned computation and publication outcomes.
   *
   * @param execution
   *          original execution
   */
  record Executed(AnalysisExecution execution) implements AnalyzeCommandOutcome {
    /** Retains the original execution. */
    public Executed {
      Objects.requireNonNull(execution, "execution");
    }
  }

  /**
   * Named input failure with its original relationships.
   *
   */
  final class InputRejected extends Failure {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Retains the original input failure through its operational cause relationship.
     *
     * @param failure
     *          original named failure
     */
    public InputRejected(AnalysisInputException failure) {
      super(failure);
    }

    /**
     * Returns the original mutable failure, independently of any immutable report.
     *
     * @return original named input failure
     */
    public AnalysisInputException failure() {
      return original(AnalysisInputException.class);
    }
  }

  /** Named environment failure with its original relationships. */
  final class EnvironmentFailed extends Failure {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Retains the original environment failure through its operational cause relationship.
     *
     * @param failure
     *          original named failure
     */
    public EnvironmentFailed(AnalysisEnvironmentException failure) {
      super(failure);
    }

    /**
     * Returns the original mutable failure, independently of any immutable report.
     *
     * @return original named environment failure
     */
    public AnalysisEnvironmentException failure() {
      return original(AnalysisEnvironmentException.class);
    }
  }

  /** Mutable operational failure carrier, separate from the immutable presentation snapshot. */
  abstract sealed class Failure extends RuntimeException implements AnalyzeCommandOutcome permits InputRejected, EnvironmentFailed {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Requires the original named analysis failure before initializing the carrier.
     *
     * @param failure
     *          original analysis failure
     */
    protected Failure(RuntimeException failure) {
      Objects.requireNonNull(failure, "failure");
      super("Command analysis failed", failure);
    }

    /**
     * Returns the exact original cause stored by the operational carrier.
     *
     * @param <T>
     *          named failure type
     * @param kind
     *          required original failure type
     * @return original mutable analysis failure
     */
    protected final <T extends RuntimeException> T original(Class<T> kind) {
      return kind.cast(Objects.requireNonNull(super.getCause(), "failure"));
    }

    /**
     * Rejects restoration without the original operational relationship.
     *
     * @param input
     *          serialization stream
     * @throws InvalidObjectException
     *           always
     */
    @Serial
    private void readObject(ObjectInputStream input) throws InvalidObjectException {
      Objects.requireNonNull(input, "input");
      throw new InvalidObjectException("Command failure requires an original named analysis failure");
    }
  }
}
