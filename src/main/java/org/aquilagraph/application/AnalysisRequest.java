package org.aquilagraph.application;

import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.model.CompilerConfiguration;

/**
 * Defines the source root, compiler profile, and repository intent for one analysis.
 *
 * @param sourceRoot
 *          directory containing the source tree
 * @param compilerConfiguration
 *          accepted compiler configuration
 * @param repositorySavePolicy
 *          repository policy for a completed run
 */
public record AnalysisRequest(Path sourceRoot, CompilerConfiguration compilerConfiguration, RepositorySavePolicy repositorySavePolicy) {
  /** Validates and normalizes request input. */
  public AnalysisRequest {
    sourceRoot = Objects.requireNonNull(Objects.requireNonNull(sourceRoot, "sourceRoot").toAbsolutePath().normalize(),
        "normalized sourceRoot");
    Objects.requireNonNull(compilerConfiguration, "compilerConfiguration");
    Objects.requireNonNull(repositorySavePolicy, "repositorySavePolicy");
  }

}
