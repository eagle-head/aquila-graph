package org.aquilagraph;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cli.AnalyzeCommand;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;

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
    var environment = System.getenv();
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var execution = AnalysisCapabilities.execution(computation,
        Neo4jRepositories.astResults(() -> Neo4jConnectionSettings.fromEnvironment(environment)),
        Neo4jRepositories.completedRuns(() -> Neo4jConnectionSettings.fromEnvironment(environment)));
    var command = new AnalyzeCommand(execution, JavaRelease.JAVA_25);
    var exitCode = command.run(arguments, System.out, System.err);
    if (exitCode != AnalyzeCommand.EXIT_COMPLETED) {
      System.exit(exitCode);
    }
  }
}
