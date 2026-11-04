package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.application.port.AnalysisUseCase;

/** Applies publication only after a normal computation. */
final class AnalysisExecutionCoordinator implements AnalysisUseCase {
  private final AnalysisComputation computation;
  private final PublicationCoordinator publication;

  AnalysisExecutionCoordinator(AnalysisComputation computation, PublicationCoordinator publication) {
    this.computation = Objects.requireNonNull(computation, "computation");
    this.publication = publication;
  }

  @Override
  public AnalysisExecution execute(AnalysisRequest request) {
    Objects.requireNonNull(request, "request");
    var run = computation.analyze(request.computation());
    return new AnalysisExecution(run, publication.apply(run, request.publicationIntent()));
  }
}
