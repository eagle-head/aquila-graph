package org.aquilagraph.source.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Translated Java type declaration.
 *
 * @param typeKey
 *          stable type identifier
 * @param qualifiedName
 *          compiler-reported qualified name, empty for local and anonymous types
 * @param kind
 *          declaration category
 * @param modifiers
 *          compiler-derived declaration modifiers
 * @param enclosingType
 *          nearest enclosing type, independent of lexical declaration ownership
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
 * @param recordComponents
 *          written record-header components in declaration order
 * @param methods
 *          declared methods
 * @param synthesizedDeclarations
 *          compiler-exposed direct members without a written declaration or detailed member aggregate
 * @param displayName
 *          copied compiler declaration display, independent of written name and canonical key
 * @param staticType
 *          compiler-selected declaration type identity
 * @param nesting
 *          lexical nesting and declaration owner
 * @param origins
 *          declaration source locations
 */
public record JavaType(String typeKey, String qualifiedName, JavaTypeKind kind, Set<JavaTypeModifier> modifiers,
    Optional<JavaTypeReference> enclosingType, Optional<JavaTypeReference> directSuperclass, List<JavaTypeReference> directInterfaces,
    List<JavaTypeReference> permittedSubclasses, List<JavaTypeParameter> typeParameters, boolean sourceDeclared, List<JavaField> fields,
    List<JavaRecordComponent> recordComponents, List<JavaMethod> methods, List<JavaSynthesizedDeclaration> synthesizedDeclarations,
    List<SourceSpan> origins, JavaTypeNesting nesting, JavaTypeIdentity staticType, String displayName) {
  /**
   * Creates an earlier-profile top-level or member declaration.
   *
   * @param typeKey
   *          stable type identifier
   * @param qualifiedName
   *          compiler-reported or derived name
   * @param kind
   *          declaration category
   * @param modifiers
   *          effective modifiers
   * @param enclosingType
   *          enclosing member type
   * @param directSuperclass
   *          direct superclass
   * @param directInterfaces
   *          direct interfaces
   * @param permittedSubclasses
   *          permitted subclasses
   * @param typeParameters
   *          type parameters
   * @param sourceDeclared
   *          source provenance
   * @param fields
   *          fields
   * @param recordComponents
   *          record components
   * @param methods
   *          methods
   * @param synthesizedDeclarations
   *          nonwritten members
   * @param origins
   *          source locations
   */
  public JavaType(String typeKey, String qualifiedName, JavaTypeKind kind, Set<JavaTypeModifier> modifiers,
      Optional<JavaTypeReference> enclosingType, Optional<JavaTypeReference> directSuperclass, List<JavaTypeReference> directInterfaces,
      List<JavaTypeReference> permittedSubclasses, List<JavaTypeParameter> typeParameters, boolean sourceDeclared, List<JavaField> fields,
      List<JavaRecordComponent> recordComponents, List<JavaMethod> methods, List<JavaSynthesizedDeclaration> synthesizedDeclarations,
      List<SourceSpan> origins) {
    this(typeKey, qualifiedName, kind, modifiers, enclosingType, directSuperclass, directInterfaces, permittedSubclasses, typeParameters,
        sourceDeclared, fields, recordComponents, methods, synthesizedDeclarations, origins, historicalNesting(enclosingType),
        declarationIdentity(typeKey, qualifiedName, modifiers, enclosingType, typeParameters), qualifiedName);
  }

  private static JavaTypeNesting historicalNesting(Optional<JavaTypeReference> enclosingType) {
    var owner = Objects.requireNonNull(enclosingType, "enclosingType").flatMap(JavaTypeReference::declarationKey);
    return new JavaTypeNesting(owner.isEmpty() ? JavaTypeNesting.Kind.TOP_LEVEL : JavaTypeNesting.Kind.MEMBER, owner);
  }

  private static JavaTypeIdentity declarationIdentity(String typeKey, String qualifiedName, Set<JavaTypeModifier> modifiers,
      Optional<JavaTypeReference> enclosingType, List<JavaTypeParameter> typeParameters) {
    requireText(typeKey, "typeKey");
    requireText(qualifiedName, "qualifiedName");
    Objects.requireNonNull(modifiers, "modifiers");
    Objects.requireNonNull(typeParameters, "typeParameters");
    var declarationName = enclosingType.isPresent() && !modifiers.contains(JavaTypeModifier.STATIC)
        && qualifiedName.startsWith(enclosingType.orElseThrow().identity().erasedName() + '.')
            ? enclosingType.orElseThrow().identity().displayName() + '.' + qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
            : qualifiedName;
    var arguments = typeParameters.isEmpty()
        ? ""
        : typeParameters.stream().map(parameter -> Objects.requireNonNull(parameter, "typeParameters entry").name())
            .collect(Collectors.joining(",", "<", ">"));
    return new JavaTypeIdentity(JavaTypeCategory.DECLARED, declarationName + arguments, qualifiedName);
  }
  /** Validates and defensively copies type data. */
  public JavaType {
    requireText(typeKey, "typeKey");
    Objects.requireNonNull(nesting, "nesting");
    Objects.requireNonNull(staticType, "staticType");
    if (staticType.category() != JavaTypeCategory.DECLARED && staticType.category() != JavaTypeCategory.ERROR) {
      throw new IllegalArgumentException("A Java type declaration requires its selected declared type");
    }
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(qualifiedName, "qualifiedName");
    if (nesting.kind() != JavaTypeNesting.Kind.ANONYMOUS && nesting.kind() != JavaTypeNesting.Kind.LOCAL) {
      requireText(qualifiedName, "qualifiedName");
    }
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
    recordComponents = List.copyOf(Objects.requireNonNull(recordComponents, "recordComponents"));
    methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
    synthesizedDeclarations = List.copyOf(Objects.requireNonNull(synthesizedDeclarations, "synthesizedDeclarations"));
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
    for (var field : fields) {
      if (!field.ownerTypeKey().equals(typeKey) || !keys.add(field.fieldKey())) {
        throw new IllegalArgumentException("Fields must be uniquely owned by their declaring type");
      }
    }
    if (kind != JavaTypeKind.RECORD && !recordComponents.isEmpty()) {
      throw new IllegalArgumentException("Only a record can declare record components");
    }
    for (var position = 0; position < recordComponents.size(); position++) {
      var component = recordComponents.get(position);
      if (!component.ownerTypeKey().equals(typeKey) || component.ordinal() != position || !keys.add(component.componentKey())) {
        throw new IllegalArgumentException("Record components must be ordered and uniquely owned by their record");
      }
    }
    for (var method : methods) {
      if (!method.ownerTypeKey().equals(typeKey) || !keys.add(method.methodKey())) {
        throw new IllegalArgumentException("Methods must be uniquely owned by their declaring type");
      }
    }
    for (var declaration : synthesizedDeclarations) {
      if (!declaration.ownerTypeKey().equals(typeKey) || !keys.add(declaration.declarationKey())) {
        throw new IllegalArgumentException("Synthesized declarations must be uniquely owned outside detailed members");
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
