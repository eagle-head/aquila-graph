package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Compiler-derived reference to a Java type declaration or type variable.
 *
 * @param identity
 *          compiler-derived type identity
 * @param declarationKey
 *          canonical declaration identity when the compiler exposes one
 */
public record JavaTypeReference(JavaTypeIdentity identity, Optional<String> declarationKey) {
  /** Validates the type reference. */
  public JavaTypeReference {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(declarationKey, "declarationKey");
    declarationKey.ifPresent(key -> requireText(key, "declarationKey"));
  }

  private static void requireText(String value, String name) {
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }
}
