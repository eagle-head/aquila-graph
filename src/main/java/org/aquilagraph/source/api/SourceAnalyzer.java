package org.aquilagraph.source.api;

import java.nio.file.Path;
import org.aquilagraph.identity.api.CompilerConfiguration;

/** Captures and analyzes a configured source tree. */
@FunctionalInterface
public interface SourceAnalyzer {
  /**
   * Captures and analyzes sources beneath a root directory.
   *
   * @param sourceRoot
   *          source directory
   * @param configuration
   *          explicit compiler configuration
   * @return declarations, transient syntax evidence, environment identity, and diagnostics
   * @throws AnalysisInputException
   *           if the source input or compiler configuration is rejected
   * @throws AnalysisEnvironmentException
   *           if the active compiler environment cannot complete the analysis
   */
  SyntaxAnalysisInput analyze(Path sourceRoot, CompilerConfiguration configuration);
}
