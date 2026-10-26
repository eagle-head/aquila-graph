package org.aquilagraph.build;

import java.util.ArrayList;
import java.util.List;

/** Tokenizes adapter configuration without evaluating shell syntax or substitutions. */
final class ArgumentWords {
  private static final char UNQUOTED = '\0';

  private ArgumentWords() {
  }

  static List<String> parse(String text) {
    var result = new ArrayList<String>();
    var word = new StringBuilder();
    var quote = UNQUOTED;
    var present = false;
    var position = 0;
    while (position < text.length()) {
      var character = text.charAt(position);
      position++;
      if (character == quote) {
        quote = UNQUOTED;
      } else if (quote == UNQUOTED && (character == '\'' || character == '"')) {
        quote = character;
        present = true;
      } else if (quote == UNQUOTED && Character.isWhitespace(character)) {
        if (present) {
          result.add(word.toString());
          word.setLength(0);
          present = false;
        }
      } else if (quote != '\'' && character == '\\' && position < text.length()
          && (text.charAt(position) == quote || text.charAt(position) == '\\')) {
        word.append(text.charAt(position));
        position++;
        present = true;
      } else {
        word.append(character);
        present = true;
      }
    }
    if (quote != UNQUOTED) {
      throw new IllegalArgumentException("Unterminated quote in local build flags");
    }
    if (present) {
      result.add(word.toString());
    }
    return List.copyOf(result);
  }
}
