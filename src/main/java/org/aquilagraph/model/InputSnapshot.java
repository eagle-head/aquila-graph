package org.aquilagraph.model;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, content-addressed set of source files discovered below a root.
 *
 * @param sourceRoot
 *          normalized absolute source root
 * @param digestAlgorithm
 *          source content digest algorithm
 * @param snapshotDigest
 *          stable snapshot digest
 * @param sourceFiles
 *          files ordered by normalized relative path
 */
public record InputSnapshot(Path sourceRoot, String digestAlgorithm, String snapshotDigest, List<SourceFile> sourceFiles) {
  /** Validates snapshot identity, uniqueness, and deterministic ordering. */
  public InputSnapshot {
    sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot").toAbsolutePath().normalize();
    Objects.requireNonNull(digestAlgorithm, "digestAlgorithm");
    Objects.requireNonNull(snapshotDigest, "snapshotDigest");
    sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
    var keys = new HashSet<String>();
    @Nullable
    Path previous = null;
    for (var sourceFile : sourceFiles) {
      Objects.requireNonNull(sourceFile, "sourceFile");
      if (!keys.add(sourceFile.sourceFileKey())) {
        throw new IllegalArgumentException("Source-file keys must be unique");
      }
      if (previous != null && previous.compareTo(sourceFile.relativePath()) >= 0) {
        throw new IllegalArgumentException("Source files must have deterministic relative-path order");
      }
      previous = sourceFile.relativePath();
    }
  }

  /**
   * Returns the number of captured source files.
   *
   * @return source-file count
   */
  public int sourceFileCount() {
    return sourceFiles.size();
  }
}
