package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.List;
import java.util.Optional;

/** Immutable ownership and compiler addresses for one actual source execution scope. */
record SourceExecutionFacts(ExecutionScope scope, SyntaxNode root, Optional<MethodBodyOrigin> methodOrigin,
    List<AttributedBodyOccurrence> attributedOccurrences) {
  SourceExecutionFacts {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(methodOrigin, "methodOrigin");
    if ((scope instanceof ExecutionScope.Method) != methodOrigin.isPresent()) {
      throw new IllegalArgumentException("Only an actual method scope has a method body origin");
    }
    attributedOccurrences = List.copyOf(attributedOccurrences);
  }
}
