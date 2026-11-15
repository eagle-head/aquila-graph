package org.aquilagraph.identity.api;

import java.nio.charset.StandardCharsets;
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
public record SourceFile(String sourceFileKey, CapturedLocation relativePath, String contentDigest, String content) {
  /** Validates identity and rootless location. */
  public SourceFile {
    Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    requireRelativePath(relativePath);
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
   *           if the location is absolute
   */
  public static SourceFile fromContent(CapturedLocation relativePath, String content) {
    requireRelativePath(relativePath);
    var contentDigest = digest(Objects.requireNonNull(content, "content"));
    var key = StableId.sha256("source", relativePath.canonicalRelativeName(), contentDigest);
    return new SourceFile(key, relativePath, contentDigest, content);
  }

  /**
   * Returns the case-preserving, slash-separated path used for source identity and snapshot order.
   *
   * @return canonical root-relative path
   */
  public String canonicalRelativePath() {
    return relativePath.canonicalRelativeName();
  }

  private static void requireRelativePath(CapturedLocation path) {
    Objects.requireNonNull(path, "relativePath");
    if (path.isAbsolute()) {
      throw new IllegalArgumentException("Source-file paths must be relative to the source root");
    }
  }

  private static String digest(String content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
    }
  }
}
