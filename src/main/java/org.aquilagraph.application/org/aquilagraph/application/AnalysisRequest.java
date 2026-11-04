package org.aquilagraph.application;

import java.util.Objects;

/**
 * Computation and independent publication intent for coordinated execution.
 *
 * @param computation
 *          exact source production and optional graph selection
 * @param publicationIntent
 *          independent storage intent
 */
public record AnalysisRequest(AnalysisComputationRequest computation, PublicationIntent publicationIntent) {
  /** Requires explicit computation and publication intent. */
  public AnalysisRequest {
    Objects.requireNonNull(computation, "computation");
    Objects.requireNonNull(publicationIntent, "publicationIntent");
  }
}
