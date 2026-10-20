package org.aquilagraph.identity.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    relativePath = normalizeRelativePath(relativePath);
    Objects.requireNonNull(contentDigest, "contentDigest");
    Objects.requireNonNull(content, "content");
  }

  /**
   * Captures source content with its canonical path and content-derived identity.
   *
   * @param relativePath
   *          path below the source root
   * @param content
   *          decoded source content
   * @return immutable source with a UTF-8 SHA-256 content digest
   * @throws IllegalArgumentException
   *           if the normalized path is absolute, contains a root, or escapes the source root
   */
  public static SourceFile fromContent(Path relativePath, String content) {
    var normalizedPath = normalizeRelativePath(relativePath);
    var contentDigest = digest(Objects.requireNonNull(content, "content"));
    var key = StableId.sha256("source", canonicalPath(normalizedPath), contentDigest);
    return new SourceFile(key, normalizedPath, contentDigest, content);
  }

  /**
   * Returns the case-preserving, slash-separated path used for source identity and snapshot order.
   *
   * @return canonical root-relative path
   */
  public String canonicalRelativePath() {
    return canonicalPath(relativePath);
  }

  private static Path normalizeRelativePath(Path path) {
    var normalized = Objects.requireNonNull(path, "relativePath").normalize();
    if (normalized.isAbsolute() || normalized.getRoot() != null || normalized.startsWith("..")) {
      throw new IllegalArgumentException("Source-file paths must be relative to the source root");
    }
    return normalized;
  }

  private static String canonicalPath(Path path) {
    return path.toString().replace('\\', '/');
  }

  private static String digest(String content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
    }
  }
}
