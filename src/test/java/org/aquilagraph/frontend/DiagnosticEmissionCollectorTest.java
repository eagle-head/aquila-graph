package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.junit.jupiter.api.Test;

class DiagnosticEmissionCollectorTest {
  @Test
  void givenLargestCreatingOrdinal_whenImportedAndAllocationIsExhausted_thenRetainedEventsRemainValidAndOtherProducersStillAllocate() {
    // Arrange
    var largest = event("body", "unit", Integer.MAX_VALUE);
    var collector = new DiagnosticEmissionCollector("body", "unit");

    // Act
    collector.include(new DiagnosticEmissions(List.of(largest)));
    var retained = collector.snapshot();
    assertThrows(ArithmeticException.class, () -> collector.emit(note("exhausted")));
    collector.emit("annotation", note("independent"));
    collector.startBatch();
    assertThrows(ArithmeticException.class, () -> collector.emit(note("still-exhausted")));
    collector.emit("annotation", note("next-independent"));

    // Assert
    assertEquals(List.of(largest), retained.events());
    assertEquals(List.of(new DiagnosticEmission.Address("annotation", "unit", 1)), collector.snapshot().events().stream()
        .map(DiagnosticEmission::address).toList());
  }

  @Test
  void givenImportedProducerAndOwnerAddresses_whenCollectionContinues_thenOnlyMatchingAllocationAdvances() {
    // Arrange
    var imported = List.of(event("body", "unit", 2), event("body", "unit", 7), event("body", "another-unit", 90),
        event("annotation", "unit", 4), event("annotation", "another-unit", 100));
    var collector = new DiagnosticEmissionCollector("body", "unit");

    // Act
    collector.include(DiagnosticEmissions.empty());
    collector.include(new DiagnosticEmissions(imported));
    collector.include(new DiagnosticEmissions(imported));
    collector.emit(note("next-body"));
    collector.emit("annotation", note("next-annotation"));
    collector.emit("type-use", note("first-type-use"));
    var actual = collector.snapshot().events();

    // Assert
    assertEquals(imported, actual.subList(0, imported.size()));
    for (var index = 0; index < imported.size(); index++) {
      assertSame(imported.get(index), actual.get(index));
    }
    assertEquals(List.of(new DiagnosticEmission.Address("body", "unit", 8), new DiagnosticEmission.Address("annotation", "unit", 5),
        new DiagnosticEmission.Address("type-use", "unit", 0)), actual.subList(imported.size(), actual.size()).stream()
            .map(DiagnosticEmission::address).toList());
  }

  @Test
  void givenCallerAndCollectorAliases_whenSnapshotsReturnThroughResultsAndFailures_thenMembershipAndInstancesRemainImmutable() {
    // Arrange
    var original = event("body", "unit", 0);
    var caller = new ArrayList<>(List.of(original));
    var batch = new DiagnosticEmissions(caller);
    var collector = new DiagnosticEmissionCollector("body", "unit");
    collector.include(batch);
    var result = new TranslationResult<>("translated", collector.snapshot());
    var failure = new JavaSourceProjectionFailure(collector.snapshot());

    // Act
    caller.clear();
    collector.emit(note("later"));
    var joined = DiagnosticEmissions.combine(result.emissions(), batch, failure.emissions(), result.emissions());

    // Assert
    assertEquals(List.of(original), joined.events());
    assertEquals(List.of(original), result.emissions().events());
    assertSame(original, joined.events().getFirst());
    assertSame(original.diagnostic(), result.diagnostics().getFirst());
    assertSame(original.diagnostic(), failure.emissions().diagnostics().getFirst());
    assertEquals(2, collector.emissionCount());
    assertThrows(UnsupportedOperationException.class, () -> batch.events().clear());
    assertThrows(UnsupportedOperationException.class, () -> result.diagnostics().clear());
  }

  @Test
  void givenConflictingEventsForOneAddress_whenBatchOrCollectorImportsThem_thenConflictIsRejectedWithoutPartialImport() {
    // Arrange
    var original = event("body", "unit", 0);
    var conflict = new DiagnosticEmission(original.address(), note("changed"), original.cause(), List.of(), -1);
    var collector = new DiagnosticEmissionCollector("body", "unit");
    collector.include(new DiagnosticEmissions(List.of(original)));
    var incompatible = new DiagnosticEmissions(List.of(event("body", "unit", 8), conflict));

    // Act
    assertThrows(IllegalArgumentException.class, () -> new DiagnosticEmissions(List.of(original, conflict)));
    assertThrows(IllegalArgumentException.class, () -> collector.include(incompatible));
    assertThrows(IllegalArgumentException.class,
        () -> DiagnosticEmissions.combine(new DiagnosticEmissions(List.of(original)), incompatible));
    collector.emit(note("after-rejection"));

    // Assert
    assertEquals(List.of(original.address(), new DiagnosticEmission.Address("body", "unit", 1)), collector.snapshot().events().stream()
        .map(DiagnosticEmission::address).toList());
    assertSame(original, collector.snapshot().events().getFirst());
  }

  @Test
  void givenReusedCollectorAndAnEmptyBatch_whenNewBatchesStart_thenEarlierMembershipFreezesAndAllocationContinues() {
    // Arrange
    var collector = new DiagnosticEmissionCollector("body", "unit");
    collector.emit(note("same-value"));
    var first = collector.snapshot();

    // Act
    collector.startBatch();
    collector.emit(note("same-value"));
    var second = collector.snapshot();
    collector.startBatch();
    var empty = collector.snapshot();
    collector.startBatch();
    collector.emit(note("same-value"));
    var third = collector.snapshot();

    // Assert
    assertEquals(DiagnosticEmissions.empty(), empty);
    assertEquals(List.of(0, 1, 2), List.of(first, second, third).stream().map(batch -> batch.events().getFirst().address().ordinal()).toList());
    assertEquals(List.of(1, 1, 1), List.of(first, second, third).stream().map(batch -> batch.events().size()).toList());
    assertEquals(first.diagnostics(), second.diagnostics());
    assertEquals(first.diagnostics(), third.diagnostics());
  }

  @Test
  void givenEqualValuesFromSeparateCreatingOwners_whenBranchesJoin_thenIndependentEventsRemainInEncounterOrder() {
    // Arrange
    var left = new DiagnosticEmissionCollector("body", "left");
    var right = new DiagnosticEmissionCollector("body", "right");
    left.emit(note("same-value"));
    right.emit(note("same-value"));

    // Act
    var joined = DiagnosticEmissions.combine(left.snapshot(), right.snapshot(), left.snapshot());

    // Assert
    assertEquals(List.of(new DiagnosticEmission.Address("body", "left", 0), new DiagnosticEmission.Address("body", "right", 0)),
        joined.events().stream().map(DiagnosticEmission::address).toList());
    assertEquals(joined.diagnostics().getFirst(), joined.diagnostics().getLast());
    assertSame(left.snapshot().events().getFirst(), joined.events().getFirst());
    assertSame(right.snapshot().events().getFirst(), joined.events().getLast());
  }

  @Test
  void givenIndependentOperationsAndEmptyResults_whenConstructed_thenTypedContractsKeepDeterministicLocalAllocation() {
    // Arrange
    var first = new DiagnosticEmissionCollector("body", "unit");
    var second = new DiagnosticEmissionCollector("body", "unit");

    // Act
    var emptyResult = new TranslationResult<>("empty", first.snapshot());
    var emptyFailure = new JavaSourceProjectionFailure(DiagnosticEmissions.empty());
    first.emit(note("same-value"));
    second.emit(note("same-value"));

    // Assert
    assertEquals(DiagnosticEmissions.empty(), emptyResult.emissions());
    assertEquals(DiagnosticEmissions.empty(), emptyFailure.emissions());
    assertEquals(List.of(new DiagnosticEmission.Address("body", "unit", 0)), first.snapshot().events().stream()
        .map(DiagnosticEmission::address).toList());
    assertEquals(first.snapshot(), second.snapshot());
    assertFalse(Collection.class.isAssignableFrom(DiagnosticEmissions.class));
    assertFalse(Collection.class.isAssignableFrom(DiagnosticEmissionCollector.class));
  }

  @Test
  void givenConcurrentNestedBatchesForOneOwner_whenCreationAndImportsInterleave_thenAllocationIsSharedAndMembershipStaysLocal() {
    // Arrange
    var root = new DiagnosticEmissionCollector("body", "unit");
    root.emit(note("root"));
    var left = root.newBatch();
    var right = left.newBatch();

    // Act
    left.emit(note("left"));
    var returned = left.snapshot();
    right.emit(note("right"));
    left.include(new DiagnosticEmissions(List.of(event("body", "unit", 8), event("body", "other", 100))));
    root.emit(note("after-import"));
    right.startBatch();
    right.emit(note("after-empty-batch"));

    // Assert
    assertEquals(List.of(0, 9), root.snapshot().events().stream().map(event -> event.address().ordinal()).toList());
    assertEquals(List.of(1), returned.events().stream().map(event -> event.address().ordinal()).toList());
    assertEquals(List.of(1, 8, 100), left.snapshot().events().stream().map(event -> event.address().ordinal()).toList());
    assertEquals(List.of(10), right.snapshot().events().stream().map(event -> event.address().ordinal()).toList());
    var independent = new DiagnosticEmissionCollector("body", "unit");
    independent.emit(note("independent"));
    assertEquals(0, independent.snapshot().events().getFirst().address().ordinal());
  }

  @Test
  void givenPriorEventsAndADeclaredProjectionFailure_whenComposed_thenOriginalEventsAndFailureChainRemainFrozen() {
    // Arrange
    var earlier = new DiagnosticEmissionCollector("body", "unit");
    var later = earlier.newBatch();
    earlier.emit(note("earlier"));
    later.emit(note("later"));
    var original = new JavaSourceProjectionFailure(later.snapshot());
    var cleanup = new IllegalStateException("cleanup");
    original.addSuppressed(cleanup);

    // Act
    var composed = new JavaSourceProjectionFailure(earlier.snapshot(), original);
    earlier.startBatch();
    later.startBatch();

    // Assert
    assertEquals(List.of(0, 1), composed.emissions().events().stream().map(event -> event.address().ordinal()).toList());
    assertEquals(List.of(1), original.emissions().events().stream().map(event -> event.address().ordinal()).toList());
    assertSame(original.emissions().events().getFirst(), composed.emissions().events().getLast());
    assertSame(original, composed.getCause());
    assertSame(cleanup, original.getSuppressed()[0]);
    var emptyPrefix = new JavaSourceProjectionFailure(DiagnosticEmissions.empty(), composed);
    assertEquals(composed.emissions(), emptyPrefix.emissions());
    assertSame(composed, emptyPrefix.getCause());
  }

  private static DiagnosticEmission event(String producer, String owner, int ordinal) {
    var diagnostic = note(producer + ':' + owner + ':' + ordinal);
    return new DiagnosticEmission(new DiagnosticEmission.Address(producer, owner, ordinal), diagnostic, diagnostic.outcomeKind(), List.of(), -1);
  }

  private static AnalysisDiagnostic note(String key) {
    return new AnalysisDiagnostic(key, "frontend.note", DiagnosticStage.FRONTEND, DiagnosticSeverity.INFO, "Translation observation",
        List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }
}
