package org.aquilagraph.source.internal;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Translates the standard glob operators over captured slash-separated names. */
final class CapturedPathGlob {
  private static final char GLOB_NEGATION = '!';
  private static final char REGEX_NEGATION = '^';
  private CapturedPathGlob() {
  }

  static String expression(String glob) {
    var result = new StringBuilder();
    var group = false;
    var index = 0;
    while (index < glob.length()) {
      var token = glob.charAt(index);
      switch (token) {
        case '*' -> {
          if (index + 1 < glob.length() && glob.charAt(index + 1) == '*') {
            result.append(".*");
            index++;
          } else {
            result.append("[^/]*");
          }
        }
        case '?' -> result.append("[^/]");
        case '\\' -> {
          index++;
          if (index == glob.length()) {
            throw invalid(glob, index - 1);
          }
          result.append(Pattern.quote(String.valueOf(glob.charAt(index))));
        }
        case '[' -> {
          var end = glob.indexOf(']', index + 1);
          if (end < 0 || end == index + 1) {
            throw invalid(glob, index);
          }
          var contents = glob.substring(index + 1, end);
          result.append("(?=[^/])[");
          if (contents.charAt(0) == GLOB_NEGATION) {
            result.append('^');
            contents = contents.substring(1);
          } else if (contents.charAt(0) == REGEX_NEGATION) {
            result.append('\\');
          }
          result.append(contents.replace("\\", "\\\\").replace("[", "\\[").replace("&", "\\&")).append(']');
          index = end;
        }
        case '{' -> {
          if (group) {
            throw invalid(glob, index);
          }
          group = true;
          result.append("(?:");
        }
        case '}' -> {
          if (!group) {
            throw invalid(glob, index);
          }
          group = false;
          result.append(')');
        }
        case ',' -> result.append(group ? "|" : ",");
        default -> result.append(Pattern.quote(String.valueOf(token)));
      }
      index++;
    }
    if (group) {
      throw invalid(glob, glob.length());
    }
    return result.toString();
  }

  private static PatternSyntaxException invalid(String glob, int index) {
    return new PatternSyntaxException("Invalid captured path glob", glob, index);
  }
}
