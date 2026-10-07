package org.aquilagraph;

import java.util.Map;
import org.aquilagraph.application.AnalysisApplicationService;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.CompletedRunPublicationService;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cli.AnalyzeCommand;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.repository.neo4j.Neo4jAnalysisRunRepository;
import org.aquilagraph.repository.neo4j.Neo4jAstResultRepositoryFactory;
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
    var analysisRunner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var retention = new AstRetentionService(
        new Neo4jAstResultRepositoryFactory(() -> Neo4jConnectionSettings.fromEnvironment(environment)));
    var publication = new CompletedRunPublicationService(() -> openNeo4jRepository(environment));
    var analysisService = new AnalysisApplicationService(analysisRunner, retention, publication);
    var command = new AnalyzeCommand(analysisService, JavaRelease.JAVA_25);
    var exitCode = command.run(arguments, System.out, System.err);
    if (exitCode != AnalyzeCommand.EXIT_COMPLETED) {
      System.exit(exitCode);
    }
  }

  private static AnalysisRunRepository openNeo4jRepository(Map<String, String> environment) {
    try {
      return new Neo4jAnalysisRunRepository(Neo4jConnectionSettings.fromEnvironment(environment));
    } catch (IllegalArgumentException exception) {
      throw new RepositoryException("Invalid Neo4j repository configuration", exception);
    }
  }
}
