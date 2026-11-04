package org.aquilagraph;

import org.aquilagraph.application.AnalysisApplicationService;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.CompletedRunPublicationService;
import org.aquilagraph.application.SourceAnalysisRunner;
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
    var analysisRunner = new SourceAnalysisRunner(JavaFrontends.standard(), CfgDerivers.standard());
    var retention = new AstRetentionService(Neo4jRepositories.astResults(() -> Neo4jConnectionSettings.fromEnvironment(environment)));
    var publication = new CompletedRunPublicationService(
        Neo4jRepositories.completedRuns(() -> Neo4jConnectionSettings.fromEnvironment(environment)));
    var analysisService = new AnalysisApplicationService(analysisRunner, retention, publication);
    var command = new AnalyzeCommand(analysisService, JavaRelease.JAVA_25);
    var exitCode = command.run(arguments, System.out, System.err);
    if (exitCode != AnalyzeCommand.EXIT_COMPLETED) {
      System.exit(exitCode);
    }
  }
}
