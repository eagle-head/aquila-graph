package org.aquilagraph.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.ControlFlowGraph;

public record ControlFlowGraphResult(
    Optional<ControlFlowGraph> graph, List<AnalysisDiagnostic> diagnostics) {
  public ControlFlowGraphResult {
    graph = Objects.requireNonNull(graph, "graph");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }
}
