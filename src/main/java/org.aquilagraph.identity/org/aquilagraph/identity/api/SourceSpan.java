package org.aquilagraph.identity.api;

import java.util.Objects;

/**
 * Half-open character range within a captured source file.
 *
 * @param sourceFileKey
 *          owning source-file identifier
 * @param startOffset
 *          inclusive character offset
 * @param endOffsetExclusive
 *          exclusive character offset
 */
public record SourceSpan(String sourceFileKey, long startOffset, long endOffsetExclusive) {
  /** Validates source ownership and range bounds. */
  public SourceSpan {
    Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    if (startOffset < 0 || endOffsetExclusive < startOffset) {
      throw new IllegalArgumentException("Source offsets must describe an available half-open span for " + sourceFileKey + ": ["
          + startOffset + ", " + endOffsetExclusive + ")");
    }
  }

  /**
   * Returns the number of characters in the half-open range.
   *
   * @return span length
   */
  public long length() {
    return endOffsetExclusive - startOffset;
  }
}
