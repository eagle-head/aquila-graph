package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.SyntaxKind;

record SyntaxNodeMapping(SyntaxKind kind, String detail, List<SyntaxChildMapping> children, SyntaxAcceptance acceptance) {
  SyntaxNodeMapping {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(detail, "detail");
    children = List.copyOf(Objects.requireNonNull(children, "children"));
    Objects.requireNonNull(acceptance, "acceptance");
    if (kind == SyntaxKind.UNSUPPORTED && acceptance != SyntaxAcceptance.UNSUPPORTED) {
      throw new IllegalArgumentException("Unsupported syntax cannot be accepted");
    }
  }
}
