package org.aquilagraph.frontend;

import java.util.Objects;
import org.aquilagraph.syntax.SyntaxRole;

record SyntaxStructuralPath(String value) {
  SyntaxStructuralPath {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("A syntax structural path cannot be blank");
    }
  }

  static SyntaxStructuralPath methodBody() {
    return new SyntaxStructuralPath("body");
  }

  SyntaxStructuralPath child(SyntaxRole role) {
    Objects.requireNonNull(role, "role");
    return new SyntaxStructuralPath(value + "." + role.pathSegment());
  }

  SyntaxStructuralPath element(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("A syntax structural index cannot be negative");
    }
    return new SyntaxStructuralPath(value + "." + index);
  }
}
