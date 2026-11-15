package org.aquilagraph.testsupport;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.CapturedLocation.Syntax;

/** Converts host paths used by test fixtures into explicitly captured values. */
public final class TestLocations {
  private static final Syntax HOST_SYNTAX = "\\".equals(FileSystems.getDefault().getSeparator()) ? Syntax.WINDOWS : Syntax.POSIX;

  private TestLocations() {}

  public static CapturedLocation capturedPath(Path path) {
    return CapturedLocation.parse(HOST_SYNTAX, path.toString());
  }

  public static CapturedLocation capturedAbsolute(Path path) {
    return capturedPath(path.toAbsolutePath());
  }
}
