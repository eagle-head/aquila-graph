package org.aquilagraph.application;

import java.io.IOException;
import java.util.Objects;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.repository.AnalysisRunRepositoryFactory;
import org.aquilagraph.repository.RepositoryException;

/** Coordinates source discovery, analysis, and optional repository storage. */
public final class AnalysisApplicationService {
  private final JavaFrontend frontend;
  private final AnalysisEngine analysisEngine;
  private final AnalysisRunRepositoryFactory repositoryFactory;

  /**
   * Creates the application service from its analysis and repository collaborators.
   *
   * @param frontend
   *          Java source frontend
   * @param analysisEngine
   *          control-flow analysis engine
   * @param repositoryFactory
   *          repository factory
   */
  public AnalysisApplicationService(JavaFrontend frontend, AnalysisEngine analysisEngine, AnalysisRunRepositoryFactory repositoryFactory) {
    this.frontend = Objects.requireNonNull(frontend, "frontend");
    this.analysisEngine = Objects.requireNonNull(analysisEngine, "analysisEngine");
    this.repositoryFactory = Objects.requireNonNull(repositoryFactory, "repositoryFactory");
  }

  /**
   * Executes one analysis request and stores only a completed result when requested.
   *
   * @param request
   *          analysis input and repository policy
   * @return analysis execution and repository status
   * @throws IOException
   *           if source input cannot be read
   * @throws AnalysisRepositoryException
   *           if the repository fails
   */
  public AnalysisExecution execute(AnalysisRequest request) throws IOException {
    Objects.requireNonNull(request, "request");
    var frontendResult = frontend.analyze(request.sourceRoot(), request.compilerConfiguration());
    var run = analysisEngine.analyze(frontendResult);
    if (!run.isComplete()) {
      return AnalysisExecution.notEligibleForRepository(run);
    }
    if (!request.repositorySaveRequested()) {
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
