package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Original parsed owner topology copied alongside one actual source occurrence.
 *
 * @param nodeKey
 *          exact retained source occurrence identity
 * @param sourceFileKey
 *          captured source that owns the occurrence
 * @param ownerPath
 *          original parsed owner topology
 * @param sourceKind
 *          original public source-tree category
 * @param origin
 *          available original range in the same captured source
 */
public record WrittenSourceAssociation(String nodeKey, String sourceFileKey, SourceOwnerPath ownerPath, JavaSourceKind sourceKind,
    Optional<SourceSpan> origin) {
  /** Copies exact source ownership, topology, kind, and available range. */
  public WrittenSourceAssociation {
    Objects.requireNonNull(nodeKey, "nodeKey");
    Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    Objects.requireNonNull(ownerPath, "ownerPath");
    Objects.requireNonNull(sourceKind, "sourceKind");
    Objects.requireNonNull(origin, "origin");
    if (nodeKey.isBlank() || sourceFileKey.isBlank() || origin.filter(value -> !value.sourceFileKey().equals(sourceFileKey)).isPresent()) {
      throw new IllegalArgumentException("A written source association requires an exact captured source and parsed owner path");
    }
  }
}
