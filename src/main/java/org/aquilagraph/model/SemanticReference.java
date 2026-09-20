package org.aquilagraph.model;

import java.util.Objects;
import java.util.Optional;

public record SemanticReference(
    ReferenceKind referenceKind,
    ResolutionStatus status,
    Optional<String> symbolKey,
    Optional<String> typeName) {
  public SemanticReference {
    Objects.requireNonNull(referenceKind, "referenceKind");
    Objects.requireNonNull(status, "status");
    symbolKey = Objects.requireNonNull(symbolKey, "symbolKey");
    typeName = Objects.requireNonNull(typeName, "typeName");
  }
}
