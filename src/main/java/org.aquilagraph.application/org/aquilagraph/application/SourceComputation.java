package org.aquilagraph.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.AnalysisRequestIdentity.NotRequested;
import org.aquilagraph.application.AnalysisRequestIdentity.ProductionSelection;
import org.aquilagraph.cfg.api.CfgDeriver;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProducer;
import org.aquilagraph.source.api.JavaMethod;

/** Coordinates public source and control-flow ports without owning either domain's policy. */
final class SourceComputation implements AnalysisComputation {
  private final AstProducer producer;
  private final CfgInvocation cfg;

  SourceComputation(AstProducer producer, CfgDeriver deriver) {
    this.producer = Objects.requireNonNull(producer, "producer");
    this.cfg = new CfgInvocation(deriver);
  }

  @Override
  public AnalysisRun analyze(AnalysisComputationRequest request) {
    Objects.requireNonNull(request, "request");
    var ast = Objects.requireNonNull(producer.produce(request.source()), "produced source outcome");
    var selection = request.cfgSelection();
    if (selection.isEmpty()) {
      var identity = new AnalysisRequestIdentity(AnalysisOperation.AST_ONLY, AnalysisRequestIdentity.sourceSubject(ast),
          NotRequested.INSTANCE);
      return run(identity, ast, Optional.empty());
    }
    var unbound = selection.orElseThrow();
    var bound = ast.result().map(result -> result.identity());
    var methods = switch (unbound.scope()) {
      case METHODS -> unbound.methodKeys();
      case ALL_BODIES ->
        ast.result().map(result -> result.program().methods().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey).toList())
            .orElseGet(List::of);
    };
    var identity = new AnalysisRequestIdentity(AnalysisOperation.PRODUCTION_WITH_CFG, AnalysisRequestIdentity.sourceSubject(ast),
        new ProductionSelection(bound, unbound, methods));
    var outcome = bound.map(value -> cfg.derive(ast, new CfgRequest(value, methods, unbound.profile())));
    return run(identity, ast, outcome);
  }

  private static AnalysisRun run(AnalysisRequestIdentity identity, AstOutcome ast, Optional<CfgOutcome> cfg) {
    return AnalysisRun.create(identity, new ProgramRepresentation(ast, cfg), List.of());
  }
}
