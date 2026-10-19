package org.aquilagraph.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable complete-unit lexical view with translated spelling and original UTF-16 offsets. */
final class JavaSourceTokens {
  private static final List<String> COMPOUND_SYMBOLS = List.of(">>>=", "<<=", ">>=", "...", ">>>", "::", "->", "++", "--", "==", "<=", ">=",
      "!=", "&&", "||", "+=", "-=", "*=", "/=", "&=", "|=", "^=", "%=", "<<", ">>");
  private final List<Token> tokens;

  private JavaSourceTokens(List<Token> tokens) {
    this.tokens = List.copyOf(tokens);
  }

  static JavaSourceTokens capture(String source) {
    var decoded = decode(Objects.requireNonNull(source, "source"));
    var text = decoded.text();
    var result = new ArrayList<Token>();
    var index = 0;
    while (index < text.length()) {
      var start = index;
      var character = text.charAt(index);
      if (whiteSpace(character)) {
        index++;
        continue;
      }
      if (text.startsWith("/*", index)) {
        var end = text.indexOf("*/", index + 2);
        index = end < 0 ? text.length() : end + 2;
        continue;
      }
      if (text.startsWith("//", index)) {
        while (index < text.length() && text.charAt(index) != '\n' && text.charAt(index) != '\r') {
          index++;
        }
        continue;
      }
      Kind kind;
      if (character == '"' || character == '\'') {
        kind = character == '\'' ? Kind.CHARACTER : text.startsWith("\"\"\"", index) ? Kind.TEXT_BLOCK : Kind.STRING;
        var delimiter = kind == Kind.TEXT_BLOCK ? "\"\"\"" : Character.toString(character);
        index = quotedEnd(text, index, delimiter);
      } else if (Character.isJavaIdentifierStart(text.codePointAt(index))) {
        kind = Kind.IDENTIFIER;
        index += Character.charCount(text.codePointAt(index));
        while (index < text.length() && Character.isJavaIdentifierPart(text.codePointAt(index))) {
          index += Character.charCount(text.codePointAt(index));
        }
      } else if (digit(character) || (character == '.' && index + 1 < text.length() && digit(text.charAt(index + 1)))) {
        kind = Kind.NUMBER;
        index = numberEnd(text, index);
      } else {
        kind = Kind.SYMBOL;
        var symbol = symbol(text, index);
        index += symbol.length();
      }
      result.add(new Token(kind, text.substring(start, index), decoded.starts().get(start), decoded.ends().get(index - 1)));
    }
    return new JavaSourceTokens(result);
  }

  List<Token> all() {
    return tokens;
  }

  List<Token> within(long start, long end) {
    return tokens.stream().filter(token -> token.start() >= start && token.end() <= end).toList();
  }

  private static String symbol(String text, int index) {
    return COMPOUND_SYMBOLS.stream().filter(value -> text.startsWith(value, index)).findFirst()
        .orElseGet(() -> text.substring(index, index + 1));
  }

  private static int quotedEnd(String text, int start, String delimiter) {
    var index = start + delimiter.length();
    while (index < text.length()) {
      if (text.startsWith(delimiter, index)) {
        return index + delimiter.length();
      }
      if (text.charAt(index) == '\\' && index + 1 < text.length()) {
        index += 2;
      } else {
        index++;
      }
    }
    return index;
  }

  private static int numberEnd(String text, int start) {
    var index = start;
    var hexadecimal = text.startsWith("0x", start) || text.startsWith("0X", start);
    var binary = text.startsWith("0b", start) || text.startsWith("0B", start);
    if (hexadecimal || binary) {
      index += 2;
    }
    while (index < text.length() && (text.charAt(index) == '_' || numericDigit(text.charAt(index), hexadecimal))) {
      index++;
    }
    if (!binary && index < text.length() && text.charAt(index) == '.') {
      index++;
      while (index < text.length() && (text.charAt(index) == '_' || numericDigit(text.charAt(index), hexadecimal))) {
        index++;
      }
    }
    if (!binary && index < text.length() && exponent(text.charAt(index), hexadecimal)) {
      index++;
      if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
        index++;
      }
      while (index < text.length() && (text.charAt(index) == '_' || digit(text.charAt(index)))) {
        index++;
      }
    }
    if (index < text.length() && "lLfFdD".indexOf(text.charAt(index)) >= 0) {
      index++;
    }
    return index;
  }

  private static boolean exponent(char character, boolean hexadecimal) {
    return hexadecimal ? character == 'p' || character == 'P' : character == 'e' || character == 'E';
  }

  private static boolean numericDigit(char character, boolean hexadecimal) {
    return digit(character) || (hexadecimal && ((character >= 'a' && character <= 'f') || (character >= 'A' && character <= 'F')));
  }

  private static boolean digit(char character) {
    return character >= '0' && character <= '9';
  }

  private static boolean whiteSpace(char character) {
    return character == ' ' || character == '\t' || character == '\f' || character == '\r' || character == '\n';
  }

  private static DecodedSource decode(String source) {
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
    return new DecodedSource(text.toString(), starts, ends);
  }

  enum Kind {
    IDENTIFIER, NUMBER, CHARACTER, STRING, TEXT_BLOCK, SYMBOL
  }

  record Token(Kind kind, String value, int start, int end) {
  }

  private record DecodedSource(String text, List<Integer> starts, List<Integer> ends) {
  }
}
