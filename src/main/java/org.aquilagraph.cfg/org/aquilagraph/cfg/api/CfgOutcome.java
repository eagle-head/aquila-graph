package org.aquilagraph.cfg.api;

import org.aquilagraph.cfg.internal.values.CfgDiagnosticAdmission;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;

/**
 * Exact request, ordered method results, and separately retained CFG diagnostics.
 *
 * @param request
 *          immutable original request including rejected selection
 * @param state
 *          request qualification
 * @param methods
 *          ordered selected-declaration results
 * @param diagnostics
 *          ordered request and method diagnostic occurrences
 */
public record CfgOutcome(CfgRequest request, CfgState state, List<MethodCfgOutcome> methods, List<AnalysisDiagnostic> diagnostics) {
  /** Requires canonical selection order and truthful aggregate qualification. */
  public CfgOutcome {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(state, "state");
    methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    diagnostics.forEach(CfgDiagnosticAdmission::requireCreatingStage);
    if (state == CfgState.REJECTED) {
      if (!methods.isEmpty() || diagnostics.isEmpty()) {
        throw new IllegalArgumentException("A rejected CFG request requires diagnostics and no method results");
      }
    } else {
      if (!request.hasUniqueMethodSelection()) {
        throw new IllegalArgumentException("A non-rejected CFG outcome requires unique selected method keys");
      }
      if (!methods.stream().map(MethodCfgOutcome::methodKey).toList().equals(request.methodKeys())
          || !diagnostics.equals(methods.stream().flatMap(value -> value.diagnostics().stream()).toList())
          || methods.stream().anyMatch(value -> !value.isQualifiedFor(request))) {
        throw new IllegalArgumentException("CFG results must match the exact ordered request");
      }
      var complete = methods.stream()
          .allMatch(value -> value.state() == MethodCfgState.AVAILABLE || value.state() == MethodCfgState.NOT_APPLICABLE)
          && diagnostics.stream().noneMatch(AnalysisDiagnostic::isError);
      if ((state == CfgState.COMPLETE) != complete) {
        throw new IllegalArgumentException("CFG completion contradicts selected method outcomes");
      }
    }
  }

}
