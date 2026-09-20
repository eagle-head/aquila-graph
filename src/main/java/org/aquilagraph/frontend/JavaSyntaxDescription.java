package org.aquilagraph.frontend;

import java.util.Objects;
import org.aquilagraph.model.SyntaxKind;

record JavaSyntaxDescription(SyntaxKind kind, String detail) {
  JavaSyntaxDescription {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(detail, "detail");
  }
}
