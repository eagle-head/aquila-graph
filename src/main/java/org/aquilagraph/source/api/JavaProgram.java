package org.aquilagraph.source.api;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    Map<String, JavaType> typesByKey = new HashMap<>();
    var typeParameterKeys = new HashSet<String>();
    var fieldKeys = new HashSet<String>();
    var componentKeys = new HashSet<String>();
    var methodKeys = new HashSet<String>();
    var synthesizedKeys = new HashSet<String>();
    var declarationKeys = new HashSet<String>();
    for (var type : types) {
      if (!typeKeys.add(Objects.requireNonNull(type, "type").typeKey())) {
        throw new IllegalArgumentException("Java type keys must be unique");
      }
      typesByKey.put(type.typeKey(), type);
      requireUniqueDeclarationKey(declarationKeys, type.typeKey());
      for (var typeParameter : type.typeParameters()) {
        if (!typeParameterKeys.add(typeParameter.typeParameterKey())) {
          throw new IllegalArgumentException("Java type parameter keys must be unique across the program");
        }
        requireUniqueDeclarationKey(declarationKeys, typeParameter.typeParameterKey());
      }
      for (var field : type.fields()) {
        if (!fieldKeys.add(field.fieldKey())) {
          throw new IllegalArgumentException("Java field keys must be unique across the program");
        }
        requireUniqueDeclarationKey(declarationKeys, field.fieldKey());
      }
      for (var component : type.recordComponents()) {
        if (!componentKeys.add(component.componentKey())) {
          throw new IllegalArgumentException("Java record component keys must be unique across the program");
        }
        requireUniqueDeclarationKey(declarationKeys, component.componentKey());
      }
      for (var method : type.methods()) {
        if (!methodKeys.add(method.methodKey())) {
          throw new IllegalArgumentException("Java method keys must be unique across the program");
        }
        requireUniqueDeclarationKey(declarationKeys, method.methodKey());
      }
      for (var declaration : type.synthesizedDeclarations()) {
        if (!synthesizedKeys.add(declaration.declarationKey())) {
          throw new IllegalArgumentException("Java synthesized declaration keys must be unique across the program");
        }
        requireUniqueDeclarationKey(declarationKeys, declaration.declarationKey());
      }
    }
    validateEnclosingTypes(types, typesByKey);
  }

  private static void requireUniqueDeclarationKey(Set<String> keys, String key) {
    if (!keys.add(key)) {
      throw new IllegalArgumentException("Java declaration keys must be unique across kinds and types");
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

  private static void validateEnclosingTypes(List<JavaType> types, Map<String, JavaType> typesByKey) {
    for (var type : types) {
      validateEnclosingType(type, typesByKey);
    }
  }

  private static void validateEnclosingType(JavaType type, Map<String, JavaType> typesByKey) {
    if (type.enclosingType().isEmpty()) {
      return;
    }
    var enclosingKey = type.enclosingType().orElseThrow().declarationKey().orElseThrow(JavaProgram::missingEnclosingTypeKey);
    var enclosingDeclaration = typesByKey.get(enclosingKey);
    if (enclosingKey.equals(type.typeKey()) || enclosingDeclaration == null
        || !enclosingDeclaration.staticType().equals(type.enclosingType().orElseThrow().identity())) {
      throw invalidEnclosingType();
    }
    var visited = new HashSet<String>();
    var candidate = type;
    while (candidate.enclosingType().isPresent()) {
      if (!visited.add(candidate.typeKey())) {
        throw cyclicTypeOwnership();
      }
      var ownerKey = candidate.enclosingType().orElseThrow().declarationKey().orElseThrow();
      candidate = typesByKey.get(ownerKey);
      if (candidate == null) {
        throw invalidEnclosingType();
      }
    }
  }

  private static IllegalArgumentException missingEnclosingTypeKey() {
    return new IllegalArgumentException("An enclosing type requires a canonical declaration key");
  }

  private static IllegalArgumentException invalidEnclosingType() {
    return new IllegalArgumentException("An enclosing type must identify another declaration in the same program");
  }

  private static IllegalArgumentException cyclicTypeOwnership() {
    return new IllegalArgumentException("Java type ownership cannot contain a cycle");
  }
}
