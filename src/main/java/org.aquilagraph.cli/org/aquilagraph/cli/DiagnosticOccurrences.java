package org.aquilagraph.cli;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;

/** Selects native event slots in canonical presentation order without matching values. */
record DiagnosticOccurrences(List<AnalysisDiagnostic> diagnostics, Map<String, Integer> methodOffsets, int sourceCount,
    int applicationOffset) {
  DiagnosticOccurrences {
    diagnostics = List.copyOf(diagnostics);
    methodOffsets = Map.copyOf(methodOffsets);
  }

  static DiagnosticOccurrences from(AnalysisRun run) {
    var sourceCount = run.representation().ast().diagnostics().size();
    var offset = sourceCount;
    var methods = new HashMap<String, Integer>();
    var cfg = run.representation().cfg();
    if (cfg.isPresent()) {
      var outcome = cfg.orElseThrow();
      if (outcome.state() == CfgState.REJECTED) {
        offset += outcome.diagnostics().size();
      } else {
        for (var declaration : run.representation().ast().result().orElseThrow().program().methods()) {
          for (var method : outcome.methods()) {
            if (method.methodKey().equals(declaration.methodKey())) {
              methods.put(method.methodKey(), offset);
              offset += method.diagnostics().size();
            }
          }
        }
      }
    }
    return new DiagnosticOccurrences(run.diagnostics(), methods, sourceCount, offset);
  }

  int source(int position) {
    return position + 1;
  }

  int request(int position) {
    return sourceCount + position + 1;
  }

  int method(String methodKey, int position) {
    return Objects.requireNonNull(methodOffsets.get(methodKey), "method diagnostic owner") + position + 1;
  }

  int application(int position) {
    return applicationOffset + position + 1;
  }
}
