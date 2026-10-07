package org.aquilagraph.cfg.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.BodyRef;
import org.aquilagraph.source.api.AnalysisDiagnostic;

/**
 * Retained result for one selected declaration, independent of AST completion.
 *
 * @param astIdentity
 *          exact source result
 * @param methodKey
 *          selected canonical declaration
 * @param state
 *          declaration derivation state
 * @param body
 *          admitted source association when available
 * @param graph
 *          validated graph only for AVAILABLE
 * @param blockers
 *          every ordered admission refusal
 * @param failure
 *          original declared recoverable construction cause only for FAILED
 * @param diagnostics
 *          ordered CFG diagnostic occurrences
 */
public record MethodCfgOutcome(AstIdentity astIdentity, String methodKey, MethodCfgState state, Optional<BodyRef> body,
    Optional<ControlFlowGraph> graph, List<CfgBlocker> blockers, Optional<Throwable> failure, List<AnalysisDiagnostic> diagnostics) {
  /** Enforces exclusive graph, refusal, and failure shapes. */
  public MethodCfgOutcome {
    Objects.requireNonNull(astIdentity, "astIdentity");
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(graph, "graph");
    blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
    Objects.requireNonNull(failure, "failure");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    diagnostics.forEach(CfgDiagnosticAdmission::requireCreatingStage);
    if (methodKey.isBlank() || (state == MethodCfgState.AVAILABLE) != graph.isPresent()
        || (state == MethodCfgState.FAILED) != failure.isPresent() || (state == MethodCfgState.FAILED && diagnostics.isEmpty())
        || ((state == MethodCfgState.AVAILABLE || state == MethodCfgState.FAILED) && body.isEmpty())
        || (state == MethodCfgState.NOT_APPLICABLE && body.isPresent())
        || ((state == MethodCfgState.AVAILABLE || state == MethodCfgState.NOT_APPLICABLE || state == MethodCfgState.FAILED) != blockers
            .isEmpty())) {
      throw new IllegalArgumentException("Method CFG outcome fields contradict its state");
    }
    body.ifPresent(value -> {
      if (!value.astIdentity().equals(astIdentity) || !value.methodKey().equals(methodKey)) {
        throw new IllegalArgumentException("Method CFG body belongs to another source declaration");
      }
    });
    graph.ifPresent(value -> {
      if (!value.methodKey().equals(methodKey) || !value.body().equals(body) || value.identity().isEmpty()
          || !value.identity().orElseThrow().astIdentity().equals(astIdentity)) {
        throw new IllegalArgumentException("Method CFG graph belongs to another declaration");
      }
    });
    if (graph.isPresent() && !graph.orElseThrow().diagnostics().equals(diagnostics)) {
      throw new IllegalArgumentException("Available method diagnostics must retain the graph's content-qualified occurrences");
    }
    if (blockers.stream().anyMatch(value -> !value.astIdentity().equals(astIdentity) || !value.methodKey().equals(methodKey))) {
      throw new IllegalArgumentException("Method CFG blockers belong to another source declaration");
    }
    if (!blockers.isEmpty() && (state != MethodCfgState.refusalSummary(blockers)
        || !diagnostics.equals(blockers.stream().map(CfgBlocker::diagnostic).toList()))) {
      throw new IllegalArgumentException("Method CFG refusal must retain its blocker summary and exact ordered diagnostics");
    }
  }

  boolean isQualifiedFor(CfgRequest request) {
    return astIdentity.equals(request.astIdentity()) && graph.map(value -> {
      var identity = value.identity().orElseThrow();
      var profile = request.profile();
      return profile.version().equals(identity.cfgProfileVersion()) && profile.graphModelVersion().equals(identity.graphModelVersion());
    }).orElse(true);
  }
}
