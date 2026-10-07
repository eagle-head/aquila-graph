package org.aquilagraph.frontend;

import java.util.Objects;
import java.util.Set;
import javax.lang.model.SourceVersion;
import org.aquilagraph.identity.api.JavaRelease;

/** Validates that the active compiler can analyze a selected Java release. */
final class JavaCompilerReleaseSupport {
  private JavaCompilerReleaseSupport() {
  }

  static void requireSupported(Set<SourceVersion> sourceVersions, JavaRelease release) {
    Objects.requireNonNull(sourceVersions, "sourceVersions");
    Objects.requireNonNull(release, "release");
    var sourceVersionName = "RELEASE_" + release.feature();
    if (sourceVersions.stream().noneMatch(version -> version.name().equals(sourceVersionName))) {
      throw new IllegalStateException("The system compiler does not support Java release " + release.feature());
    }
  }
}
