package org.aquilagraph.cfg.api;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.BodyRef;
import org.aquilagraph.identity.api.SourceOccurrence;

/**
 * Exact body ownership and written occurrence or explicit generated operation role.
 *
 * @param body
 *          exact admitted body association
 * @param occurrence
 *          optional owned written source occurrence
 * @param generatedRole
 *          explicit local operation responsibility
 */
public record GraphSourceRef(BodyRef body, Optional<SourceOccurrence> occurrence, String generatedRole) {
  /** Rejects foreign occurrence identity and an absent generated responsibility. */
  public GraphSourceRef {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(occurrence, "occurrence");
    if (Objects.requireNonNull(generatedRole, "generatedRole").isBlank()) {
      throw new IllegalArgumentException("Graph source provenance requires an explicit operation role");
    }
    if (occurrence.isPresent() && !occurrence.orElseThrow().astIdentity().equals(body.astIdentity())) {
      throw new IllegalArgumentException("Graph source occurrence belongs to another AST result");
    }
  }
}
