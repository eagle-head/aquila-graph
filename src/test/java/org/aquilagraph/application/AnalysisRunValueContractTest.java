package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgState;
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
  void givenWarningAndErrorInABodylessMethod_whenAssessingTheRequest_thenOnlyTheErrorBlocksAndBothNativeSlotsRemain() {
    // Arrange
    var source = TestAnalysisRuns.fromSource("interface Example { void absent(); }").representation().ast();
    var ast = source.result().orElseThrow();
    var key = ast.program().methods().getFirst().methodKey();
    var warning = new AnalysisDiagnostic("diagnostic:warning", "control-flow.notice", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.WARNING, "Method notice", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var error = new AnalysisDiagnostic("diagnostic:error", "control-flow.error", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, "Method error", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var diagnostics = List.of(warning, error);
    var method = new MethodCfgOutcome(ast.identity(), key, MethodCfgState.NOT_APPLICABLE, Optional.empty(), Optional.empty(),
        List.of(), Optional.empty(), diagnostics);
    var request = new CfgRequest(ast.identity(), List.of(key), CfgProfile.JAVA_BOUNDED);
    var cfg = new CfgOutcome(request, CfgState.PARTIAL, List.of(method), diagnostics);

    // Act
    var qualification = RequestQualification.assess(new ProgramRepresentation(source, Optional.of(cfg)), List.of());

    // Assert
    assertEquals(AnalysisCompletion.PARTIAL, qualification.completion());
    assertEquals(List.of(new RequestQualificationReason.MethodDiagnostic(method, 1)), qualification.reasons());
    assertEquals(diagnostics, method.diagnostics());
  }

  @Test
  void givenEquivalentAndDifferentNativeContexts_whenComparingAssessments_thenEqualityAndHashingFollowTheCapturedContext() {
    // Arrange
    var run = TestAnalysisRuns.completed();
    var representation = run.representation();
    var error = new AnalysisDiagnostic("diagnostic:application", "application.error", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, "Application error", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var sourceOnly = new ProgramRepresentation(representation.ast(), Optional.empty());

    // Act
    var first = RequestQualification.assess(representation, List.of());
    var equal = RequestQualification.assess(representation, List.of());
    var changedDiagnostic = RequestQualification.assess(representation, List.of(error));
    var changedContext = RequestQualification.assess(sourceOnly, List.of());

    // Assert
    assertTrue(first.equals(first));
    assertEquals(first, equal);
    assertEquals(first.hashCode(), equal.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals("assessment"));
    assertFalse(first.equals(changedDiagnostic));
    assertFalse(first.equals(changedContext));
    assertFalse(first.equals(TestAnalysisRuns.rejected().qualification()));
    assertTrue(first.matches(representation, List.of()));
    assertFalse(first.matches(sourceOnly, List.of()));
    assertFalse(changedContext.matches(representation, List.of()));
    assertFalse(first.matches(representation, List.of(error)));
  }

  @Test
  void givenDiagnosticPositionsOutsideTheirNativeOwners_whenConstructingReasons_thenBothBoundsAreRejected() {
    // Arrange
    var run = TestAnalysisRuns.completed();
    var method = TestAnalysisRuns.fromSource("interface Example { static void run() {} }")
        .representation().cfg().orElseThrow().methods().getFirst();
    var diagnostic = new AnalysisDiagnostic("diagnostic:application", "application.error", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, "Application error", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var diagnostics = List.of(diagnostic);

    // Act
    var negativeMethod = assertThrows(IllegalArgumentException.class,
        () -> new RequestQualificationReason.MethodDiagnostic(method, -1));
    var missingMethod = assertThrows(IllegalArgumentException.class,
        () -> new RequestQualificationReason.MethodDiagnostic(method, method.diagnostics().size()));
    var negativeApplication = assertThrows(IllegalArgumentException.class,
        () -> new RequestQualificationReason.ApplicationDiagnostic(diagnostics, -1));
    var missingApplication = assertThrows(IllegalArgumentException.class,
        () -> new RequestQualificationReason.ApplicationDiagnostic(diagnostics, diagnostics.size()));

    // Assert
    assertEquals("A method error requires an existing diagnostic slot", negativeMethod.getMessage());
    assertEquals(negativeMethod.getMessage(), missingMethod.getMessage());
    assertEquals("An application error requires an existing diagnostic slot", negativeApplication.getMessage());
    assertEquals(negativeApplication.getMessage(), missingApplication.getMessage());
  }

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
    assertEquals(List.of(error, error), run.diagnostics().values());
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
