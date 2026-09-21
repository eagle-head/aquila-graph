package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxValue;

record SyntaxNodeMapping(SyntaxKind kind, SyntaxValue value, List<SyntaxChildMapping> children,
    Optional<SemanticReference> semanticReference) {
  SyntaxNodeMapping {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(value, "value");
    children = List.copyOf(Objects.requireNonNull(children, "children"));
    Objects.requireNonNull(semanticReference, "semanticReference");
  }
}
