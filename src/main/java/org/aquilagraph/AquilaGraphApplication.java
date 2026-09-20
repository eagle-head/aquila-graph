package org.aquilagraph;

import java.util.Map;
import org.aquilagraph.application.AnalysisApplicationService;
import org.aquilagraph.application.AnalysisRepository;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.cli.AnalyzeCommand;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.persistence.Neo4jAnalysisRepository;
import org.aquilagraph.persistence.Neo4jConnectionSettings;

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
    var analysisService = new AnalysisApplicationService(new JavaFrontend(new JavaSourceLoader()), new AnalysisEngine(),
        () -> openNeo4jRepository(environment));
    var command = new AnalyzeCommand(analysisService);
    var exitCode = command.run(arguments, System.out, System.err);
    if (exitCode != AnalyzeCommand.EXIT_COMPLETED) {
      System.exit(exitCode);
    }
  }

  private static AnalysisRepository openNeo4jRepository(Map<String, String> environment) {
    try {
      return new Neo4jAnalysisRepository(Neo4jConnectionSettings.fromEnvironment(environment));
    } catch (IllegalArgumentException exception) {
      throw new AnalysisRepositoryException("Invalid Neo4j persistence configuration", exception);
    }
  }
}
