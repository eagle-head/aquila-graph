package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * One input-local source obligation captured independently of emitted outcomes.
 *
 * @param address
 *          exact source owner, partition, role, and position
 * @param feature
 *          known policy feature, when present
 * @param origins
 *          copied source spans belonging to the captured source
 * @param writtenValue
 *          independently copied written accessor state, when present
 * @param requiresAttribution
 *          whether the obligation requires a later semantic projection
 */
public record SourceContext(Address address, Optional<LanguageFeature> feature, List<SourceSpan> origins,
    Optional<CoveragePropertyValue> writtenValue, boolean requiresAttribution) {
  /** Validates immutable source-owned context evidence. */
  public SourceContext {
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(feature, "feature");
    origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
    Objects.requireNonNull(writtenValue, "writtenValue");
    if (origins.stream().anyMatch(origin -> !origin.sourceFileKey().equals(address.sourceFileKey()))) {
      throw new IllegalArgumentException("Context origins must belong to the captured source");
    }
  }

  /** Independent evidence partitions in retained producer order. */
  public enum Partition {
    /** Parsed tree encounters. */
    TREE,
    /** Written declaration-header type slots. */
    TYPE,
    /** Recursive written type syntax beneath independently captured declaration and body slots. */
    WRITTEN_TYPE_USE,
    /** Translation-owned policy encounters. */
    TRANSLATION,
    /** Written declaration accessors. */
    WRITTEN_PROPERTY,
    /** Attributed declaration accessors. */
    ATTRIBUTED_PROPERTY
  }

  /**
   * Source owner topology and role-local position; this is not a persisted identity.
   *
   * @param sourceFileKey
   *          captured source identity
   * @param partition
   *          independent evidence partition
   * @param ownerPath
   *          original parsed owner topology
   * @param role
   *          exact context role or accessor identity
   * @param ordinal
   *          nonnegative position within the role
   */
  public record Address(String sourceFileKey, Partition partition, SourceOwnerPath ownerPath, String role, int ordinal) {
    /** Validates the input-local address. */
    public Address {
      Objects.requireNonNull(sourceFileKey, "sourceFileKey");
      Objects.requireNonNull(partition, "partition");
      Objects.requireNonNull(ownerPath, "ownerPath");
      Objects.requireNonNull(role, "role");
      if (sourceFileKey.isBlank() || role.isBlank() || ordinal < 0) {
        throw new IllegalArgumentException("Context addresses require source, owner topology, role and nonnegative positions");
      }
    }
  }
}
