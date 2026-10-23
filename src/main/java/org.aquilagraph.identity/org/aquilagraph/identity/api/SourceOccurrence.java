package org.aquilagraph.identity.api;

import java.util.Objects;

/**
 * Exact source result and local occurrence identity, without inferred ownership.
 *
 * @param astIdentity
 *          qualified source result
 * @param nodeKey
 *          source-local occurrence key
 */
public record SourceOccurrence(AstIdentity astIdentity, String nodeKey) {
  /** Requires a qualified result and nonblank local identity. */
  public SourceOccurrence {
    Objects.requireNonNull(astIdentity, "astIdentity");
    if (Objects.requireNonNull(nodeKey, "nodeKey").isBlank()) {
      throw new IllegalArgumentException("A source occurrence requires a nonblank local key");
    }
  }
}
