package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.application.port.SourceAnalyzer;
import org.aquilagraph.core.AnalysisEngine;

/** Coordinates source discovery, analysis, and optional repository storage. */
public final class AnalysisApplicationService {
  private final SourceAnalyzer sourceAnalyzer;
  private final AnalysisEngine analysisEngine;
  private final AnalysisRunRepositoryFactory repositoryFactory;

  /**
   * Creates the application service from its analysis and repository collaborators.
   *
   * @param sourceAnalyzer
   *          source-analysis port
   * @param analysisEngine
   *          control-flow analysis engine
   * @param repositoryFactory
   *          repository factory
   */
  public AnalysisApplicationService(SourceAnalyzer sourceAnalyzer, AnalysisEngine analysisEngine,
      AnalysisRunRepositoryFactory repositoryFactory) {
    this.sourceAnalyzer = Objects.requireNonNull(sourceAnalyzer, "sourceAnalyzer");
    this.analysisEngine = Objects.requireNonNull(analysisEngine, "analysisEngine");
    this.repositoryFactory = Objects.requireNonNull(repositoryFactory, "repositoryFactory");
  }

  /**
   * Executes one analysis request and stores only a completed result when requested.
   *
   * @param request
   *          analysis input and repository policy
   * @return analysis execution and repository status
   * @throws AnalysisRepositoryException
   *           if the repository fails
   */
  public AnalysisExecution execute(AnalysisRequest request) {
    Objects.requireNonNull(request, "request");
    var frontendResult = sourceAnalyzer.analyze(request.sourceRoot(), request.compilerConfiguration());
    var run = analysisEngine.analyze(frontendResult);
    if (!run.isComplete()) {
      return AnalysisExecution.notEligibleForRepository(run, request.repositorySavePolicy());
    }
    if (request.repositorySavePolicy() == RepositorySavePolicy.DO_NOT_SAVE) {
      return AnalysisExecution.withoutRepositoryRequest(run);
    }

    try (var repository = Objects.requireNonNull(repositoryFactory.open(), "repository")) {
      repository.save(run);
      return AnalysisExecution.saved(run);
    } catch (RepositoryException exception) {
      throw new AnalysisRepositoryException(run, exception);
    }
  }
}
