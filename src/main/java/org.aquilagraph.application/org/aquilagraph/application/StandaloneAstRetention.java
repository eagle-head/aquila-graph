package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.application.ComputedPublicationSubject.Ast;
import org.aquilagraph.application.port.AstResultRepositoryFactory;

/** Applies publication policy to the standalone source subject. */
final class StandaloneAstRetention implements AstRetention {
  private final AstResultRepositoryFactory repositories;

  StandaloneAstRetention(AstResultRepositoryFactory repositories) {
    this.repositories = Objects.requireNonNull(repositories, "repositories");
  }

  @Override
  public PublicationOutcome retainAst(AstPublicationRequest request) {
    Objects.requireNonNull(request, "request");
    var subject = new Ast(request.ast());
    return request.intent() == PublicationIntent.DO_NOT_SAVE
        ? PublicationCoordinator.notRequested(subject)
        : PublicationCoordinator.publishAst(subject, repositories);
  }
}
