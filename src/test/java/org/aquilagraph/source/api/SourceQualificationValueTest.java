package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SourceQualificationValueTest {
  @Test
  void givenContradictoryCompletionAndReasons_whenConstructingQualification_thenTheDecisionIsRejected() {
    // Arrange
    var reason = new SourceQualificationReason(SourceQualificationReason.Category.EMPTY_SOURCE,
        SourceQualificationReason.EvidenceRole.SOURCE_CAPTURE, SourceQualificationReason.Capture.INSTANCE,
        List.of(), List.of(), List.of());

    // Act
    var complete = assertThrows(IllegalArgumentException.class, () -> new SourceQualification(AstCompletion.COMPLETE,
        AstProfile.JAVA_SOURCE_BOUNDED, Optional.empty(), List.of(reason)));
    var incomplete = assertThrows(IllegalArgumentException.class, () -> new SourceQualification(AstCompletion.INCOMPLETE,
        AstProfile.JAVA_SOURCE_BOUNDED, Optional.empty(), List.of()));

    // Assert
    assertEquals("Source completion must agree with its blocking reasons", complete.getMessage());
    assertEquals(complete.getMessage(), incomplete.getMessage());
  }

  @Test
  void givenRepeatedNativeDiagnosticSlots_whenReferencingAndMutatingTheSuppliedList_thenExactFrozenOccurrencesAndBoundsArePreserved() {
    // Arrange
    var diagnostic = new AnalysisDiagnostic("diagnostic:source", "source.error", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, "Source error", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var owner = new ArrayList<>(List.of(diagnostic, diagnostic));

    // Act
    var first = new SourceQualificationReason.DiagnosticReference(owner, 0, Optional.empty());
    var second = new SourceQualificationReason.DiagnosticReference(owner, 1, Optional.empty());
    var negative = assertThrows(IllegalArgumentException.class,
        () -> new SourceQualificationReason.DiagnosticReference(owner, -1, Optional.empty()));
    var pastEnd = assertThrows(IllegalArgumentException.class,
        () -> new SourceQualificationReason.DiagnosticReference(owner, 2, Optional.empty()));
    owner.clear();

    // Assert
    assertSame(diagnostic, first.diagnostic());
    assertSame(diagnostic, second.diagnostic());
    assertEquals(List.of(diagnostic, diagnostic), first.owner());
    assertEquals(0, first.position());
    assertEquals(1, second.position());
    assertEquals("A diagnostic reference requires an existing owning slot", negative.getMessage());
    assertEquals(negative.getMessage(), pastEnd.getMessage());
    assertThrows(UnsupportedOperationException.class, () -> first.owner().clear());
  }
}
