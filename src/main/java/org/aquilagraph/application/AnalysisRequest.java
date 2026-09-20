package org.aquilagraph.application;

import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;

/**
 * Defines the source root, compiler profile, and repository intent for one analysis.
 *
 * @param sourceRoot
 *          directory containing Java sources
 * @param compilerConfiguration
 *          accepted compiler configuration
 * @param repositorySaveRequested
 *          whether a completed run should be saved
 */
public record AnalysisRequest(Path sourceRoot, CompilerConfiguration compilerConfiguration, boolean repositorySaveRequested) {
  /** Validates and normalizes request input. */
  public AnalysisRequest {
    sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot").toAbsolutePath().normalize();
    Objects.requireNonNull(compilerConfiguration, "compilerConfiguration");
  }

  /**
   * Creates a request using the standard compiler profile for a supported Java release.
   *
   * @param sourceRoot
   *          directory containing Java sources
   * @param release
   *          supported Java language release
   * @param repositorySaveRequested
   *          whether a completed run should be saved
   * @return Java analysis request
   */
  public static AnalysisRequest forJava(Path sourceRoot, JavaRelease release, boolean repositorySaveRequested) {
    return new AnalysisRequest(sourceRoot, CompilerConfiguration.standard(release), repositorySaveRequested);
  }
}
