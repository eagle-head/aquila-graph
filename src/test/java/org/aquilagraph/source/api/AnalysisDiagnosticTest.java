package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.identity.api.SourceSpan;
import org.junit.jupiter.api.Test;

class AnalysisDiagnosticTest {
  @Test
  void givenProducerCauseAndUnrelatedCode_whenConstructing_thenExplicitMeaningIsIndependentOfCode() {
    // Arrange
    var code = "control-flow.erroneous-semantics";
    var causes = List.of(AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE, AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE,
        AnalysisOutcomeKind.INPUT_REJECTION, AnalysisOutcomeKind.PERSISTENCE_FAILURE, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);

    // Act
    var diagnostics = causes.stream().map(cause -> new AnalysisDiagnostic("diagnostic:provided", code, DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, "Provided observation", List.of(), cause)).toList();

    // Assert
    assertEquals(causes, diagnostics.stream().map(AnalysisDiagnostic::outcomeKind).toList());
    assertEquals(List.of(code, code, code, code, code), diagnostics.stream().map(AnalysisDiagnostic::code).toList());
  }

  @Test
  void givenMutableOriginsAndExplicitCause_whenConstructing_thenOriginsAreFrozenAndCauseParticipatesInValueSemantics() {
    // Arrange
    var origin = new SourceSpan("source:provided", 0, 4);
    var origins = new ArrayList<>(List.of(origin, origin));

    // Act
    var diagnostic = new AnalysisDiagnostic("diagnostic:provided", "provided.observation", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.WARNING, "Provided observation", origins, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    origins.clear();
    var same = new AnalysisDiagnostic("diagnostic:provided", "provided.observation", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.WARNING, "Provided observation", List.of(origin, origin), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var otherCause = new AnalysisDiagnostic("diagnostic:provided", "provided.observation", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.WARNING, "Provided observation", List.of(origin, origin), AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE);

    // Assert
    assertEquals(List.of(origin, origin), diagnostic.origins());
    assertThrows(UnsupportedOperationException.class, () -> diagnostic.origins().clear());
    assertEquals(same, diagnostic);
    assertEquals(same.hashCode(), diagnostic.hashCode());
    assertNotEquals(otherCause, diagnostic);
  }

  @Test
  void givenNonDiagnosticLanguageOutcome_whenConstructing_thenIntrinsicConsistencyRejectsIt() {
    // Arrange
    var origins = List.<SourceSpan>of();
    var causes = List.of(AnalysisOutcomeKind.REPRESENTED_LANGUAGE_FEATURE, AnalysisOutcomeKind.IRRELEVANT_LANGUAGE_FEATURE);

    for (var cause : causes) {
      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> new AnalysisDiagnostic("diagnostic:provided", "provided.observation",
          DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, "Provided observation", origins, cause));

      // Assert
      assertEquals("Non-error language-feature outcomes cannot be attached to diagnostics", failure.getMessage());
    }
  }

  @Test
  void givenRestrictedCause_whenConstructingAcrossStagesAndSeverities_thenOnlyItsDeclaredErrorBoundaryIsValid() {
    // Arrange
    var causes = List.of(AnalysisOutcomeKind.COMPILER_ERROR, AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE,
        AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);

    for (var cause : causes) {
      var owner = cause == AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE ? DiagnosticStage.CONTROL_FLOW : DiagnosticStage.FRONTEND;
      // Act
      var diagnostic = new AnalysisDiagnostic("diagnostic:provided", "provided.observation", owner, DiagnosticSeverity.ERROR,
          "Provided observation", List.of(), cause);

      // Assert
      assertEquals(cause, diagnostic.outcomeKind());
      for (var stage : DiagnosticStage.values()) {
        for (var severity : DiagnosticSeverity.values()) {
          if (stage != owner || severity != DiagnosticSeverity.ERROR) {
            assertThrows(IllegalArgumentException.class, () -> new AnalysisDiagnostic("diagnostic:provided", "provided.observation", stage,
                severity, "Provided observation", List.of(), cause));
          }
        }
      }
    }
  }
}
