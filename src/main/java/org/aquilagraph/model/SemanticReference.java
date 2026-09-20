package org.aquilagraph.model;

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
 */
public record SemanticReference(ReferenceKind referenceKind, ResolutionStatus status, Optional<String> symbolKey,
    Optional<String> typeName) {
  /** Validates resolution consistency and optional semantic attributes. */
  public SemanticReference {
    Objects.requireNonNull(referenceKind, "referenceKind");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(symbolKey, "symbolKey");
    Objects.requireNonNull(typeName, "typeName");
  }
}
