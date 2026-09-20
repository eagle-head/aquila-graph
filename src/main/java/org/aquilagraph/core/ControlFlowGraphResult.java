package org.aquilagraph.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.ControlFlowGraph;

/**
 * Contains an optional method graph and diagnostics emitted while building it.
 *
 * @param graph
 *          graph when construction succeeded
 * @param diagnostics
 *          construction diagnostics
 */
public record ControlFlowGraphResult(Optional<ControlFlowGraph> graph, List<AnalysisDiagnostic> diagnostics) {
  /** Validates and defensively copies the result. */
  public ControlFlowGraphResult {
    Objects.requireNonNull(graph, "graph");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }
}
