package org.aquilagraph.model;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record InputSnapshot(
    Path sourceRoot,
    String digestAlgorithm,
    String snapshotDigest,
    List<SourceFile> sourceFiles) {
  public InputSnapshot {
    sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot").toAbsolutePath().normalize();
    Objects.requireNonNull(digestAlgorithm, "digestAlgorithm");
    Objects.requireNonNull(snapshotDigest, "snapshotDigest");
    sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
    var keys = new HashSet<String>();
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

  public int sourceFileCount() {
    return sourceFiles.size();
  }
}
