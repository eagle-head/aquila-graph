package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;

/**
 * Normal lookup retaining the exact requested identity and explicit absent or incompatible state.
 *
 * @param requestedIdentity
 *          exact query subject
 * @param status
 *          normal lookup classification
 * @param outcome
 *          complete admitted stored source only for FOUND
 * @param unsupportedFormat
 *          observed unsupported storage format or source profile version only for INCOMPATIBLE
 */
public record AstLookup(AstIdentity requestedIdentity, AstLookupStatus status, Optional<AstOutcome> outcome,
    Optional<String> unsupportedFormat) {
  /** Prevents corruption, identity mismatch, or incomplete storage from becoming a successful lookup. */
  public AstLookup {
    Objects.requireNonNull(requestedIdentity, "requestedIdentity");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(unsupportedFormat, "unsupportedFormat");
    if ((status == AstLookupStatus.FOUND) != outcome.isPresent()
        || (status == AstLookupStatus.INCOMPATIBLE) != unsupportedFormat.isPresent()
        || unsupportedFormat.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("AST lookup fields contradict its normal state");
    }
    outcome.ifPresent(value -> {
      if (value.completion() != AstCompletion.COMPLETE || !value.result().orElseThrow().identity().equals(requestedIdentity)) {
        throw new IllegalArgumentException("Found AST must be complete and match the exact requested identity");
      }
    });
  }
}
