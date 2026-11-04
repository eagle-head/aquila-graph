package org.aquilagraph.application;

/** Produces source and optionally derives control flow without publication. */
@FunctionalInterface
public interface AnalysisComputation {
  /**
   * Computes the requested source and binds the optional selection after production.
   *
   * @param request
   *          exact source production request and optional unbound graph selection
   * @return qualified source and any derived CFG
   * @throws AnalysisDerivationException
   *           if derivation reports an abrupt failure for the bound request
   */
  AnalysisRun analyze(AnalysisComputationRequest request);
}
