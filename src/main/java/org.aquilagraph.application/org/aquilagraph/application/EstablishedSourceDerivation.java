package org.aquilagraph.application;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.application.AnalysisRequestIdentity.ExistingSelection;
import org.aquilagraph.cfg.api.CfgDeriver;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.source.api.AstOutcome;

/** Qualifies caller-bound derivation without producing source. */
final class EstablishedSourceDerivation implements ExistingSourceDerivation {
  private final CfgInvocation cfg;

  EstablishedSourceDerivation(CfgDeriver deriver) {
    this.cfg = new CfgInvocation(deriver);
  }

  @Override
  public AnalysisRun deriveExisting(AstOutcome ast, CfgRequest request) {
    Objects.requireNonNull(ast, "ast");
    Objects.requireNonNull(request, "request");
    var identity = new AnalysisRequestIdentity(AnalysisOperation.DERIVE_EXISTING, AnalysisRequestIdentity.sourceSubject(ast),
        new ExistingSelection(request));
    return AnalysisRun.create(identity, new ProgramRepresentation(ast, ast.result().map(ignored -> cfg.derive(ast, request))), List.of());
  }
}
