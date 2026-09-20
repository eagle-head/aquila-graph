package org.aquilagraph.model;

import java.nio.file.Path;
import java.util.Objects;

public record SourceFile(String sourceFileKey, Path relativePath, String contentDigest, String content) {
  public SourceFile {
    Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    relativePath = Objects.requireNonNull(relativePath, "relativePath").normalize();
    Objects.requireNonNull(contentDigest, "contentDigest");
    Objects.requireNonNull(content, "content");
    if (relativePath.isAbsolute() || relativePath.startsWith("..")) {
      throw new IllegalArgumentException("Source-file paths must be relative to the source root");
    }
  }
}
