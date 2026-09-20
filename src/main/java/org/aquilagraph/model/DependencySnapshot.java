package org.aquilagraph.model;

import java.nio.file.Path;
import java.util.Objects;

public record DependencySnapshot(
    Path location,
    DependencyKind kind,
    long byteCount,
    int entryCount,
    String fingerprint) {
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
