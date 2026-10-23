package org.aquilagraph.source.api;

/** Produces immutable source facts without acquiring a graph consumer. */
@FunctionalInterface
public interface AstProducer {
  /**
   * Captures sources and establishes a source-qualified outcome after owned cleanup.
   *
   * @param request
   *          explicit capture, compiler configuration, and source profile
   * @return normal source outcome and retained captured provenance
   * @throws AnalysisInputException
   *           if source capture or configuration fails
   * @throws AnalysisEnvironmentException
   *           if compiler acquisition or environment verification fails
   */
  AstOutcome produce(AstProductionRequest request);
}
