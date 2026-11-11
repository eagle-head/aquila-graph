package org.aquilagraph;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AstRetentionRepositoryFactory;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cli.AnalyzeCommand;
import org.aquilagraph.cli.CommandExitPolicy;
import org.aquilagraph.cli.CommandDestination;
import org.aquilagraph.cli.CommandRunner;
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
    var runner = commandRunner(Neo4jRepositories.astRetention(() -> Neo4jConnectionSettings.fromEnvironment(environment)),
        Neo4jRepositories.completedRuns(() -> Neo4jConnectionSettings.fromEnvironment(environment)));
    var exitCode = runner.run(arguments, CommandDestination.printStream(System.out), CommandDestination.printStream(System.err)).exitCode();
    if (exitCode != CommandExitPolicy.EXIT_COMPLETED) {
      System.exit(exitCode);
    }
  }

  static CommandRunner commandRunner(AstRetentionRepositoryFactory astRetention, AnalysisRunRepositoryFactory completedRuns) {
    // The CLI inherits the JVM host's logging configuration for every command.
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var execution = AnalysisCapabilities.execution(computation, astRetention, completedRuns);
    return new CommandRunner(new AnalyzeCommand(execution, JavaRelease.JAVA_25, new Neo4jFailureDetailProjector()));
  }
}
