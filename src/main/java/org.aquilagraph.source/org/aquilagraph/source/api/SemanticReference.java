package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Compiler-derived reference from syntax to a Java symbol or type.
 *
 * @param referenceKind
 *          referenced symbol category
 * @param status
 *          resolution outcome
 * @param symbolKey
 *          optional stable symbol identifier
 * @param typeName
 *          optional compiler-reported type
 * @param typeAlternativeNames
 *          compiler-reported alternatives of a union type, or the single declared type
 * @param assignableToTypeNames
 *          compiler-reported type and its transitive supertypes
 * @param declarationIdentity
 *          copied canonical type target under the module-qualified policy
 */
public record SemanticReference(ReferenceKind referenceKind, ResolutionStatus status, Optional<String> symbolKey, Optional<String> typeName,
    List<String> typeAlternativeNames, List<String> assignableToTypeNames, Optional<JavaTypeDeclarationIdentity> declarationIdentity) {
  /**
   * Creates a reference using the historical identity representation.
   *
   * @param referenceKind
   *          referenced category
   * @param status
   *          resolution outcome
   * @param symbolKey
   *          declaration key
   * @param typeName
   *          descriptive type
   * @param typeAlternativeNames
   *          union alternatives
   * @param assignableToTypeNames
   *          transitive supertypes
   */
  public SemanticReference(ReferenceKind referenceKind, ResolutionStatus status, Optional<String> symbolKey, Optional<String> typeName,
      List<String> typeAlternativeNames, List<String> assignableToTypeNames) {
    this(referenceKind, status, symbolKey, typeName, typeAlternativeNames, assignableToTypeNames, Optional.empty());
  }
  /** Validates resolution consistency and optional semantic attributes. */
  public SemanticReference {
    Objects.requireNonNull(referenceKind, "referenceKind");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(symbolKey, "symbolKey");
    Objects.requireNonNull(typeName, "typeName");
    Objects.requireNonNull(declarationIdentity, "declarationIdentity");
    if (declarationIdentity.isPresent() && (referenceKind != ReferenceKind.TYPE
        || !symbolKey.equals(declarationIdentity.map(JavaTypeDeclarationIdentity::declarationKey)))) {
      throw new IllegalArgumentException("A typed target must agree with its TYPE reference declaration key");
    }
    typeAlternativeNames = List.copyOf(Objects.requireNonNull(typeAlternativeNames, "typeAlternativeNames"));
    assignableToTypeNames = List.copyOf(Objects.requireNonNull(assignableToTypeNames, "assignableToTypeNames"));
    symbolKey.ifPresent(key -> {
      if (key.isBlank()) {
        throw new IllegalArgumentException("A semantic reference identity cannot be blank");
      }
    });
    if (status == ResolutionStatus.RESOLVED && symbolKey.isEmpty()) {
      throw new IllegalArgumentException("A resolved semantic reference requires a declaration identity");
    }
    if (typeName.isEmpty() && (!typeAlternativeNames.isEmpty() || !assignableToTypeNames.isEmpty())) {
      throw new IllegalArgumentException("Type alternatives and assignability require a compiler-reported type");
    }
  }
}
