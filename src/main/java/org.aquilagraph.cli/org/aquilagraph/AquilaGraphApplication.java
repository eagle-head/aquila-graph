package org.aquilagraph;

import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AstRetentionRepositoryFactory;
import org.aquilagraph.cli.AnalyzeCommand;
import org.aquilagraph.cli.CommandExitPolicy;
import org.aquilagraph.cli.CommandDestination;
import org.aquilagraph.cli.CommandRunner;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.library.AquilaGraph;
import org.aquilagraph.library.ObservabilityConfiguration;
import org.aquilagraph.identity.api.JavaRelease;

/** Composition root and process entry point for the Aquila Graph command-line application. */
public final class AquilaGraphApplication {
  private AquilaGraphApplication() {
  }

  /**
   * Runs the command-line application and exits with its result code.
   *
   * @param arguments
   *          command-line arguments
   */
  public static void main(String[] arguments) {
    final int exitCode;
    try (var aquila = AquilaGraph.open()) {
      var runner = commandRunner(aquila);
      exitCode = runner.run(arguments, CommandDestination.printStream(System.out), CommandDestination.printStream(System.err)).exitCode();
    }
    if (exitCode != CommandExitPolicy.EXIT_COMPLETED) {
      System.exit(exitCode);
    }
  }

  static CommandRunner commandRunner(AstRetentionRepositoryFactory astRetention, AnalysisRunRepositoryFactory completedRuns) {
    return commandRunner(request -> {
      try (var aquila = AquilaGraph.open(ObservabilityConfiguration.disabled(), astRetention, completedRuns)) {
        return aquila.execute(request);
      }
    });
  }

  private static CommandRunner commandRunner(AnalysisUseCase execution) {
    return new CommandRunner(new AnalyzeCommand(execution, JavaRelease.JAVA_25, new Neo4jFailureDetailProjector()));
  }
}
