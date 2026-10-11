package org.aquilagraph.source.api;

import java.util.Objects;

/**
 * Actual attributed compiler-tree address copied for one retained execution occurrence.
 *
 * @param scopeKey
 *          canonical containing execution scope
 * @param nodeKey
 *          exact retained occurrence
 * @param sourceFileKey
 *          captured source owner
 * @param sourceKind
 *          exact public source-tree kind
 * @param compilerPath
 *          actual root-relative attributed tree address
 */
public record AttributedBodyOccurrence(String scopeKey, String nodeKey, String sourceFileKey, JavaSourceKind sourceKind,
    SyntaxStructuralPath compilerPath) {
  /** Checks copied address shape without authenticating compiler provenance. */
  public AttributedBodyOccurrence {
    DecisionValueValidation.requireKey(scopeKey, "scopeKey");
    DecisionValueValidation.requireKey(nodeKey, "nodeKey");
    DecisionValueValidation.requireKey(sourceFileKey, "sourceFileKey");
    Objects.requireNonNull(sourceKind, "sourceKind");
    Objects.requireNonNull(compilerPath, "compilerPath");
    if (!sourceKind.identifiesSourceSyntax()) {
      throw new IllegalArgumentException("An attributed occurrence requires an actual source-tree kind");
    }
  }
}
