package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;
import org.aquilagraph.identity.api.AstIdentity;

/**
 * Exact repository operation and subject.
 *
 * @param kind
 *          operation being attempted
 * @param subject
 *          exact attempted or requested identity
 */
public record RepositoryOperationContext(RepositoryOperationKind kind, PublicationSubjectIdentity subject) {
  /** Requires the exact subject namespace for the operation. */
  public RepositoryOperationContext {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(subject, "subject");
    if ((kind == RepositoryOperationKind.SAVE_COMPLETED_RUN) != (subject instanceof CompletedRun)) {
      throw new IllegalArgumentException("Repository operation requires a subject in its own namespace");
    }
  }

  /**
   * Identifies complete-AST publication.
   *
   * @param identity
   *          exact source identity
   * @return source publication context
   */
  public static RepositoryOperationContext saveAst(AstIdentity identity) {
    return new RepositoryOperationContext(RepositoryOperationKind.SAVE_AST, new Ast(identity));
  }

  /**
   * Identifies an exact AST lookup.
   *
   * @param identity
   *          requested source identity
   * @return source query context
   */
  public static RepositoryOperationContext queryAst(AstIdentity identity) {
    return new RepositoryOperationContext(RepositoryOperationKind.QUERY_AST, new Ast(identity));
  }

  /**
   * Identifies publication of a coordinated analysis run.
   *
   * @param runId
   *          exact analysis request key
   * @return completed-run publication context
   */
  public static RepositoryOperationContext saveCompletedRun(String runId) {
    return new RepositoryOperationContext(RepositoryOperationKind.SAVE_COMPLETED_RUN, new CompletedRun(runId));
  }
}
