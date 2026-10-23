package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.ComputedPublicationSubject.Ast;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.port.AstResultRepositoryFactory;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.api.AstCompletion;
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
      return new PublicationOutcome(request.intent(),
          request.ast().result().map(result -> new PublicationSubjectIdentity.Ast(result.identity())), PublicationStatus.NOT_REQUESTED,
          CommitKnowledge.NOT_STARTED, Optional.empty());
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
    var context = new RepositoryOperationContext(RepositoryOperationKind.QUERY_AST, identity);
    try (var owned = new OwnedAstRepository(context)) {
      owned.acquire(repositories);
      return owned.find(identity);
    }
  }

  private PublicationOutcome publish(ComputedPublicationSubject subject) {
    var ast = subject.ast();
    var identity = ast.result().map(result -> result.identity());
    var subjectIdentity = identity.<PublicationSubjectIdentity>map(PublicationSubjectIdentity.Ast::new);
    if (ast.completion() != AstCompletion.COMPLETE) {
      return new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, subjectIdentity, PublicationStatus.NOT_ELIGIBLE,
          CommitKnowledge.NOT_STARTED, Optional.empty());
    }
    var context = new RepositoryOperationContext(RepositoryOperationKind.SAVE_AST, identity.orElseThrow());
    try (var owned = new OwnedAstRepository(context)) {
      owned.acquire(repositories);
      owned.saveComplete(ast);
    } catch (RepositoryOperationException exception) {
      var failure = new PublicationFailure(exception);
      if (exception.effectivePrimary() instanceof RepositoryException) {
        return new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, subjectIdentity, PublicationStatus.FAILED, failure.knowledge(),
            Optional.of(failure));
      }
      throw new AnalysisPublicationException(subject, failure);
    }
    return new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, subjectIdentity, PublicationStatus.SAVED,
        CommitKnowledge.KNOWN_COMMIT, Optional.empty());
  }
}
