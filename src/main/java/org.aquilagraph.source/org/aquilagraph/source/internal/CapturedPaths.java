package org.aquilagraph.source.internal;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Decodes captured location syntax without resolving foreign roots on the current host. */
final class CapturedPaths {
  enum Role {
    VALUE, ABSOLUTE, RELATIVE
  }
  enum Syntax {
    WINDOWS, POSIX
  }

  private final Map<Syntax, CapturedFileSystem> fileSystems = new EnumMap<>(Syntax.class);
  private final Syntax local = "\\".equals(FileSystems.getDefault().getSeparator()) ? Syntax.WINDOWS : Syntax.POSIX;
  Path read(String text, Role role, @Nullable Syntax sourceSyntax) {
    var syntax = role == Role.RELATIVE && sourceSyntax != null ? sourceSyntax : rootedSyntax(text);
    Path result;
    if (syntax == local && Path.of(text).toString().equals(text)) {
      result = Path.of(text);
    } else {
      result = fileSystems.computeIfAbsent(syntax, CapturedFileSystem::new).getPath(text);
    }
    if (role == Role.ABSOLUTE && !result.isAbsolute()) {
      throw new IllegalArgumentException("A retained absolute location requires its original root: " + text);
    }
    return result;
  }

  Syntax syntax(Path path) {
    return path instanceof CapturedPath captured ? captured.syntax() : local;
  }

  private Syntax relativeSyntax(String text) {
    return text.indexOf('/') >= 0 ? Syntax.POSIX : text.indexOf('\\') >= 0 ? Syntax.WINDOWS : local;
  }

  private Syntax rootedSyntax(String text) {
    if (text.startsWith("\\\\") || (text.length() >= 3 && driveLetter(text.charAt(0)) && text.charAt(1) == ':'
        && (text.charAt(2) == '\\' || text.charAt(2) == '/'))) {
      return Syntax.WINDOWS;
    }
    return text.startsWith("/") ? Syntax.POSIX : relativeSyntax(text);
  }

  static boolean driveLetter(char value) {
    return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
  }
}
