package org.aquilagraph.model;

import java.util.Objects;
import java.util.Optional;

public record FlowValue(
    String valueId,
    String graphId,
    ValueKind kind,
    Optional<String> displayName,
    Optional<String> literal,
    Optional<String> staticType) {
  public FlowValue {
    Objects.requireNonNull(valueId, "valueId");
    Objects.requireNonNull(graphId, "graphId");
    Objects.requireNonNull(kind, "kind");
    displayName = Objects.requireNonNull(displayName, "displayName");
    literal = Objects.requireNonNull(literal, "literal");
    staticType = Objects.requireNonNull(staticType, "staticType");
  }
}
