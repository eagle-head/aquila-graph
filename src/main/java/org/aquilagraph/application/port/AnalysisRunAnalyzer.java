package org.aquilagraph.application.port;

import java.nio.file.Path;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CompilerConfiguration;

/** Produces one public analysis run from a source root and compiler configuration. */
@FunctionalInterface
public interface AnalysisRunAnalyzer {
  /**
   * Analyzes one configured source root.
   *
   * @param sourceRoot
   *          source directory
   * @param configuration
   *          compiler configuration
   * @return completed, incomplete, or rejected analysis run
   */
  AnalysisRun analyze(Path sourceRoot, CompilerConfiguration configuration);
}
