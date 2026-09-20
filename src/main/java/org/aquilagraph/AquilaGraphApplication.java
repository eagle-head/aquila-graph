package org.aquilagraph;

import org.aquilagraph.application.AnalysisApplicationService;
import org.aquilagraph.cli.AnalyzeCommand;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.persistence.Neo4jAnalysisRepository;
import org.aquilagraph.persistence.Neo4jConnectionSettings;

public final class AquilaGraphApplication {
  private AquilaGraphApplication() {}

  public static void main(String[] arguments) {
    var environment = System.getenv();
    var analysisService =
        new AnalysisApplicationService(
            new JavaFrontend(new JavaSourceLoader()),
            new AnalysisEngine(),
            () ->
                new Neo4jAnalysisRepository(
                    Neo4jConnectionSettings.fromEnvironment(environment)));
    var command = new AnalyzeCommand(analysisService);
    var exitCode = command.run(arguments, System.out, System.err);
    if (exitCode != AnalyzeCommand.EXIT_COMPLETED) {
      System.exit(exitCode);
    }
  }
}
