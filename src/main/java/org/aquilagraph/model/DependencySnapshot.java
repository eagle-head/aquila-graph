package org.aquilagraph.model;

import java.nio.file.Path;
import java.util.Objects;

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
 */
public record DependencySnapshot(Path location, DependencyKind kind, long byteCount, int entryCount, String fingerprint) {
  /** Validates and normalizes dependency metadata. */
  public DependencySnapshot {
    location = Objects.requireNonNull(location, "location").toAbsolutePath().normalize();
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(fingerprint, "fingerprint");
    if (byteCount < 0) {
      throw new IllegalArgumentException("Dependency byte count cannot be negative");
    }
    if (entryCount < 0) {
      throw new IllegalArgumentException("Dependency entry count cannot be negative");
    }
    if (kind == DependencyKind.MISSING && (byteCount != 0 || entryCount != 0)) {
      throw new IllegalArgumentException("A missing dependency cannot contain entries or bytes");
    }
    if (kind == DependencyKind.REGULAR_FILE && entryCount != 1) {
      throw new IllegalArgumentException("A regular-file dependency has exactly one entry");
    }
  }
}
