package org.aquilagraph.frontend;

import java.util.Objects;

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

  SyntaxStructuralPath child(String role) {
    Objects.requireNonNull(role, "role");
    if (role.isBlank() || role.indexOf('.') >= 0) {
      throw new IllegalArgumentException("A syntax structural role must be one non-blank path segment");
    }
    return new SyntaxStructuralPath(value + "." + role);
  }

  SyntaxStructuralPath element(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("A syntax structural index cannot be negative");
    }
    return new SyntaxStructuralPath(value + "." + index);
  }
}
