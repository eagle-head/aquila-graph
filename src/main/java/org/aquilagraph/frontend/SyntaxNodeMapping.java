package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxValue;

record SyntaxNodeMapping(SyntaxKind kind, SyntaxValue value, List<SyntaxChildMapping> children,
    Optional<SemanticReference> semanticReference) {
  SyntaxNodeMapping {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(value, "value");
    children = List.copyOf(Objects.requireNonNull(children, "children"));
    Objects.requireNonNull(semanticReference, "semanticReference");
  }
}
