package org.aquilagraph.frontend.internal;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.CapturedLocation.Syntax;

/** Converts between captured lexical values and paths at frontend filesystem boundaries. */
enum NativeLocations {
  ;

  static CapturedLocation capture(Path path) {
    return CapturedLocation.parse(syntax(path.getFileSystem().getSeparator()), path.toString());
  }

  static Path toNative(CapturedLocation location) {
    if (!location.isAbsolute() || location.syntax() != syntax(FileSystems.getDefault().getSeparator())) {
      throw new IllegalArgumentException("Compiler dependencies require an absolute location in the host syntax: " + location.spelling());
    }
    var path = Path.of(location.spelling());
    var captured = capture(path);
    if (!path.isAbsolute() || location.syntax() != captured.syntax() || location.rootKind() != captured.rootKind()
        || !location.rootComponents().equals(captured.rootComponents()) || !location.components().equals(captured.components())) {
      throw new IllegalArgumentException("The native path does not preserve the configured location: " + location.spelling());
    }
    return path;
  }

  private static Syntax syntax(String separator) {
    return "\\".equals(separator) ? Syntax.WINDOWS : Syntax.POSIX;
  }
}
