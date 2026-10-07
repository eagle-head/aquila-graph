package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.source.api.AstOutcome;

/**
 * Independent source retention intent without a graph selection.
 *
 * @param ast
 *          exact established source outcome
 * @param intent
 *          explicit no-save or completed-AST retention intent
 */
public record AstPublicationRequest(AstOutcome ast, PublicationIntent intent) {
  /** Keeps unavailable outcomes truthful and excludes the separate combined-run operation. */
  public AstPublicationRequest {
    Objects.requireNonNull(ast, "ast");
    Objects.requireNonNull(intent, "intent");
    if (intent == PublicationIntent.SAVE_COMPLETED) {
      throw new IllegalArgumentException("Independent AST publication cannot request a combined-run save");
    }
  }
}
