package org.aquilagraph.frontend.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Immutable Unicode translation, raw UTF-16 boundaries and lexical trivia for one captured unit. */
final class JavaTranslatedSource {
  private final String translatedText;
  private final List<Integer> starts;
  private final List<Integer> ends;
  private final int rawLength;

  private JavaTranslatedSource(String text, List<Integer> starts, List<Integer> ends, int rawLength) {
    translatedText = text;
    this.starts = List.copyOf(starts);
    this.ends = List.copyOf(ends);
    this.rawLength = rawLength;
  }

  static JavaTranslatedSource capture(String source) {
    Objects.requireNonNull(source, "source");
    var text = new StringBuilder(source.length());
    var starts = new ArrayList<Integer>();
    var ends = new ArrayList<Integer>();
    var index = 0;
    var previousWasEscape = false;
    var trailingBackslashes = 0;
    while (index < source.length()) {
      var end = index + 1;
      var character = source.charAt(index);
      var escaped = false;
      if (character == '\\' && (previousWasEscape || trailingBackslashes % 2 == 0) && end < source.length() && source.charAt(end) == 'u') {
        var hex = end;
        while (hex < source.length() && source.charAt(hex) == 'u') {
          hex++;
        }
        if (hex + 4 <= source.length()) {
          var value = 0;
          var valid = true;
          for (var offset = hex; offset < hex + 4; offset++) {
            var next = Character.digit(source.charAt(offset), 16);
            valid &= next >= 0 && source.charAt(offset) <= 127;
            value = value * 16 + Math.max(next, 0);
          }
          if (valid) {
            character = (char) value;
            end = hex + 4;
            escaped = true;
          }
        }
      }
      text.append(character);
      starts.add(index);
      ends.add(end);
      trailingBackslashes = character == '\\' ? trailingBackslashes + 1 : 0;
      previousWasEscape = escaped;
      index = end;
    }
    return new JavaTranslatedSource(text.toString(), starts, ends, source.length());
  }

  String text() {
    return translatedText;
  }

  int rawStart(int index) {
    return starts.get(index);
  }

  int rawEnd(int exclusiveIndex) {
    return ends.get(exclusiveIndex - 1);
  }

  /** Returns only exact boundaries, never a position inside an escape or surrogate pair. */
  OptionalInt translatedOffset(long rawOffset) {
    if (rawOffset == rawLength) {
      return OptionalInt.of(translatedText.length());
    }
    if (rawOffset < 0 || rawOffset > rawLength) {
      return OptionalInt.empty();
    }
    var index = Collections.binarySearch(starts, Math.toIntExact(rawOffset));
    if (index < 0 || (index > 0 && Character.isHighSurrogate(translatedText.charAt(index - 1))
        && Character.isLowSurrogate(translatedText.charAt(index)))) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(index);
  }

  OptionalInt identifierEnd(int start) {
    if (start >= translatedText.length() || !Character.isJavaIdentifierStart(translatedText.codePointAt(start))) {
      return OptionalInt.empty();
    }
    var index = start + Character.charCount(translatedText.codePointAt(start));
    while (index < translatedText.length() && Character.isJavaIdentifierPart(translatedText.codePointAt(index))) {
      index += Character.charCount(translatedText.codePointAt(index));
    }
    return OptionalInt.of(index);
  }

  /** An unfinished block comment is unknown at a bounded consumer, not evidence of absent syntax. */
  OptionalInt triviaEnd(int start, int end) {
    var index = start;
    while (index < end) {
      var character = translatedText.charAt(index);
      if (character == ' ' || character == '\t' || character == '\f' || character == '\r' || character == '\n') {
        index++;
      } else if (index + 1 < end && translatedText.startsWith("//", index)) {
        index += 2;
        while (index < end && translatedText.charAt(index) != '\n' && translatedText.charAt(index) != '\r') {
          index++;
        }
      } else if (index + 1 < end && translatedText.startsWith("/*", index)) {
        var close = translatedText.indexOf("*/", index + 2);
        if (close < 0 || close + 2 > end) {
          return OptionalInt.empty();
        }
        index = close + 2;
      } else {
        break;
      }
    }
    return OptionalInt.of(index);
  }
}
