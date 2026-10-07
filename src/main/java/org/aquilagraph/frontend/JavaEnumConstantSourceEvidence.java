package org.aquilagraph.frontend;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;

/** Locates the optional written argument list that the attributed enum creation tree does not distinguish. */
final class JavaEnumConstantSourceEvidence {
  private static final char SLASH = '/';
  private static final char STAR = '*';
  private static final char BACKSLASH = '\\';

  private JavaEnumConstantSourceEvidence() {
  }

  static Optional<Boolean> argumentList(SourceFile sourceFile, SourceSpan range, long identifierStart) {
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(range, "range");
    if (identifierStart < range.startOffset() || identifierStart >= range.endOffsetExclusive()) {
      return Optional.empty();
    }
    var content = sourceFile.content();
    var end = Math.toIntExact(range.endOffsetExclusive());
    var cursor = Math.toIntExact(identifierStart);
    var characters = decodedCharacters(content, cursor, end);
    var first = character(characters, cursor);
    if (first.isEmpty() || !Character.isJavaIdentifierStart(first.orElseThrow().value())) {
      return Optional.empty();
    }
    cursor = first.orElseThrow().nextOffset();
    while (cursor < end) {
      var next = character(characters, cursor);
      if (next.isEmpty() || !Character.isJavaIdentifierPart(next.orElseThrow().value())) {
        break;
      }
      cursor = next.orElseThrow().nextOffset();
    }
    var delimiter = -1;
    while (cursor < end && delimiter < 0) {
      var next = character(characters, cursor);
      if (next.isEmpty()) {
        return Optional.empty();
      }
      var value = next.orElseThrow().value();
      if (Character.isWhitespace(value) || Character.isSpaceChar(value)) {
        cursor = next.orElseThrow().nextOffset();
        continue;
      }
      if (value == SLASH) {
        var following = character(characters, next.orElseThrow().nextOffset());
        if (following.isEmpty()) {
          return Optional.empty();
        }
        if (following.orElseThrow().value() == SLASH) {
          cursor = following.orElseThrow().nextOffset();
          while (cursor < end) {
            var comment = character(characters, cursor).orElseThrow();
            cursor = comment.nextOffset();
            if (comment.value() == '\n' || comment.value() == '\r') {
              break;
            }
          }
          continue;
        }
        if (following.orElseThrow().value() == STAR) {
          cursor = following.orElseThrow().nextOffset();
          var closed = false;
          while (cursor < end) {
            var comment = character(characters, cursor).orElseThrow();
            cursor = comment.nextOffset();
            if (comment.value() == STAR) {
              var close = character(characters, cursor);
              if (close.isPresent() && close.orElseThrow().value() == SLASH) {
                cursor = close.orElseThrow().nextOffset();
                closed = true;
                break;
              }
            }
          }
          if (!closed) {
            return Optional.empty();
          }
          continue;
        }
        return Optional.empty();
      }
      delimiter = value;
    }
    return switch (delimiter) {
      case '(' -> Optional.of(true);
      case '{', ',', ';', -1 -> Optional.of(false);
      default -> Optional.empty();
    };
  }

  private static Map<Integer, SourceCharacter> decodedCharacters(String content, int start, int end) {
    var characters = new HashMap<Integer, SourceCharacter>();
    var cursor = start;
    var backslashes = 0;
    var previousEscaped = false;
    while (cursor < end) {
      var unit = codeUnit(content, cursor, end, backslashes, previousEscaped);
      var character = unit.character();
      characters.put(cursor, character);
      backslashes = character.value() == BACKSLASH ? backslashes + 1 : 0;
      previousEscaped = unit.escaped();
      cursor = character.nextOffset();
    }
    for (var entry : characters.entrySet()) {
      var first = entry.getValue();
      var second = characters.get(first.nextOffset());
      if (second != null && Character.isHighSurrogate((char) first.value()) && Character.isLowSurrogate((char) second.value())) {
        entry.setValue(supplementaryCharacter(first, second));
      }
    }
    return characters;
  }

  private static Optional<SourceCharacter> character(Map<Integer, SourceCharacter> characters, int offset) {
    return Optional.ofNullable(characters.get(offset));
  }

  private static SourceCharacter supplementaryCharacter(SourceCharacter first, SourceCharacter second) {
    return new SourceCharacter(Character.toCodePoint((char) first.value(), (char) second.value()), second.nextOffset());
  }

  private static DecodedUnit rawBackslash(int offset) {
    return new DecodedUnit(new SourceCharacter(BACKSLASH, offset + 1), false);
  }

  private static DecodedUnit codeUnit(String content, int offset, int end, int backslashes, boolean previousEscaped) {
    if (content.charAt(offset) == BACKSLASH && (previousEscaped || backslashes % 2 == 0)) {
      var marker = offset + 1;
      while (marker < end && content.charAt(marker) == 'u') {
        marker++;
      }
      if (marker > offset + 1 && marker + 4 <= end) {
        var code = 0;
        for (var position = marker; position < marker + 4; position++) {
          var digit = Character.digit(content.charAt(position), 16);
          if (digit < 0) {
            return rawBackslash(offset);
          }
          code = (code << 4) | digit;
        }
        return new DecodedUnit(new SourceCharacter(code, marker + 4), true);
      }
    }
    return new DecodedUnit(new SourceCharacter(content.charAt(offset), offset + 1), false);
  }

  private record SourceCharacter(int value, int nextOffset) {
  }

  private record DecodedUnit(SourceCharacter character, boolean escaped) {
  }
}
