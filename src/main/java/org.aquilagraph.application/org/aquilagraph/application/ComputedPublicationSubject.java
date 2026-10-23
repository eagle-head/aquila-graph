package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.source.api.AstOutcome;

/** Already computed subject retained by publication without changing either domain owner. */
public sealed interface ComputedPublicationSubject permits ComputedPublicationSubject.Ast, ComputedPublicationSubject.Coordinated {
  /**
   * Independent source subject.
   *
   * @param outcome
   *          exact established source outcome
   */
  record Ast(AstOutcome outcome) implements ComputedPublicationSubject {
    /** Preserves the established source owner by reference. */
    public Ast {
      Objects.requireNonNull(outcome, "outcome");
    }
  }

  /**
   * Coordinated analysis subject.
   *
   * @param run
   *          exact already computed application run
   */
  record Coordinated(AnalysisRun run) implements ComputedPublicationSubject {
    /** Preserves the complete computed request and both domain outcomes. */
    public Coordinated {
      Objects.requireNonNull(run, "run");
    }
  }

  /**
   * Projects the already established source outcome from either subject variant.
   *
   * @return identical established source outcome
   */
  default AstOutcome ast() {
    return switch (this) {
      case Ast subject -> subject.outcome();
      case Coordinated subject -> subject.run().representation().ast();
    };
  }
}
