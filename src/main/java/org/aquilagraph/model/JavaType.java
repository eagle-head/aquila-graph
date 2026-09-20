package org.aquilagraph.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Translated Java type declaration.
 *
 * @param typeKey
 *          stable type identifier
 * @param qualifiedName
 *          compiler-reported or derived qualified name
 * @param kind
 *          declaration category
 * @param sourceDeclared
 *          whether the declaration has a source origin
 * @param methods
 *          declared methods
 * @param origins
 *          declaration source locations
 */
public record JavaType(String typeKey, String qualifiedName, JavaTypeKind kind, boolean sourceDeclared, List<JavaMethod> methods,
    List<SourceSpan> origins) {
  /** Validates and defensively copies type data. */
  public JavaType {
    Objects.requireNonNull(typeKey, "typeKey");
    Objects.requireNonNull(qualifiedName, "qualifiedName");
    Objects.requireNonNull(kind, "kind");
    methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    var keys = new HashSet<String>();
    for (var method : methods) {
      if (!method.ownerTypeKey().equals(typeKey) || !keys.add(method.methodKey())) {
        throw new IllegalArgumentException("Methods must be uniquely owned by their declaring type");
      }
    }
  }
}
