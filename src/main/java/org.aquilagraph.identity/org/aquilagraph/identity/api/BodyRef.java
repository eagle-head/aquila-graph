package org.aquilagraph.identity.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Exact source-result association for one canonical method body.
 *
 * @param astIdentity
 *          exact admitted result identity
 * @param methodKey
 *          canonical method identity
 * @param sourceOccurrenceKey
 *          written source body occurrence
 * @param nonwrittenProvenance
 *          explicit origin when no written occurrence exists
 */
public record BodyRef(AstIdentity astIdentity, String methodKey, Optional<String> sourceOccurrenceKey,
    Optional<NonwrittenBodyProvenance> nonwrittenProvenance) {
  /** Requires exactly one written or nonwritten body origin. */
  public BodyRef {
    Objects.requireNonNull(astIdentity, "astIdentity");
    if (Objects.requireNonNull(methodKey, "methodKey").isBlank()) {
      throw new IllegalArgumentException("Body method identity cannot be blank");
    }
    Objects.requireNonNull(sourceOccurrenceKey, "sourceOccurrenceKey");
    Objects.requireNonNull(nonwrittenProvenance, "nonwrittenProvenance");
    if (sourceOccurrenceKey.isPresent() == nonwrittenProvenance.isPresent()) {
      throw new IllegalArgumentException("A body requires exactly one written occurrence or nonwritten origin");
    }
    if (sourceOccurrenceKey.isPresent() && sourceOccurrenceKey.orElseThrow().isBlank()) {
      throw new IllegalArgumentException("Written body occurrence identity cannot be blank");
    }
  }
}
