package org.aquilagraph.cli;

import java.util.Objects;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRepositoryException;

/** Application outcomes translated into the command-line adapter's result vocabulary. */
sealed interface AnalyzeCommandOutcome {
  record Executed(AnalysisExecution execution) implements AnalyzeCommandOutcome {
    public Executed {
      Objects.requireNonNull(execution, "execution");
    }
  }

  final class RepositoryFailed implements AnalyzeCommandOutcome {
    private final AnalysisRepositoryException repositoryFailure;

    RepositoryFailed(AnalysisRepositoryException failure) {
      repositoryFailure = Objects.requireNonNull(failure, "failure");
    }

    AnalysisRepositoryException failure() {
      return repositoryFailure;
    }
  }

  record InputRejected(String message) implements AnalyzeCommandOutcome {
    public InputRejected {
      Objects.requireNonNull(message, "message");
    }
  }

  record EnvironmentFailed(String message) implements AnalyzeCommandOutcome {
    public EnvironmentFailed {
      Objects.requireNonNull(message, "message");
    }
  }
}
