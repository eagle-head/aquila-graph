package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.junit.jupiter.api.Test;

class AnalysisRunValueContractTest {
  @Test
  void givenUnavailableSource_whenRunUsesForeignCompleteAssessment_thenActualQualificationRejectsIt() {
    // Arrange
    var rejected = TestAnalysisRuns.rejected();
    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new AnalysisRun(rejected.identity(),
        rejected.representation(), TestAnalysisRuns.completed().qualification(), List.of()));
    // Assert
    assertTrue(failure.getMessage().contains("actual context qualification"));
  }

  @Test
  void givenCompleteSource_whenRunUsesForeignRejectedAssessment_thenActualQualificationRejectsIt() {
    // Arrange
    var completed = TestAnalysisRuns.completed();
    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new AnalysisRun(completed.identity(),
        completed.representation(), TestAnalysisRuns.rejected().qualification(), List.of()));
    // Assert
    assertTrue(failure.getMessage().contains("actual context qualification"));
  }

  @Test
  void givenApplicationErrorsInRepeatedSlots_whenRunIsConstructed_thenFailedQualificationRetainsExactOwnersAndFrozenOccurrences() {
    // Arrange
    var original = TestAnalysisRuns.completed();
    var error = new AnalysisDiagnostic("diagnostic:provided", "provided.error", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, "Provided error", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var diagnostics = new ArrayList<>(List.of(error, error));
    // Act
    var run = AnalysisRun.create(original.identity(), original.representation(), diagnostics);
    diagnostics.clear();
    // Assert
    assertSame(original.identity(), run.identity());
    assertSame(original.representation(), run.representation());
    assertEquals(List.of(error, error), run.diagnostics());
    assertEquals(AnalysisCompletion.FAILED, run.completion());
    var reasons = run.qualification().reasons().stream()
        .map(RequestQualificationReason.ApplicationDiagnostic.class::cast).toList();
    assertEquals(List.of(0, 1), reasons.stream().map(RequestQualificationReason.ApplicationDiagnostic::position).toList());
    assertTrue(reasons.stream().allMatch(reason -> reason.owner() == run.applicationDiagnostics()));
    assertSame(error, reasons.getFirst().diagnostic());
    assertSame(error, reasons.getLast().diagnostic());
    assertEquals(run.qualification(), RequestQualification.assess(run.representation(), run.applicationDiagnostics()));
    assertEquals(run.qualification().hashCode(), RequestQualification.assess(run.representation(), run.applicationDiagnostics()).hashCode());
    assertFalse(run.isPublishable());
    assertThrows(UnsupportedOperationException.class, () -> run.applicationDiagnostics().clear());
    assertThrows(IllegalArgumentException.class, () -> new AnalysisRun(original.identity(), original.representation(),
        original.qualification(), List.of(error)));
  }

  @Test
  void givenSourceAndApplicationErrorsWithRejectedSelection_whenComposed_thenRejectionWinsAndEveryIndependentCauseSurvives() {
    // Arrange
    var partial = TestAnalysisRuns.fromSource("class Example { int field = 1; int value() { return 1; } }");
    var ast = partial.representation().ast();
    var request = new CfgRequest(ast.result().orElseThrow().identity(), List.of("unknown"), CfgProfile.JAVA_BOUNDED);
    var runner = new SourceAnalysisRunner(ignored -> {
      throw new AssertionError("Existing source must not be produced again");
    }, CfgDerivers.standard());
    var rejected = runner.deriveExisting(ast, request);
    var error = new AnalysisDiagnostic("provided:error", "provided.error", DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR,
        "Supplied application error", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);

    // Act
    var run = AnalysisRun.create(rejected.identity(), rejected.representation(), List.of(error, error));

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertEquals(AnalysisCompletion.REJECTED, run.completion());
    assertEquals(List.of(new RequestQualificationReason.Source(ast), new RequestQualificationReason.Selection(rejected.representation().cfg().orElseThrow()),
        new RequestQualificationReason.ApplicationDiagnostic(run.applicationDiagnostics(), 0),
        new RequestQualificationReason.ApplicationDiagnostic(run.applicationDiagnostics(), 1)), run.qualification().reasons());
    assertSame(request, run.representation().cfg().orElseThrow().request());
    assertFalse(run.isPublishable());
  }

  @Test
  void givenUnavailableAndUnsupportedSource_whenNativeRunsAreConstructed_thenAbsenceAndPartialResultsRemainDistinct() {
    // Arrange
    var unavailable = TestAnalysisRuns.rejected();
    var partial = TestAnalysisRuns.fromSource("class Example { void run() { while (true) {} } }");
    // Act
    var absent = unavailable.representation().ast().result();
    var present = partial.representation().ast().result();
    // Assert
    assertTrue(absent.isEmpty());
    assertTrue(present.isPresent());
    assertEquals(AnalysisCompletion.REJECTED, unavailable.completion());
    assertEquals(AnalysisCompletion.PARTIAL, partial.completion());
    assertFalse(unavailable.isPublishable());
    assertFalse(partial.isPublishable());
  }

  @Test
  void givenGraphForAnotherMethod_whenMethodOutcomeIsConstructed_thenExactMethodAssociationRejectsIt() {
    // Arrange
    var run = TestAnalysisRuns.fromSource("interface Example { static int value() { return 1; } }");
    var method = run.representation().cfg().orElseThrow().methods().getFirst();
    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new MethodCfgOutcome(method.astIdentity(), "foreign-method",
        MethodCfgState.AVAILABLE, method.body(), method.graph(), List.of(), method.failure(), method.diagnostics()));
    // Assert
    assertTrue(failure.getMessage().contains("another source declaration"));
  }
}
