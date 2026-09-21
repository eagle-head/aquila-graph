package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;

/**
 * Durable type parameter declared by a Java type.
 *
 * @param typeParameterKey
 *          stable parameter declaration identity
 * @param ownerTypeKey
 *          canonical identity of the declaring type
 * @param name
 *          source declaration name
 * @param bounds
 *          compiler-resolved bounds in declaration order, including an implicit {@code Object} bound
 * @param sourceDeclared
 *          whether the declaration has a direct source origin
 * @param origins
 *          declaration source locations
 */
public record JavaTypeParameter(String typeParameterKey, String ownerTypeKey, String name, List<JavaTypeReference> bounds,
    boolean sourceDeclared, List<SourceSpan> origins) {
  /** Validates ownership and defensively copies parameter data. */
  public JavaTypeParameter {
    requireText(typeParameterKey, "typeParameterKey");
    requireText(ownerTypeKey, "ownerTypeKey");
    requireText(name, "name");
    bounds = List.copyOf(Objects.requireNonNull(bounds, "bounds"));
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    bounds.forEach(bound -> Objects.requireNonNull(bound, "bound"));
    if (bounds.isEmpty()) {
      throw new IllegalArgumentException("A Java type parameter requires at least one compiler-resolved bound");
    }
    if (sourceDeclared != !origins.isEmpty()) {
      throw new IllegalArgumentException("Type parameter source declaration state must match its source origin");
    }
  }

  private static void requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }
}
