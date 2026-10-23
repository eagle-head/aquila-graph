package org.aquilagraph.source.api;

import java.util.Objects;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Copied source kind, range, and attributed outcome of a written type.
 *
 * @param sourceKind
 *          exact public source-tree kind
 * @param origin
 *          written type range in the captured source
 * @param type
 *          copied compiler type outcome
 */
public record WrittenTypeEvidence(JavaSourceKind sourceKind, SourceSpan origin, SyntaxType type) {
  /** Validates the source type category and copied outcome. */
  public WrittenTypeEvidence {
    Objects.requireNonNull(sourceKind, "sourceKind");
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(type, "type");
    if (!sourceKind.identifiesTypeUseSyntax() || origin.length() == 0 || type == SyntaxType.NotApplicable.INSTANCE) {
      throw new IllegalArgumentException("Written type evidence requires a type-use kind, source range, and attributed outcome");
    }
  }
}
