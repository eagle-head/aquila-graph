package org.aquilagraph.cli;

import java.util.Objects;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.model.AnalysisRun;

/** Application outcomes translated into the command-line adapter's result vocabulary. */
sealed interface AnalyzeCommandOutcome {
  record Executed(AnalysisExecution execution) implements AnalyzeCommandOutcome {
    public Executed {
      Objects.requireNonNull(execution, "execution");
    }
  }

  record RepositoryFailed(AnalysisRun run, String message) implements AnalyzeCommandOutcome {
    public RepositoryFailed {
      Objects.requireNonNull(run, "run");
      Objects.requireNonNull(message, "message");
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
