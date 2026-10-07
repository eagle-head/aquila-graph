package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;
import org.aquilagraph.source.api.AstCompletion;

/**
 * Coordinated computation and its independent publication result.
 *
 * @param run
 *          exact computed request and independent domain outcomes
 * @param publication
 *          independent repository decision or result
 */
public record AnalysisExecution(AnalysisRun run, PublicationOutcome publication) {
  /** Retains both outcomes without changing either domain qualification. */
  public AnalysisExecution {
    Objects.requireNonNull(run, "run");
    Objects.requireNonNull(publication, "publication");
    var ast = run.representation().ast();
    Optional<PublicationSubjectIdentity> expected = switch (publication.intent()) {
      case DO_NOT_SAVE, SAVE_COMPLETE_AST -> ast.result().map(value -> new Ast(value.identity()));
      case SAVE_COMPLETED -> Optional.of(new CompletedRun(run.identity().key()));
    };
    if (!publication.subjectIdentity().equals(expected)) {
      throw new IllegalArgumentException("Publication must retain the exact computed subject in its own namespace");
    }
    if (publication.intent() == PublicationIntent.SAVE_COMPLETE_AST
        && ((publication.status() == PublicationStatus.NOT_ELIGIBLE) == (ast.completion() == AstCompletion.COMPLETE))) {
      throw new IllegalArgumentException("AST publication eligibility must follow the independent source qualification");
    }
    if (publication.intent() == PublicationIntent.SAVE_COMPLETED
        && ((publication.status() == PublicationStatus.NOT_ELIGIBLE) == run.isPublishable())) {
      throw new IllegalArgumentException("Completed-run publication requires complete source and exact qualified body coverage");
    }
  }
}
