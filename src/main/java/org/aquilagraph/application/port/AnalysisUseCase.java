package org.aquilagraph.application.port;

import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRequest;

/** Input port for executing one source-analysis request. */
@FunctionalInterface
public interface AnalysisUseCase {
  /**
   * Executes one analysis request and applies its repository policy.
   *
   * @param request
   *          analysis input and repository policy
   * @return analysis execution and repository status
   */
  AnalysisExecution execute(AnalysisRequest request);
}
