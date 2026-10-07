package org.aquilagraph.application.port;

import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRequest;

/** Coordinates independently owned source, control-flow, and publication outcomes. */
@FunctionalInterface
public interface AnalysisUseCase {
  /**
   * Executes the requested computation and independent publication intent.
   *
   * @param request
   *          exact caller request
   * @return computation and publication outcomes
   */
  AnalysisExecution execute(AnalysisRequest request);
}
