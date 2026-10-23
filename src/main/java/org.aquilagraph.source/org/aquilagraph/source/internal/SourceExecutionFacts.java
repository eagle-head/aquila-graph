package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SyntaxNode;
import java.util.Objects;
import java.util.List;
import java.util.Optional;

/**
 * Immutable ownership and compiler addresses for one actual source execution scope.
 *
 * @param scope
 *          owning execution scope
 * @param root
 *          copied written root
 * @param methodOrigin
 *          origin present exactly for method scopes
 * @param attributedOccurrences
 *          copied compiler occurrence addresses
 */
public record SourceExecutionFacts(ExecutionScope scope, SyntaxNode root, Optional<MethodBodyOrigin> methodOrigin,
    List<AttributedBodyOccurrence> attributedOccurrences) {
  /**
   * Copies occurrence addresses and checks the execution-scope origin.
   */
  public SourceExecutionFacts {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(methodOrigin, "methodOrigin");
    if ((scope instanceof ExecutionScope.Method) != methodOrigin.isPresent()) {
      throw new IllegalArgumentException("Only an actual method scope has a method body origin");
    }
    attributedOccurrences = List.copyOf(attributedOccurrences);
  }
}
