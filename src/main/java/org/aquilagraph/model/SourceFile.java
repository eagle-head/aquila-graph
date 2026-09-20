package org.aquilagraph.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable source file content and content-derived identity.
 *
 * @param sourceFileKey
 *          stable source-file identifier
 * @param relativePath
 *          normalized path below the source root
 * @param contentDigest
 *          source content digest
 * @param content
 *          decoded as UTF-8
 */
public record SourceFile(String sourceFileKey, Path relativePath, String contentDigest, String content) {
  /** Validates identity and normalizes the relative path. */
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
