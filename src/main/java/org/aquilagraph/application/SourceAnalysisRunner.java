package org.aquilagraph.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.AnalysisRequestIdentity.ExistingSelection;
import org.aquilagraph.application.AnalysisRequestIdentity.NotRequested;
import org.aquilagraph.application.AnalysisRequestIdentity.ProductionSelection;
import org.aquilagraph.cfg.api.CfgDerivationException;
import org.aquilagraph.cfg.api.CfgDeriver;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProducer;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.JavaMethod;

/** Coordinates public source and control-flow ports without owning either domain's policy. */
public final class SourceAnalysisRunner {
  private final AstProducer producer;
  private final CfgDeriver deriver;

  /**
   * Creates port-based coordination with no compiler or lowering implementation dependency.
   *
   * @param producer
   *          source production port
   * @param deriver
   *          immutable source interpretation port
   */
  public SourceAnalysisRunner(AstProducer producer, CfgDeriver deriver) {
    this.producer = Objects.requireNonNull(producer, "producer");
    this.deriver = Objects.requireNonNull(deriver, "deriver");
  }

  /**
   * Produces source and binds the unchanged optional selection after normal production.
   *
   * @param request
   *          requested source, selection, and independent publication intent
   * @return established source, optional CFG, and overall request qualification
   */
  public AnalysisRun analyze(AnalysisRequest request) {
    Objects.requireNonNull(request, "request");
    var ast = Objects.requireNonNull(
        producer.produce(new AstProductionRequest(request.input(), request.configuration(), request.astProfile())),
        "produced source outcome");
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
    var cfg = bound.map(value -> derive(ast, new CfgRequest(value, methods, unbound.profile())));
    return run(identity, ast, cfg);
  }

  /**
   * Derives from an established outcome without production or publication.
   *
   * @param ast
   *          exact established source outcome
   * @param request
   *          caller-bound request including any invalid selection
   * @return computed qualification retaining both caller request and source outcome
   */
  public AnalysisRun deriveExisting(AstOutcome ast, CfgRequest request) {
    Objects.requireNonNull(ast, "ast");
    Objects.requireNonNull(request, "request");
    var identity = new AnalysisRequestIdentity(AnalysisOperation.DERIVE_EXISTING, AnalysisRequestIdentity.sourceSubject(ast),
        new ExistingSelection(request));
    return run(identity, ast, ast.result().map(ignored -> derive(ast, request)));
  }

  private CfgOutcome derive(AstOutcome ast, CfgRequest request) {
    try {
      var outcome = Objects.requireNonNull(deriver.derive(ast.result().orElseThrow(), request), "derived CFG outcome");
      if (!request.equals(outcome.request())) {
        throw new IllegalStateException("CFG port returned a different requested identity or selection");
      }
      return outcome;
    } catch (CfgDerivationException exception) {
      if (!request.equals(exception.request())) {
        throw new IllegalStateException("CFG port returned a different requested identity or selection", exception);
      }
      throw new AnalysisDerivationException(ast, exception);
    }
  }

  private static AnalysisRun run(AnalysisRequestIdentity identity, AstOutcome ast, Optional<CfgOutcome> cfg) {
    return new AnalysisRun(identity, new ProgramRepresentation(ast, cfg), AnalysisQualification.assess(ast, cfg, List.of()), List.of());
  }
}
