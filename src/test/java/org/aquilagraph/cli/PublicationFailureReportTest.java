package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AstLookup;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.port.AstResultRepository;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.junit.jupiter.api.Test;

class PublicationFailureReportTest {
  @Test
  void givenFailedCfgAndAstPublicationWithSharedCause_whenRealApplicationReportIsPrepared_thenRootOrderAndDifferentCommitObservationsRemainExact()
      throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("interface Example { static void run() {} }");
    var source = ast.result().orElseThrow();
    var methodKey = source.program().methods().getFirst().methodKey();
    for (var knowledge : List.of(CommitKnowledge.UNKNOWN, CommitKnowledge.KNOWN_COMMIT)) {
      var shared = new IllegalStateException("shared cause");
      var cfgFailure = new IllegalStateException("CFG construction", shared);
      var repositoryFailure = new RepositoryException("AST save failed", shared);
      var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), RecoverableCfgFailures.atMethod(methodKey, cfgFailure));
      var useCase = AnalysisCapabilities.execution(computation, context -> new AstResultRepository() {
        @Override public void saveComplete(AstOutcome outcome) {
          if (knowledge == CommitKnowledge.UNKNOWN) {
            throw new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPLACEMENT, knowledge, repositoryFailure);
          }
        }
        @Override public AstLookup find(AstIdentity identity) { throw new AssertionError("Unexpected lookup"); }
        @Override public void close() {
          if (knowledge == CommitKnowledge.KNOWN_COMMIT) {
            throw new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPOSITORY_CLOSE, knowledge, repositoryFailure);
          }
        }
      }, context -> { throw new AssertionError("Unexpected completed-run repository"); });
      var request = new AnalysisRequest(new AnalysisComputationRequest(
          new AstProductionRequest(source.snapshot(), source.environment().compilerConfiguration(), ast.profile()),
          Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))), PublicationIntent.SAVE_COMPLETE_AST);

      // Act
      var execution = useCase.execute(request);
      var outcome = new AnalyzeCommandOutcome.Executed(execution);
      var report = assertInstanceOf(CommandReport.Executed.class, new CommandReportProjector(failure -> Optional.empty()).project(outcome));
      var prepared = CliTestFixtures.prepared(outcome);

      // Assert
      var failed = report.qualifications().stream().filter(ReportQualification.Failed.class::isInstance)
          .map(ReportQualification.Failed.class::cast).findFirst().orElseThrow();
      assertEquals(1, failed.failureReference());
      assertEquals(3, report.publication().failureReference().orElseThrow());
      assertEquals(Optional.of(knowledge), report.publication().knowledge());
      assertEquals(Optional.of(knowledge), report.failures().node(4).operation().orElseThrow().knowledge());
      var expected = new ArrayList<>(ReportTextExpectations.node(1, "java.lang.IllegalStateException", "\"CFG construction\"", "2",
          "not-applicable", "not-applicable", List.of(), List.of("  Operation: not-applicable")));
      expected.addAll(ReportTextExpectations.simpleNode(2, "java.lang.IllegalStateException", "shared cause"));
      expected.addAll(ReportTextExpectations.node(3, "org.aquilagraph.application.PublicationFailure", "\"Publication failed\"", "4", "4", "5",
          List.of(), List.of("  Operation: not-applicable")));
      var operation = new ArrayList<>(List.of("  Operation: SAVE_AST"));
      operation.addAll(ReportTextExpectations.astSubject("Operation", source.identity()).stream().map(line -> "  " + line).toList());
      operation.addAll(List.of("  Commit knowledge: " + knowledge, "  Application boundary: not-applicable",
          "  Adapter: not-applicable", "  Adapter phase: not-applicable"));
      expected.addAll(ReportTextExpectations.node(4, "org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException", "\"Repository operation failed\"",
          "5", "not-applicable", "5", List.of(), operation));
      expected.addAll(ReportTextExpectations.node(5, "org.aquilagraph.application.port.RepositoryException", "\"AST save failed\"", "2",
          "not-applicable", "not-applicable", List.of(), List.of("  Operation: not-applicable")));
      var lines = prepared.error().lines().toList();
      assertEquals(expected, lines.subList(lines.indexOf("Failure: 1"), lines.size()));
      var expectedPublication = new ArrayList<>(List.of("Repository: FAILED", "Publication intent: SAVE_COMPLETE_AST"));
      expectedPublication.addAll(ReportTextExpectations.astSubject("Publication", source.identity()));
      expectedPublication.add("Commit knowledge: " + knowledge);
      var output = prepared.output().lines().toList();
      assertEquals(expectedPublication, output.subList(output.size() - expectedPublication.size(), output.size()));
    }
  }
}
