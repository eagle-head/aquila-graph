package org.aquilagraph.source.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Durable declaration of a Java field or enum constant.
 *
 * @param fieldKey
 *          canonical declaration identity
 * @param ownerTypeKey
 *          canonical identity of the declaring type
 * @param name
 *          source declaration name
 * @param staticType
 *          compiler-derived declared type
 * @param modifiers
 *          source declaration modifiers
 * @param enumConstant
 *          whether the declaration is an enum constant
 * @param sourceDeclared
 *          whether the declaration has a direct source origin
 * @param origins
 *          declaration source locations
 */
public record JavaField(String fieldKey, String ownerTypeKey, String name, JavaTypeIdentity staticType, Set<String> modifiers,
    boolean enumConstant, boolean sourceDeclared, List<SourceSpan> origins) {
  /** Validates declaration ownership and defensively copies field data. */
  public JavaField {
    requireText(fieldKey, "fieldKey");
    requireText(ownerTypeKey, "ownerTypeKey");
    requireText(name, "name");
    Objects.requireNonNull(staticType, "staticType");
    modifiers = Collections.unmodifiableSet(new TreeSet<>(Objects.requireNonNull(modifiers, "modifiers")));
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    if (sourceDeclared != !origins.isEmpty()) {
      throw new IllegalArgumentException("Field source declaration state must match its source origin");
    }
  }

  private static void requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }
}
