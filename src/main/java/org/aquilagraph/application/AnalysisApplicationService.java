package org.aquilagraph.application;

import java.io.IOException;
import java.util.Objects;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;

public final class AnalysisApplicationService {
  private final JavaFrontend frontend;
  private final AnalysisEngine analysisEngine;
  private final AnalysisRepositoryFactory repositoryFactory;

  public AnalysisApplicationService(
      JavaFrontend frontend,
      AnalysisEngine analysisEngine,
      AnalysisRepositoryFactory repositoryFactory) {
    this.frontend = Objects.requireNonNull(frontend, "frontend");
    this.analysisEngine = Objects.requireNonNull(analysisEngine, "analysisEngine");
    this.repositoryFactory = Objects.requireNonNull(repositoryFactory, "repositoryFactory");
  }

  public AnalysisExecution execute(AnalysisRequest request) throws IOException {
    Objects.requireNonNull(request, "request");
    var frontendResult =
        frontend.analyze(request.sourceRoot(), request.compilerConfiguration());
    var run = analysisEngine.analyze(frontendResult);
    if (!run.isComplete()) {
      return AnalysisExecution.notEligibleForPersistence(run);
    }
    if (!request.persistenceRequested()) {
      return AnalysisExecution.withoutPersistenceRequest(run);
    }

    try (var repository = Objects.requireNonNull(repositoryFactory.open(), "repository")) {
      return AnalysisExecution.persisted(run, repository.persist(run));
    } catch (RuntimeException exception) {
      throw new AnalysisPersistenceException(run, exception);
    }
  }
}
