package org.aquilagraph.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.StableId;

public final class ControlFlowGraphBuilder {
  public ControlFlowGraphResult build(JavaMethod method) {
    Objects.requireNonNull(method, "method");
    if (method.body().isEmpty()) {
      return new ControlFlowGraphResult(Optional.empty(), List.of());
    }
    if (!method.body().orElseThrow().supported()) {
      var message = "Method body contains Java constructs outside the supported subset";
      var diagnostic =
          new AnalysisDiagnostic(
              StableId.sha256("diagnostic", method.methodKey(), message),
              "control-flow.unsupported-method",
              DiagnosticStage.CONTROL_FLOW,
              DiagnosticSeverity.ERROR,
              message,
              method.origins());
      return new ControlFlowGraphResult(Optional.empty(), List.of(diagnostic));
    }
    return new MethodGraphLowerer(method).lower();
  }
}
