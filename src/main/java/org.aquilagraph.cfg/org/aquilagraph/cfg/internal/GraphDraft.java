package org.aquilagraph.cfg.internal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.SyntaxNode;

/** Closed construction content and creating-site evidence awaiting source-bound admission. */
record GraphDraft(DraftGraph content, Map<String, SyntaxNode> creatingNodes, Map<String, String> generatedRoles,
    List<AnalysisDiagnostic> diagnostics) {
  GraphDraft {
    Objects.requireNonNull(content, "content");
    creatingNodes = Map.copyOf(creatingNodes);
    generatedRoles = Map.copyOf(generatedRoles);
    diagnostics = List.copyOf(diagnostics);
  }
}
