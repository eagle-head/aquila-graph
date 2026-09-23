package org.aquilagraph.syntax;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

  static List<SyntaxValue.QualifiedName> copyProviders(List<SyntaxValue.QualifiedName> providers) {
    var copy = List.copyOf(Objects.requireNonNull(providers, "providers"));
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("A provides directive requires at least one provider");
    }
    return copy;
  }

  static Optional<List<SyntaxValue.QualifiedName>> copyTargets(Optional<List<SyntaxValue.QualifiedName>> targets) {
    return Objects.requireNonNull(targets, "targets").map(names -> {
      var copy = List.copyOf(names);
      if (copy.isEmpty()) {
        throw new IllegalArgumentException("A qualified module directive requires at least one target");
      }
      return copy;
    });
  }
}
