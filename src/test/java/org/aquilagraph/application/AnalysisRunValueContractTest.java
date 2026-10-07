package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.junit.jupiter.api.Test;

class AnalysisRunValueContractTest {
  @Test
  void givenUnavailableSource_whenRunClaimsComplete_thenActualQualificationRejectsIt() {
    // Arrange
    var rejected = TestAnalysisRuns.rejected();
    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new AnalysisRun(rejected.identity(),
        rejected.representation(), AnalysisCompletion.COMPLETE, List.of()));
    // Assert
    assertTrue(failure.getMessage().contains("actual context qualification"));
  }

  @Test
  void givenCompleteSource_whenRunClaimsRejected_thenActualQualificationRejectsIt() {
    // Arrange
    var completed = TestAnalysisRuns.completed();
    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new AnalysisRun(completed.identity(),
        completed.representation(), AnalysisCompletion.REJECTED, List.of()));
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
    var run = new AnalysisRun(original.identity(), original.representation(), AnalysisCompletion.FAILED, diagnostics);
    diagnostics.clear();
    // Assert
    assertSame(original.identity(), run.identity());
    assertSame(original.representation(), run.representation());
    assertEquals(List.of(error, error), run.diagnostics());
    assertFalse(run.isPublishable());
    assertThrows(UnsupportedOperationException.class, () -> run.applicationDiagnostics().clear());
    assertThrows(IllegalArgumentException.class, () -> new AnalysisRun(original.identity(), original.representation(),
        AnalysisCompletion.COMPLETE, List.of(error)));
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
