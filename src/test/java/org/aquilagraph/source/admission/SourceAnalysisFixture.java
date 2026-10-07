package org.aquilagraph.source.admission;

import java.util.Optional;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxAnalysisInput;

/** Admits copied frontend facts and drives native source and graph coordination. */
public final class SourceAnalysisFixture {
  /** Creates a fixture using standard source admission and CFG ports. */
  public SourceAnalysisFixture() {}

  /**
   * Admits actual compiler observations without reacquiring the compiler.
   * @param input copied frontend capture
   * @return native run with all produced bodies selected
   */
  public AnalysisRun analyze(SyntaxAnalysisInput input) {
    return analyze(AstResultAdmission.outcome(input, AstProfile.JAVA_SOURCE_BOUNDED, "javac-public/1"));
  }

  /**
   * Coordinates an admitted source outcome without replacing its ownership.
   * @param ast exact admitted outcome
   * @return native run with all produced bodies selected
   */
  public AnalysisRun analyze(AstOutcome ast) {
    var request = new AnalysisRequest(new SourceInput.Snapshot(ast.capturedInput().orElseThrow()),
        ast.requestedConfiguration().orElseThrow(), ast.profile(), Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)),
        PublicationIntent.DO_NOT_SAVE);
    return new SourceAnalysisRunner(ignored -> ast, CfgDerivers.standard()).analyze(request);
  }
}
