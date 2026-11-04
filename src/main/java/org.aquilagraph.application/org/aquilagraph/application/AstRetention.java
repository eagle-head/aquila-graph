package org.aquilagraph.application;

/** Retains an independently complete AST. */
@FunctionalInterface
public interface AstRetention {
  /**
   * Applies explicit AST retention intent to the established source outcome.
   *
   * @param request
   *          established source and explicit retention intent
   * @return independent publication decision
   * @throws AnalysisPublicationException
   *           if publication fails abruptly after the source is established
   */
  PublicationOutcome retainAst(AstPublicationRequest request);
}
