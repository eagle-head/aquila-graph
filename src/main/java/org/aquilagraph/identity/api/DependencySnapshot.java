package org.aquilagraph.identity.api;

import java.nio.file.Path;
import java.util.Objects;
import java.util.List;

/**
 * Captured identity and size metadata for one compiler dependency entry.
 *
 * @param location
 *          normalized dependency location
 * @param kind
 *          dependency entry form
 * @param byteCount
 *          captured content size
 * @param entryCount
 *          number of files represented
 * @param fingerprint
 *          content fingerprint
 * @param semantics
 *          detached interpretation for the configured role
 */
public record DependencySnapshot(Path location, DependencyKind kind, long byteCount, int entryCount, String fingerprint,
    DependencySemantics semantics) {
  /**
   * Constructs historical metadata without claiming observed module semantics.
   *
   * @param location
   *          dependency location
   * @param kind
   *          physical kind
   * @param byteCount
   *          captured bytes
   * @param entryCount
   *          captured files
   * @param fingerprint
   *          supplied physical fingerprint
   */
  public DependencySnapshot(Path location, DependencyKind kind, long byteCount, int entryCount, String fingerprint) {
    this(location, kind, byteCount, entryCount, fingerprint, new DependencySemantics(DependencySemantics.State.UNOBSERVED, List.of()));
  }

  /** Validates and normalizes dependency metadata. */
  public DependencySnapshot {
    location = Objects.requireNonNull(location, "location").toAbsolutePath().normalize();
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(fingerprint, "fingerprint");
    Objects.requireNonNull(semantics, "semantics");
    if ((semantics.state() == DependencySemantics.State.MISSING && kind != DependencyKind.MISSING) || (kind == DependencyKind.MISSING
        && (semantics.state() == DependencySemantics.State.MODULES || semantics.state() == DependencySemantics.State.INVALID))) {
      throw new IllegalArgumentException("Module interpretation must agree with physical absence");
    }
    if (kind == DependencyKind.REGULAR_FILE && semantics.modules().stream().anyMatch(module -> !".".equals(module.member()))) {
      throw new IllegalArgumentException("A direct archive module must be associated with its configured artifact");
    }
    if (kind == DependencyKind.DIRECTORY
        && semantics.modules().stream().anyMatch(module -> ".".equals(module.member()) && module.descriptorName().isEmpty())) {
      throw new IllegalArgumentException("A direct directory module requires an explicit descriptor");
    }
    if (byteCount < 0) {
      throw new IllegalArgumentException("Dependency byte count cannot be negative");
    }
    if (entryCount < semantics.modules().size()) {
      throw new IllegalArgumentException("Dependency entry count must be nonnegative and cover the observed module artifacts");
    }
    if (kind == DependencyKind.MISSING && (byteCount != 0 || entryCount != 0)) {
      throw new IllegalArgumentException("A missing dependency cannot contain entries or bytes");
    }
    if (kind == DependencyKind.REGULAR_FILE && entryCount != 1) {
      throw new IllegalArgumentException("A regular-file dependency has exactly one entry");
    }
  }
}
