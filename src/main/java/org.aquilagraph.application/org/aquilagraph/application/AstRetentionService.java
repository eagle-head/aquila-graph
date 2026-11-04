package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.ComputedPublicationSubject.Ast;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.port.AstResultRepository;
import org.aquilagraph.application.port.AstResultRepositoryFactory;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.api.AstOutcome;

/** Independent complete-AST publication and exact query with lazy single-operation ownership. */
public final class AstRetentionService {
  private final AstResultRepositoryFactory repositories;

  /**
   * Creates independent retention without acquiring any repository.
   *
   * @param repositories
   *          exact-operation lazy repository factory
   */
  public AstRetentionService(AstResultRepositoryFactory repositories) {
    this.repositories = Objects.requireNonNull(repositories, "repositories");
  }

  /**
   * Retains an independently complete AST without deriving graphs.
   *
   * @param outcome
   *          established source outcome
   * @return saved, ineligible, or declared repository failure
   */
  public PublicationOutcome retainAst(AstOutcome outcome) {
    return publish(new Ast(Objects.requireNonNull(outcome, "outcome")));
  }

  /**
   * Applies explicit independent retention intent without graph production.
   *
   * @param request
   *          established source and explicit retention intent
   * @return no-attempt decision or independent completed-AST publication outcome
   */
  public PublicationOutcome retainAst(AstPublicationRequest request) {
    Objects.requireNonNull(request, "request");
    if (request.intent() == PublicationIntent.DO_NOT_SAVE) {
      return PublicationCoordinator.notRequested(new Ast(request.ast()));
    }
    return retainAst(request.ast());
  }

  /**
   * Retains the complete source inside an already computed coordinated request.
   *
   * @param run
   *          established application result, independently of CFG completion
   * @return independent AST publication outcome
   */
  public PublicationOutcome retainAst(AnalysisRun run) {
    return publish(new Coordinated(Objects.requireNonNull(run, "run")));
  }

  /**
   * Returns an exact normal lookup only after enclosing repository closure.
   *
   * @param identity
   *          exact requested source identity
   * @return admitted, absent, or incompatible lookup
   */
  public AstLookup findAst(AstIdentity identity) {
    var context = RepositoryOperationContext.queryAst(identity);
    var owned = new OwnedRepository(context);
    return owned.use(repositories::open, repository -> {
      var result = owned.observe(Boundary.OPERATION, () -> repository.find(identity));
      return owned.observe(Boundary.VALIDATION, () -> {
        Objects.requireNonNull(result, "AST lookup");
        if (!identity.equals(result.requestedIdentity())) {
          throw new IllegalArgumentException("AST repository returned another requested identity");
        }
        return result;
      });
    }, AstResultRepository::close);
  }

  private PublicationOutcome publish(ComputedPublicationSubject subject) {
    return PublicationCoordinator.publish(subject, PublicationIntent.SAVE_COMPLETE_AST, repositories::open,
        repository -> repository.saveComplete(subject.ast()), AstResultRepository::close);
  }
}
