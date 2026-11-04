package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.AstProductionRequest;

/**
 * Source production and optional unchanged graph selection.
 *
 * @param source
 *          exact source production request
 * @param cfgSelection
 *          optional unbound graph selection
 */
public record AnalysisComputationRequest(AstProductionRequest source, Optional<CfgSelection> cfgSelection) {
  /** Requires explicit source and selection presence. */
  public AnalysisComputationRequest {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(cfgSelection, "cfgSelection");
  }
}
