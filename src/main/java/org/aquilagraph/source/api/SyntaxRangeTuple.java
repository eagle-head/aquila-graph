package org.aquilagraph.source.api;

import java.util.Objects;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Canonical source-range projection for one durable Java source occurrence.
 *
 * @param nodeKey
 *          derived occurrence key
 * @param range
 *          direct half-open source range
 */
public record SyntaxRangeTuple(String nodeKey, SourceSpan range) {
  /** Validates the occurrence key and source range. */
  public SyntaxRangeTuple {
    Objects.requireNonNull(nodeKey, "nodeKey");
    if (nodeKey.isBlank()) {
      throw new IllegalArgumentException("Syntax range node key cannot be blank");
    }
    Objects.requireNonNull(range, "range");
  }
}
