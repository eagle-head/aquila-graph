package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.junit.jupiter.api.Test;

class DiagnosticAssociationsTest {
  @Test
  void givenEqualDistinctDiagnosticsAndSharedContexts_whenAssociatedAndFinalized_thenIdentityOrderAndSnapshotsRemainExact() {
    // Arrange
    var first = diagnostic("same-value");
    var second = diagnostic("same-value");
    var firstEvent = entry("first-owner", first);
    var secondEvent = entry("second-owner", second);
    var associations = new DiagnosticAssociations(new DiagnosticEmissions(List.of(firstEvent, secondEvent)));
    var firstContext = context(0);
    var secondContext = context(1);

    // Act
    var firstAddress = associations.associateKnown(first, firstContext);
    associations.associateKnown(first, secondContext);
    associations.associateKnown(first, firstContext);
    var secondAddress = associations.associateKnown(second, secondContext);
    var frozen = associations.finish();
    var later = diagnostic("later-value");
    associations.createFailure(later, firstContext);
    var completed = associations.finish();

    // Assert
    assertEquals(first, second);
    assertNotSame(first, second);
    assertEquals(firstEvent.event().address(), firstAddress);
    assertEquals(secondEvent.event().address(), secondAddress);
    assertEquals(List.of(firstEvent.event().address(), secondEvent.event().address()),
        frozen.events().stream().map(DiagnosticEmission::address).toList());
    assertEquals(List.of(0, 1), frozen.events().stream().map(DiagnosticEmission::slot).toList());
    assertEquals(List.of(firstContext, secondContext), frozen.events().getFirst().contexts());
    assertEquals(List.of(secondContext), frozen.events().getLast().contexts());
    assertSame(first, frozen.diagnostics().getFirst());
    assertSame(second, frozen.diagnostics().getLast());
    assertEquals(first.outcomeKind(), frozen.events().getFirst().cause());
    assertEquals(first.origins(), frozen.events().getFirst().diagnostic().origins());
    assertEquals(List.of(first, second), frozen.diagnostics());
    assertEquals(List.of(first, second, later), completed.diagnostics());
    assertEquals(List.of(0, 1, 2), completed.events().stream().map(DiagnosticEmission::slot).toList());
    assertThrows(UnsupportedOperationException.class, () -> frozen.events().clear());
    assertThrows(UnsupportedOperationException.class, () -> frozen.events().getFirst().contexts().clear());
  }

  @Test
  void givenUnregisteredEqualDiagnostic_whenItsPolicyContextIsAssociated_thenMissingInstanceIsRejectedWithoutChangingEvents() {
    // Arrange
    var diagnostic = diagnostic("same-value");
    var associations = new DiagnosticAssociations(new DiagnosticEmissions(List.of(entry("owner", diagnostic))));
    var before = associations.finish();

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> associations.associateKnown(diagnostic("same-value"), context(0)));

    // Assert
    assertEquals("Known policy diagnostics require creating-site event transport", failure.getMessage());
    assertEquals(before, associations.finish());
    assertSame(diagnostic, associations.finish().diagnostics().getFirst());
  }

  @Test
  void givenOneDiagnosticInstanceAtTwoCreatingAddresses_whenRegistered_thenRemintingIsRejected() {
    // Arrange
    var diagnostic = diagnostic("shared-instance");
    var emissions = new DiagnosticEmissions(List.of(entry("first-owner", diagnostic), entry("second-owner", diagnostic)));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new DiagnosticAssociations(emissions));

    // Assert
    assertEquals("One diagnostic instance cannot be reminted as a second emission", failure.getMessage());
    assertEquals(2, emissions.entries().size());
    assertSame(diagnostic, emissions.entries().getFirst().event().diagnostic());
    assertSame(diagnostic, emissions.entries().getLast().event().diagnostic());
  }

  @Test
  void givenOccupiedFailureAddress_whenAnotherEventIsRegistered_thenConflictRejectsWithoutReplacingTheOriginal() {
    // Arrange
    var context = context(0);
    var original = diagnostic("original");
    var address = new DiagnosticEmission.Address("JavaSourceCoverageReconciler", context.toString(), 1);
    var event = new DiagnosticEmission(address, original, original.outcomeKind(), List.of(), -1);
    var associations = new DiagnosticAssociations(new DiagnosticEmissions(List.of(new DiagnosticEntry(event, DiagnosticSubject.None.INSTANCE))));
    var before = associations.finish();

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> associations.createFailure(diagnostic("conflicting"), context));

    // Assert
    assertEquals("One creating address cannot identify different diagnostic entries", failure.getMessage());
    assertEquals(before, associations.finish());
    assertSame(original, associations.finish().diagnostics().getFirst());
    assertEquals(List.of(), associations.finish().events().getFirst().contexts());
  }

  private static DiagnosticEntry entry(String owner, AnalysisDiagnostic diagnostic) {
    return new DiagnosticEntry(new DiagnosticEmission(new DiagnosticEmission.Address("diagnostic-source", owner, 0), diagnostic,
        diagnostic.outcomeKind(), List.of(), -1), DiagnosticSubject.None.INSTANCE);
  }

  private static SourceContext.Address context(int ordinal) {
    return new SourceContext.Address("captured-source", SourceContext.Partition.TREE, SourceOwnerPath.root(), "tree", ordinal);
  }

  private static AnalysisDiagnostic diagnostic(String key) {
    return new AnalysisDiagnostic(key, "frontend.observation", DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR,
        "Source observation", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }
}
