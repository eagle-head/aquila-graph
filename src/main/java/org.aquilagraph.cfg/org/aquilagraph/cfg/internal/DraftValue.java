package org.aquilagraph.cfg.internal;

import org.aquilagraph.cfg.api.ValueKind;

import org.aquilagraph.source.api.JavaTypeIdentity;

import java.util.Objects;
import java.util.Optional;

/**
 * Value owned by a method control-flow graph.
 *
 * @param valueId
 *          stable value identifier
 * @param graphId
 *          owning graph identifier
 * @param kind
 *          value origin or role
 * @param displayName
 *          optional source-facing name
 * @param literal
 *          optional literal representation
 * @param staticType
 *          optional compiler-resolved type identity
 */
record DraftValue(String valueId, String graphId, ValueKind kind, Optional<String> displayName, Optional<String> literal,
    Optional<JavaTypeIdentity> staticType) {
  /** Validates value identity, ownership, and optional attributes. */
  public DraftValue {
    Objects.requireNonNull(valueId, "valueId");
    Objects.requireNonNull(graphId, "graphId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(literal, "literal");
    Objects.requireNonNull(staticType, "staticType");
  }
}
