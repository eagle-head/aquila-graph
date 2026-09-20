package org.aquilagraph.application;

import java.io.IOException;
import java.util.Objects;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;

/** Coordinates source discovery, analysis, and optional persistence. */
public final class AnalysisApplicationService {
  private final JavaFrontend frontend;
  private final AnalysisEngine analysisEngine;
  private final AnalysisRepositoryFactory repositoryFactory;

  /**
   * Creates the application service from its analysis and persistence collaborators.
   *
   * @param frontend
   *          Java source frontend
   * @param analysisEngine
   *          control-flow analysis engine
   * @param repositoryFactory
   *          persistence adapter factory
   */
  public AnalysisApplicationService(JavaFrontend frontend, AnalysisEngine analysisEngine, AnalysisRepositoryFactory repositoryFactory) {
    this.frontend = Objects.requireNonNull(frontend, "frontend");
    this.analysisEngine = Objects.requireNonNull(analysisEngine, "analysisEngine");
    this.repositoryFactory = Objects.requireNonNull(repositoryFactory, "repositoryFactory");
  }

  /**
   * Executes one analysis request and persists only a completed result when requested.
   *
   * @param request
   *          analysis input and persistence policy
   * @return analysis execution and persistence status
   * @throws IOException
   *           if source input cannot be read
   * @throws AnalysisPersistenceException
   *           if the persistence adapter fails
   */
  public AnalysisExecution execute(AnalysisRequest request) throws IOException {
    Objects.requireNonNull(request, "request");
    var frontendResult = frontend.analyze(request.sourceRoot(), request.compilerConfiguration());
    var run = analysisEngine.analyze(frontendResult);
    if (!run.isComplete()) {
      return AnalysisExecution.notEligibleForPersistence(run);
    }
    if (!request.persistenceRequested()) {
      return AnalysisExecution.withoutPersistenceRequest(run);
    }

    try (var repository = Objects.requireNonNull(repositoryFactory.open(), "repository")) {
      return AnalysisExecution.persisted(run, repository.persist(run));
    } catch (AnalysisRepositoryException | IllegalStateException exception) {
      throw new AnalysisPersistenceException(run, exception);
    }
  }
}
