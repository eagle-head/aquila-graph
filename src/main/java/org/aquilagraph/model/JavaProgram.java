package org.aquilagraph.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable collection of translated top-level and nested Java types.
 *
 * @param types
 *          translated types with unique stable identifiers
 */
public record JavaProgram(List<JavaType> types) {
  /** Validates type identity uniqueness and defensively copies the collection. */
  public JavaProgram {
    types = List.copyOf(Objects.requireNonNull(types, "types"));
    var typeKeys = new HashSet<String>();
    var methodKeys = new HashSet<String>();
    for (var type : types) {
      if (!typeKeys.add(Objects.requireNonNull(type, "type").typeKey())) {
        throw new IllegalArgumentException("Java type keys must be unique");
      }
      for (var method : type.methods()) {
        if (!methodKeys.add(method.methodKey())) {
          throw new IllegalArgumentException("Java method keys must be unique across the program");
        }
      }
    }
  }

  /**
   * Returns every method in deterministic type and declaration order.
   *
   * @return flattened method list
   */
  public List<JavaMethod> methods() {
    return types.stream().flatMap(type -> type.methods().stream()).toList();
  }

  /**
   * Returns the number of declared methods without materializing the flattened method list.
   *
   * @return declared method count
   */
  public int methodCount() {
    return types.stream().mapToInt(type -> type.methods().size()).sum();
  }
}
