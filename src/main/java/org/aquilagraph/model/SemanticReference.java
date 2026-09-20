package org.aquilagraph.model;

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
 */
public record SemanticReference(ReferenceKind referenceKind, ResolutionStatus status, Optional<String> symbolKey, Optional<String> typeName,
    List<String> typeAlternativeNames, List<String> assignableToTypeNames) {
  /** Validates resolution consistency and optional semantic attributes. */
  public SemanticReference {
    Objects.requireNonNull(referenceKind, "referenceKind");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(symbolKey, "symbolKey");
    Objects.requireNonNull(typeName, "typeName");
    typeAlternativeNames = List.copyOf(Objects.requireNonNull(typeAlternativeNames, "typeAlternativeNames"));
    assignableToTypeNames = List.copyOf(Objects.requireNonNull(assignableToTypeNames, "assignableToTypeNames"));
    if (typeName.isEmpty() && (!typeAlternativeNames.isEmpty() || !assignableToTypeNames.isEmpty())) {
      throw new IllegalArgumentException("Type alternatives and assignability require a compiler-reported type");
    }
  }
}
