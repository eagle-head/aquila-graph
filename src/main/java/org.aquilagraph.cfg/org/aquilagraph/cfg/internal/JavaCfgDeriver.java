package org.aquilagraph.cfg.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.cfg.api.CfgConstructionException;
import org.aquilagraph.cfg.api.CfgDerivationException;
import org.aquilagraph.cfg.api.CfgDeriver;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;

/** Stateless control-flow interpretation over the public source read model. */
public final class JavaCfgDeriver implements CfgDeriver {
  private final GraphConstruction construction;

  /** Creates compiler-independent graph construction. */
  public JavaCfgDeriver() {
    this(new ControlFlowGraphBuilder()::build);
  }

  JavaCfgDeriver(GraphConstruction construction) {
    this.construction = Objects.requireNonNull(construction, "construction");
  }

  @Override
  public CfgOutcome derive(AstResult ast, CfgRequest request) {
    Objects.requireNonNull(ast, "ast");
    Objects.requireNonNull(request, "request");
    var diagnostics = validateRequest(ast, request);
    if (!diagnostics.isEmpty()) {
      return new CfgOutcome(request, CfgState.REJECTED, List.of(), diagnostics);
    }
    var methods = new ArrayList<MethodCfgOutcome>();
    for (var methodKey : request.methodKeys()) {
      var outcome = deriveMethod(ast, request, methodKey, methods);
      methods.add(outcome);
      diagnostics.addAll(outcome.diagnostics());
    }
    var state = methods.stream()
        .allMatch(value -> value.state() == MethodCfgState.AVAILABLE || value.state() == MethodCfgState.NOT_APPLICABLE)
        && diagnostics.stream().noneMatch(AnalysisDiagnostic::isError) ? CfgState.COMPLETE : CfgState.PARTIAL;
    return new CfgOutcome(request, state, methods, diagnostics);
  }

  private MethodCfgOutcome deriveMethod(AstResult ast, CfgRequest request, String methodKey, List<MethodCfgOutcome> completed) {
    var admission = CfgAdmission.assess(ast, methodKey, request.profile());
    if (admission.plan().isEmpty()) {
      return new MethodCfgOutcome(ast.identity(), methodKey, admission.state(), admission.body(), Optional.empty(), admission.blockers(),
          Optional.empty(), admission.blockers().stream().map(value -> value.diagnostic()).toList());
    }
    try {
      return construction.build(ast, admission.plan().orElseThrow());
    } catch (CfgConstructionException exception) {
      if (exception.mode() == CfgConstructionException.Mode.ABRUPT) {
        throw interrupted(request, completed, exception);
      }
      var cause = Objects.requireNonNull(exception.getCause(), "effective construction cause");
      var diagnostic = diagnostic(request, "control-flow.construction-failed", methodKey, "Admitted control-flow construction failed");
      return new MethodCfgOutcome(ast.identity(), methodKey, MethodCfgState.FAILED, admission.body(), Optional.empty(), List.of(),
          Optional.of(cause), List.of(diagnostic));
    }
  }

  private static CfgDerivationException interrupted(CfgRequest request, List<MethodCfgOutcome> completed,
      CfgConstructionException exception) {
    return new CfgDerivationException(request, completed, Objects.requireNonNull(exception.getCause(), "effective construction cause"));
  }

  private static List<AnalysisDiagnostic> validateRequest(AstResult ast, CfgRequest request) {
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    if (!ast.identity().equals(request.astIdentity())) {
      diagnostics.add(
          diagnostic(request, "control-flow.foreign-ast", "identity", "Requested source identity differs from the supplied AST result"));
    }
    if (!AstResult.supportsSourceModelVersion(request.astIdentity().sourceModelVersion())
        || !CfgAdmission.supportsSourceProfile(ast.profile()) || !CfgAdmission.supportsProfile(request.profile())) {
      diagnostics.add(diagnostic(request, "control-flow.incompatible-profile", "profile",
          "Requested source and graph interpretation versions are incompatible"));
    }
    var selected = new HashSet<String>();
    for (var index = 0; index < request.methodKeys().size(); index++) {
      var key = request.methodKeys().get(index);
      if (!selected.add(key)) {
        diagnostics.add(diagnostic(request, "control-flow.duplicate-selection", "selection/" + index,
            "The requested selection repeats a canonical method key"));
      }
      if (key.isBlank() || ast.method(key).isEmpty()) {
        diagnostics.add(diagnostic(request, "control-flow.unknown-method", "selection/" + index,
            "The requested selection contains an unknown canonical method key"));
      }
    }
    return diagnostics;
  }

  private static AnalysisDiagnostic diagnostic(CfgRequest request, String code, String slot, String message) {
    return new AnalysisDiagnostic(
        StableId.sha256("cfg-request-diagnostic", request.astIdentity().astResultKey(), request.profile().version(),
            request.profile().graphModelVersion(), code, slot),
        code, DiagnosticStage.CONTROL_FLOW, DiagnosticSeverity.ERROR, message, List.of(), AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
  }
}
