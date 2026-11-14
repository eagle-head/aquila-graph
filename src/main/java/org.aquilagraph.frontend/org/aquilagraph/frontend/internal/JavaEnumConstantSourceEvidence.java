package org.aquilagraph.frontend.internal;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/** Locates the optional written argument list that the attributed enum creation tree does not distinguish. */
final class JavaEnumConstantSourceEvidence {
  private JavaEnumConstantSourceEvidence() {
  }

  static Optional<Boolean> argumentList(JavaTranslatedSource source, SourceSpan range, long identifierStart) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(range, "range");
    if (identifierStart < range.startOffset() || identifierStart >= range.endOffsetExclusive()) {
      return Optional.empty();
    }
    var start = source.translatedOffset(identifierStart);
    var end = source.translatedOffset(range.endOffsetExclusive());
    if (start.isEmpty() || end.isEmpty()) {
      return Optional.empty();
    }
    var identifierEnd = source.identifierEnd(start.orElseThrow());
    if (identifierEnd.isEmpty() || identifierEnd.orElseThrow() > end.orElseThrow()) {
      return Optional.empty();
    }
    var delimiter = source.triviaEnd(identifierEnd.orElseThrow(), end.orElseThrow());
    if (delimiter.isEmpty()) {
      return Optional.empty();
    }
    if (delimiter.orElseThrow() == end.orElseThrow()) {
      return Optional.of(false);
    }
    return switch (source.text().charAt(delimiter.orElseThrow())) {
      case '(' -> Optional.of(true);
      case '{', ',', ';' -> Optional.of(false);
      default -> Optional.empty();
    };
  }
}
