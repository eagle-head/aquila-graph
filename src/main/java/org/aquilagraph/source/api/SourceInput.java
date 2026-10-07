package org.aquilagraph.source.api;

import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.identity.api.InputSnapshot;

/** Explicit source discovery or already captured production input. */
public sealed interface SourceInput {
  /**
   * Source directory requiring capture.
   *
   * @param path
   *          normalized absolute source directory
   */
  record Root(Path path) implements SourceInput {
    /** Normalizes the requested capture location without reading it. */
    public Root {
      path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }
  }

  /**
   * Immutable captured source input requiring no discovery.
   *
   * @param value
   *          captured source values
   */
  record Snapshot(InputSnapshot value) implements SourceInput {
    /** Requires the exact supplied immutable capture. */
    public Snapshot {
      Objects.requireNonNull(value, "value");
    }
  }
}
