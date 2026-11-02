package org.aquilagraph.cli;

import java.util.Objects;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.application.port.AnalysisUseCase;

/** Invokes the analysis input port and translates application failures into CLI outcomes. */
final class AnalyzeCommandExecutor {
  private final AnalysisUseCase analysisUseCase;

  AnalyzeCommandExecutor(AnalysisUseCase analysisUseCase) {
    this.analysisUseCase = Objects.requireNonNull(analysisUseCase, "analysisUseCase");
  }

  AnalyzeCommandOutcome execute(AnalyzeCommandArguments arguments) {
    Objects.requireNonNull(arguments, "arguments");
    try {
      return new AnalyzeCommandOutcome.Executed(analysisUseCase.execute(arguments.toAnalysisRequest()));
    } catch (AnalysisRepositoryException exception) {
      return new AnalyzeCommandOutcome.RepositoryFailed(exception);
    } catch (AnalysisInputException exception) {
      return new AnalyzeCommandOutcome.InputRejected(exception.description());
    } catch (AnalysisEnvironmentException exception) {
      return new AnalyzeCommandOutcome.EnvironmentFailed(exception.description());
    }
  }
}
