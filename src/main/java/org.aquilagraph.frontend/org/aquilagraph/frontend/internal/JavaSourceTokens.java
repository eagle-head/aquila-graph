package org.aquilagraph.frontend.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Immutable complete-unit lexical view with translated spelling and original UTF-16 offsets. */
final class JavaSourceTokens {
  private static final List<String> COMPOUND_SYMBOLS = List.of(">>>=", "<<=", ">>=", "...", ">>>", "::", "->", "++", "--", "==", "<=", ">=",
      "!=", "&&", "||", "+=", "-=", "*=", "/=", "&=", "|=", "^=", "%=", "<<", ">>");
  private final List<Token> tokens;
  private final JavaTranslatedSource capturedSource;

  private JavaSourceTokens(List<Token> tokens, JavaTranslatedSource source) {
    this.tokens = List.copyOf(tokens);
    capturedSource = source;
  }

  static JavaSourceTokens capture(String source) {
    var decoded = JavaTranslatedSource.capture(source);
    var text = decoded.text();
    var result = new ArrayList<Token>();
    var index = 0;
    while (index < text.length()) {
      index = decoded.triviaEnd(index, text.length()).orElse(text.length());
      if (index == text.length()) {
        break;
      }
      var start = index;
      var character = text.charAt(index);
      var identifierEnd = decoded.identifierEnd(index);
      Kind kind;
      if (character == '"' || character == '\'') {
        kind = character == '\'' ? Kind.CHARACTER : text.startsWith("\"\"\"", index) ? Kind.TEXT_BLOCK : Kind.STRING;
        var delimiter = kind == Kind.TEXT_BLOCK ? "\"\"\"" : Character.toString(character);
        index = quotedEnd(text, index, delimiter);
      } else if (identifierEnd.isPresent()) {
        kind = Kind.IDENTIFIER;
        index = identifierEnd.orElseThrow();
      } else if (digit(character) || (character == '.' && index + 1 < text.length() && digit(text.charAt(index + 1)))) {
        kind = Kind.NUMBER;
        index = numberEnd(text, index);
      } else {
        kind = Kind.SYMBOL;
        var symbol = symbol(text, index);
        index += symbol.length();
      }
      var spelling = text.substring(start, index);
      var meaning = kind == Kind.IDENTIFIER ? identifierMeaning(spelling) : spelling;
      result.add(new Token(kind, spelling, meaning, decoded.rawStart(start), decoded.rawEnd(index)));
    }
    return new JavaSourceTokens(result, decoded);
  }

  JavaTranslatedSource source() {
    return capturedSource;
  }

  List<Token> all() {
    return tokens;
  }

  List<Token> within(long start, long end) {
    return tokens.stream().filter(token -> token.start() >= start && token.end() <= end).toList();
  }

  Optional<Token> closingBrace(long start, long end) {
    return tokens.stream().filter(token -> token.start() >= start && token.end() == end)
        .filter(token -> token.kind() == Kind.SYMBOL && "}".equals(token.value())).findFirst();
  }

  private static String identifierMeaning(String spelling) {
    return spelling.codePoints().filter(character -> !Character.isIdentifierIgnorable(character))
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
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

  enum Kind {
    IDENTIFIER, NUMBER, CHARACTER, STRING, TEXT_BLOCK, SYMBOL
  }

  /** Keeps translated spelling and compiler identifier interpretation separate from original offsets. */
  record Token(Kind kind, String value, String meaning, int start, int end) {
  }
}
