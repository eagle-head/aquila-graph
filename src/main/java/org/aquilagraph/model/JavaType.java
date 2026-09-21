package org.aquilagraph.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Translated Java type declaration.
 *
 * @param typeKey
 *          stable type identifier
 * @param qualifiedName
 *          compiler-reported or derived qualified name
 * @param kind
 *          declaration category
 * @param modifiers
 *          compiler-derived declaration modifiers
 * @param enclosingType
 *          immediately enclosing member type, when present
 * @param directSuperclass
 *          compiler-resolved direct superclass, when present
 * @param directInterfaces
 *          directly implemented or extended interfaces in declaration order
 * @param permittedSubclasses
 *          compiler-resolved permitted subclasses in declaration order
 * @param typeParameters
 *          type parameter declarations in source order
 * @param sourceDeclared
 *          whether the declaration has a source origin
 * @param fields
 *          declared fields and enum constants
 * @param methods
 *          declared methods
 * @param origins
 *          declaration source locations
 */
public record JavaType(String typeKey, String qualifiedName, JavaTypeKind kind, Set<JavaTypeModifier> modifiers,
    Optional<JavaTypeReference> enclosingType, Optional<JavaTypeReference> directSuperclass, List<JavaTypeReference> directInterfaces,
    List<JavaTypeReference> permittedSubclasses, List<JavaTypeParameter> typeParameters, boolean sourceDeclared, List<JavaField> fields,
    List<JavaMethod> methods, List<SourceSpan> origins) {
  /** Validates and defensively copies type data. */
  public JavaType {
    requireText(typeKey, "typeKey");
    requireText(qualifiedName, "qualifiedName");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(modifiers, "modifiers");
    var copiedModifiers = modifiers.isEmpty() ? EnumSet.noneOf(JavaTypeModifier.class) : EnumSet.copyOf(modifiers);
    modifiers = Collections.unmodifiableSet(copiedModifiers);
    Objects.requireNonNull(enclosingType, "enclosingType");
    Objects.requireNonNull(directSuperclass, "directSuperclass");
    directInterfaces = List.copyOf(Objects.requireNonNull(directInterfaces, "directInterfaces"));
    directInterfaces.forEach(reference -> Objects.requireNonNull(reference, "directInterfaces entry"));
    permittedSubclasses = List.copyOf(Objects.requireNonNull(permittedSubclasses, "permittedSubclasses"));
    permittedSubclasses.forEach(reference -> Objects.requireNonNull(reference, "permittedSubclasses entry"));
    typeParameters = List.copyOf(Objects.requireNonNull(typeParameters, "typeParameters"));
    fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
    methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    if (sourceDeclared != !origins.isEmpty()) {
      throw new IllegalArgumentException("Type source declaration state must match its source origin");
    }
    var keys = new HashSet<String>();
    for (var parameter : typeParameters) {
      if (!parameter.ownerTypeKey().equals(typeKey) || !keys.add(parameter.typeParameterKey())) {
        throw new IllegalArgumentException("Type parameters must be uniquely owned by their declaring type");
      }
    }
    keys.clear();
    for (var field : fields) {
      if (!field.ownerTypeKey().equals(typeKey) || !keys.add(field.fieldKey())) {
        throw new IllegalArgumentException("Fields must be uniquely owned by their declaring type");
      }
    }
    keys.clear();
    for (var method : methods) {
      if (!method.ownerTypeKey().equals(typeKey) || !keys.add(method.methodKey())) {
        throw new IllegalArgumentException("Methods must be uniquely owned by their declaring type");
      }
    }
  }

  private static void requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }
}
