package org.aquilagraph.application;

import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.application.port.AnalysisRunAnalyzer;
import org.aquilagraph.application.port.SourceAnalyzer;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CompilerConfiguration;

/** Composes source analysis and control-flow assembly into one analysis-run operation. */
public final class SourceAnalysisRunner implements AnalysisRunAnalyzer {
  private final SourceAnalyzer sourceAnalyzer;
  private final AnalysisEngine analysisEngine;

  /**
   * Creates a runner from the frontend and core analysis collaborators.
   *
   * @param sourceAnalyzer
   *          source-analysis port
   * @param analysisEngine
   *          control-flow analysis engine
   */
  public SourceAnalysisRunner(SourceAnalyzer sourceAnalyzer, AnalysisEngine analysisEngine) {
    this.sourceAnalyzer = Objects.requireNonNull(sourceAnalyzer, "sourceAnalyzer");
    this.analysisEngine = Objects.requireNonNull(analysisEngine, "analysisEngine");
  }

  @Override
  public AnalysisRun analyze(Path sourceRoot, CompilerConfiguration configuration) {
    return analysisEngine.analyze(sourceAnalyzer.analyze(sourceRoot, configuration));
  }
}
