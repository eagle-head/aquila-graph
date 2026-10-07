package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.List;

/** Immutable body ownership retained independently of value admission. */
record SourceBodyFacts(SyntaxNode root, MethodBodyOrigin origin, List<AttributedBodyOccurrence> attributedOccurrences) {
  SourceBodyFacts {
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(origin, "origin");
    attributedOccurrences = List.copyOf(attributedOccurrences);
  }
}
