package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.application.port.AnalysisRunAnalyzer;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.application.port.RepositoryException;

/** Coordinates source discovery, analysis, and optional repository storage. */
public final class AnalysisApplicationService implements AnalysisUseCase {
  private final AnalysisRunAnalyzer analysisRunAnalyzer;
  private final AnalysisRunRepositoryFactory repositoryFactory;

  /**
   * Creates the application service from its analysis and repository collaborators.
   *
   * @param analysisRunAnalyzer
   *          complete analysis-run operation
   * @param repositoryFactory
   *          repository factory
   */
  public AnalysisApplicationService(AnalysisRunAnalyzer analysisRunAnalyzer, AnalysisRunRepositoryFactory repositoryFactory) {
    this.analysisRunAnalyzer = Objects.requireNonNull(analysisRunAnalyzer, "analysisRunAnalyzer");
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
  @Override
  public AnalysisExecution execute(AnalysisRequest request) {
    Objects.requireNonNull(request, "request");
    var run = analysisRunAnalyzer.analyze(request.sourceRoot(), request.compilerConfiguration());
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
