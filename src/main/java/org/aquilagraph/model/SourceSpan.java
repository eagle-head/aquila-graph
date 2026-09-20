package org.aquilagraph.model;

import java.util.Objects;

public record SourceSpan(String sourceFileKey, long startOffset, long endOffsetExclusive) {
  public SourceSpan {
    Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    if (startOffset < 0 || endOffsetExclusive < startOffset) {
      throw new IllegalArgumentException("Source offsets must describe an available half-open span");
    }
  }

  public long length() {
    return endOffsetExclusive - startOffset;
  }
}
