package org.aquilagraph.syntax;

import java.util.Objects;

/** Shared validation for scalar syntax evidence. */
final class SyntaxValueSupport {
  private SyntaxValueSupport() {
  }

  static void requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }

  static void requireName(String value) {
    requireText(value, "name");
  }
}
