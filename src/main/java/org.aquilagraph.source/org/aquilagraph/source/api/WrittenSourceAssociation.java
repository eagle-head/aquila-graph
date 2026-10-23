package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Exact parsed or explicitly grammar-derived provenance of one written source occurrence.
 *
 * @param nodeKey
 *          exact retained source occurrence identity
 * @param sourceFileKey
 *          captured source that owns the occurrence
 * @param ownerPath
 *          original parsed owner or the distinct derived occurrence address
 * @param sourceKind
 *          exact compiler category or explicitly derived written category
 * @param origin
 *          available original range in the same captured source
 * @param normalizedExpression
 *          explicit grammar-derived correspondence, absent for exact parsed occurrences
 */
public record WrittenSourceAssociation(String nodeKey, String sourceFileKey, SourceOwnerPath ownerPath, JavaSourceKind sourceKind,
    Optional<SourceSpan> origin, Optional<NormalizedExpressionCorrespondence> normalizedExpression) {
  /**
   * Copies an exact parsed occurrence without normalized-region derivation.
   *
   * @param nodeKey
   *          retained occurrence
   * @param sourceFileKey
   *          captured source
   * @param ownerPath
   *          original parsed owner
   * @param sourceKind
   *          original category
   * @param origin
   *          original range
   */
  public WrittenSourceAssociation(String nodeKey, String sourceFileKey, SourceOwnerPath ownerPath, JavaSourceKind sourceKind,
      Optional<SourceSpan> origin) {
    this(nodeKey, sourceFileKey, ownerPath, sourceKind, origin, Optional.empty());
  }
  /** Copies source ownership and validates any explicit grammar-derived topology. */
  public WrittenSourceAssociation {
    Objects.requireNonNull(nodeKey, "nodeKey");
    Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    Objects.requireNonNull(ownerPath, "ownerPath");
    Objects.requireNonNull(sourceKind, "sourceKind");
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(normalizedExpression, "normalizedExpression");
    if (nodeKey.isBlank() || sourceFileKey.isBlank() || origin.filter(value -> !value.sourceFileKey().equals(sourceFileKey)).isPresent()) {
      throw new IllegalArgumentException("A written source association requires an exact captured source and parsed owner path");
    }
    if (normalizedExpression.isPresent()) {
      var correspondence = normalizedExpression.orElseThrow();
      var occurrence = correspondence.region().occurrences().stream().filter(value -> value.path().equals(correspondence.occurrencePath()))
          .findFirst().orElseThrow();
      if (!ownerPath.equals(correspondence.writtenOwner()) || !sourceFileKey.equals(correspondence.region().origin().sourceFileKey())
          || sourceKind != occurrence.sourceKind() || !origin.equals(Optional.of(occurrence.origin()))) {
        throw new IllegalArgumentException("Derived source ownership requires its exact written occurrence and range");
      }
    }
  }
}
