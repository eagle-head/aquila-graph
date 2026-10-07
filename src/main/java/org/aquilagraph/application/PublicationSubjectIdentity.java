package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.identity.api.AstIdentity;

/** Exact published subject namespace for retained AST and coordinated runs. */
public sealed interface PublicationSubjectIdentity permits PublicationSubjectIdentity.Ast, PublicationSubjectIdentity.CompletedRun {
  /**
   * Exact independently retained source identity.
   *
   * @param identity
   *          intrinsically admitted AST identity
   */
  record Ast(AstIdentity identity) implements PublicationSubjectIdentity {
    /** Requires an actual admitted result identity. */
    public Ast {
      Objects.requireNonNull(identity, "identity");
    }
  }

  /**
   * Completed-run subject qualified by its source and exact selection.
   *
   * @param runId
   *          exact analysis request identity
   */
  record CompletedRun(String runId) implements PublicationSubjectIdentity {
    /** Requires a nonblank coordinated request identity. */
    public CompletedRun {
      if (Objects.requireNonNull(runId, "runId").isBlank()) {
        throw new IllegalArgumentException("Completed-run subject identity cannot be blank");
      }
    }
  }
}
