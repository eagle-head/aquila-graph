package org.aquilagraph.model;

import java.util.Objects;

/**
 * Stable compiler-derived identity of a Java type.
 *
 * @param category
 *          compiler-independent type category
 * @param displayName
 *          compiler-reported type including applicable type arguments
 * @param erasedName
 *          compiler-reported erasure used for nominal identity
 */
public record JavaTypeIdentity(JavaTypeCategory category, String displayName, String erasedName) {
  /** Validates the type identity. */
  public JavaTypeIdentity {
    Objects.requireNonNull(category, "category");
    requireText(displayName, "displayName");
    requireText(erasedName, "erasedName");
  }

  /**
   * Returns the stable content identity used by persisted graph properties.
   *
   * @return stable type key
   */
  public String typeKey() {
    return StableId.sha256("java-type", category.name(), displayName, erasedName);
  }

  private static void requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }
}
