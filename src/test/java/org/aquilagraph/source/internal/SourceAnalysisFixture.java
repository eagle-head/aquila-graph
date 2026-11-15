package org.aquilagraph.source.internal;

import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.source.admission.AstProductionAdmission;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;

/** Admits copied frontend facts and drives native source and graph coordination. */
public final class SourceAnalysisFixture {
  /** Creates a fixture using standard source admission and CFG ports. */
  public SourceAnalysisFixture() {}

  /**
   * Coordinates an already admitted source result through the real application and CFG ports.
   * @param ast admitted immutable result
   * @return selected analysis run
   */
  public AnalysisRun analyze(AstResult ast) {
    return analyze(AstOutcome.available(ast));
  }

  /**
   * Admits actual compiler observations without reacquiring the compiler.
   * @param input copied frontend capture
   * @return native run with all produced bodies selected
   */
  public AnalysisRun analyze(SyntaxAnalysisInput input) {
    return analyze(AstProductionAdmission.outcome(input, AstProfile.JAVA_SOURCE_BOUNDED, "javac-public/1"));
  }

  /**
   * Coordinates an admitted source outcome without replacing its ownership.
   * @param ast exact admitted outcome
   * @return native run with all produced bodies selected
   */
  public AnalysisRun analyze(AstOutcome ast) {
    var source = new AstProductionRequest(ast.capturedInput().orElseThrow(), ast.requestedConfiguration().orElseThrow(), ast.profile());
    var request = new AnalysisComputationRequest(source, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)));
    return AnalysisCapabilities.computation(ignored -> ast, CfgDerivers.standard()).analyze(request);
  }
}
