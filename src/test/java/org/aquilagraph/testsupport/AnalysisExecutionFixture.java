package org.aquilagraph.testsupport;

import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisRequestIdentity.ProductionSelection;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AstRetentionRepository;
import org.aquilagraph.application.port.AstRetentionRepositoryFactory;
import org.aquilagraph.source.api.AstProductionRequest;

/** Exercises coordinated publication with a fixed result from the computation port. */
public final class AnalysisExecutionFixture {
  private AnalysisExecutionFixture() {}

  public static AnalysisExecution executeComputed(AnalysisRun run, PublicationIntent intent,
      AstRetentionRepositoryFactory astRepositories, AnalysisRunRepositoryFactory runRepositories) {
    var ast = run.representation().ast();
    var source = new AstProductionRequest(ast.capturedInput().orElseThrow(), ast.requestedConfiguration().orElseThrow(), ast.profile());
    Optional<CfgSelection> selection = run.identity().selection() instanceof ProductionSelection produced
        ? Optional.of(produced.selection())
        : Optional.empty();
    var request = new AnalysisRequest(new AnalysisComputationRequest(source, selection), intent);
    return AnalysisCapabilities.execution(ignored -> run, astRepositories, runRepositories).execute(request);
  }

  public static AstRetentionRepository unexpectedAstRepository(RepositoryOperationContext context) {
    throw new AssertionError("Unexpected AST repository acquisition for " + context);
  }

  public static AnalysisRunRepository unexpectedRunRepository(RepositoryOperationContext context) {
    throw new AssertionError("Unexpected completed-run repository acquisition for " + context);
  }
}
