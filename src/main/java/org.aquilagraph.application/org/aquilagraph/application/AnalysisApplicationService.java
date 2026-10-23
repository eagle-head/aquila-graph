package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.port.CompletedRunPublication;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.api.AstOutcome;

/** Port-based computation with independently selected AST or completed-run publication. */
public final class AnalysisApplicationService implements AnalysisUseCase {
  private final SourceAnalysisRunner analysis;
  private final AstRetentionService retention;
  private final CompletedRunPublication completedRuns;

  /**
   * Creates computation and independent AST or completed-run publication coordination.
   *
   * @param analysis
   *          source and CFG port coordinator
   * @param retention
   *          independent AST retention and lookup
   * @param completedRuns
   *          native completed-run publication port
   */
  public AnalysisApplicationService(SourceAnalysisRunner analysis, AstRetentionService retention, CompletedRunPublication completedRuns) {
    this.analysis = Objects.requireNonNull(analysis, "analysis");
    this.retention = Objects.requireNonNull(retention, "retention");
    this.completedRuns = Objects.requireNonNull(completedRuns, "completedRuns");
  }

  /**
   * Produces and derives only requested work, then applies independent publication intent.
   *
   * @param request
   *          source, optional CFG selection, and explicit publication intent
   * @return computed run and independently established publication result
   */
  @Override
  public AnalysisExecution execute(AnalysisRequest request) {
    Objects.requireNonNull(request, "request");
    var run = analysis.analyze(request);
    var publication = switch (request.publicationIntent()) {
      case DO_NOT_SAVE ->
        new PublicationOutcome(PublicationIntent.DO_NOT_SAVE, run.representation().ast().result().map(result -> new Ast(result.identity())),
            PublicationStatus.NOT_REQUESTED, CommitKnowledge.NOT_STARTED, Optional.empty());
      case SAVE_COMPLETE_AST -> retention.retainAst(run);
      case SAVE_COMPLETED -> completedRuns.publish(run);
    };
    if (publication.intent() != request.publicationIntent()) {
      throw new IllegalStateException("Publication port returned a different requested intent");
    }
    return new AnalysisExecution(run, publication);
  }

  /**
   * Derives existing source without production or publication.
   *
   * @param ast
   *          exact established source outcome
   * @param request
   *          exact caller-bound graph request
   * @return computed run without a repository operation
   */
  public AnalysisRun deriveExisting(AstOutcome ast, CfgRequest request) {
    return analysis.deriveExisting(ast, request);
  }

  /**
   * Explicitly retains an independently complete AST without deriving graphs.
   *
   * @param ast
   *          exact established source outcome
   * @return independent publication decision or result
   */
  public PublicationOutcome retainAst(AstOutcome ast) {
    return retention.retainAst(ast);
  }

  /**
   * Applies explicit independent source retention intent.
   *
   * @param request
   *          exact established source and no-save or complete-AST intent
   * @return independent publication decision or result
   */
  public PublicationOutcome retainAst(AstPublicationRequest request) {
    return retention.retainAst(request);
  }

  /**
   * Queries an exact source identity without production or graph loading.
   *
   * @param identity
   *          exact requested source identity
   * @return normal admitted, absent, or incompatible lookup after closure
   */
  public AstLookup findAst(AstIdentity identity) {
    return retention.findAst(identity);
  }
}
