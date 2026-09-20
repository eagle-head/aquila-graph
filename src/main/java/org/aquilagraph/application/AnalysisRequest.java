package org.aquilagraph.application;

import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.model.CompilerConfiguration;

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
   * Creates a request using the supported Java 25 compiler profile.
   *
   * @param sourceRoot
   *          directory containing Java sources
   * @param repositorySaveRequested
   *          whether a completed run should be saved
   * @return Java 25 analysis request
   */
  public static AnalysisRequest java25(Path sourceRoot, boolean repositorySaveRequested) {
    return new AnalysisRequest(sourceRoot, CompilerConfiguration.java25(), repositorySaveRequested);
  }
}
