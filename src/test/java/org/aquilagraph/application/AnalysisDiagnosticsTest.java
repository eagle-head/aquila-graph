package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisDiagnosticOccurrence.Owner;
import org.aquilagraph.application.RequestQualificationReason.Method;
import org.aquilagraph.application.RequestQualificationReason.MethodDiagnostic;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.junit.jupiter.api.Test;

class AnalysisDiagnosticsTest {
  @Test
  void givenRepeatedAndSameKeyUnequalEvents_whenComposed_thenExactApplicationSlotsAndRelationsRemainDistinct() {
    // Arrange
    var original = TestAnalysisRuns.completed();
    var first = diagnostic("shared", "First", DiagnosticStage.INPUT, DiagnosticSeverity.ERROR);
    var changed = diagnostic("shared", "Changed", DiagnosticStage.INPUT, DiagnosticSeverity.ERROR);
    var supplied = new ArrayList<>(List.of(first, first, changed));
    // Act
    var run = AnalysisRun.create(original.identity(), original.representation(), supplied);
    supplied.clear();
    var catalog = run.diagnostics();
    var entries = catalog.occurrences();
    // Assert
    assertSame(catalog, run.diagnostics());
    assertEquals(List.of(first, first, changed), catalog.values());
    assertNotSame(entries.getFirst(), entries.get(1));
    assertEquals(List.of(0, 1, 2), entries.stream().map(AnalysisDiagnosticOccurrence::localPosition).toList());
    assertEquals(List.of(0, 1, 2), entries.stream().map(catalog::positionOf).toList());
    assertSame(run.applicationDiagnostics(), assertInstanceOf(Owner.Application.class, entries.getFirst().owner()).diagnostics());
    for (var position = 0; position < entries.size(); position++) {
      assertSame(entries.get(position), catalog.relatedTo(run.qualification().reasons().get(position)).getFirst());
    }
    assertThrows(UnsupportedOperationException.class, entries::clear);
    assertThrows(UnsupportedOperationException.class, () -> catalog.values().clear());
    assertThrows(UnsupportedOperationException.class, () -> catalog.relatedTo(run.qualification().reasons().getFirst()).clear());
  }

  @Test
  void givenEqualForeignAssessment_whenResolvingOccurrencesOrReasons_thenForeignReferencesAreRejected() {
    // Arrange
    var original = TestAnalysisRuns.completed();
    var error = diagnostic("error", "Application error", DiagnosticStage.INPUT, DiagnosticSeverity.ERROR);
    var first = AnalysisRun.create(original.identity(), original.representation(), List.of(error));
    var second = AnalysisRun.create(original.identity(), original.representation(), List.of(error));
    // Act
    var catalog = first.diagnostics();
    // Assert
    assertEquals(first, second);
    assertEquals(first.qualification().reasons(), second.qualification().reasons());
    assertThrows(IllegalArgumentException.class, () -> catalog.positionOf(second.diagnostics().occurrences().getFirst()));
    assertThrows(IllegalArgumentException.class, () -> catalog.relatedTo(second.qualification().reasons().getFirst()));
    assertThrows(IllegalArgumentException.class, () -> original.diagnostics().positionOf(catalog.occurrences().getFirst()));
    assertThrows(NullPointerException.class, () -> catalog.positionOf(null));
    assertThrows(NullPointerException.class, () -> catalog.relatedTo((RequestQualificationReason) null));
  }

  @Test
  void givenAdmittedSourceAndExactMethodRequest_whenDerivingExisting_thenTheGraphAndQualificationRetainTheirOwners() {
    // Arrange
    var source = TestAnalysisRuns.fromSource("class Example { int value() { return 1; } }").representation().ast();
    var ast = source.result().orElseThrow();
    var key = ast.program().methods().getFirst().methodKey();
    var request = new CfgRequest(ast.identity(), List.of(key), CfgProfile.JAVA_BOUNDED);
    var derivation = derivation();
    // Act
    var run = derivation.deriveExisting(source, request);
    // Assert
    assertSame(source, run.representation().ast());
    assertEquals(AnalysisOperation.DERIVE_EXISTING, run.identity().operation());
    assertEquals(new AnalysisRequestIdentity.ExistingSelection(request), run.identity().selection());
    var cfg = run.representation().cfg().orElseThrow();
    assertSame(request, cfg.request());
    var graph = cfg.methods().getFirst().graph().orElseThrow();
    assertEquals(ast.identity(), graph.identity().astIdentity());
    assertEquals(key, graph.methodKey());
    assertEquals(CfgState.COMPLETE, cfg.state());
    assertEquals(List.of(), run.qualification().reasons());
    assertEquals(List.of(), run.diagnostics().occurrences());
  }

  @Test
  void givenReversedMethodSelection_whenComposed_thenCanonicalOrderAndBlockerRelationsShareTheSameOccurrences() {
    // Arrange
    var source = TestAnalysisRuns.fromSource("""
        interface Example {
          static int first(int x) { (x) = 1; return x; }
          static int second(int x) { (x) = 2; return x; }
        }
        """).representation().ast();
    var ast = source.result().orElseThrow();
    var keys = ast.program().methods().stream().map(method -> method.methodKey()).toList();
    var request = new CfgRequest(ast.identity(), keys.reversed(), CfgProfile.JAVA_BOUNDED);
    // Act
    var run = derivation().deriveExisting(source, request);
    var catalog = run.diagnostics();
    var cfg = run.representation().cfg().orElseThrow();
    var first = cfg.methods().getLast();
    var second = cfg.methods().getFirst();
    // Assert
    assertEquals(List.of(first.diagnostics().getFirst(), second.diagnostics().getFirst()), catalog.values());
    assertSame(first, assertInstanceOf(Owner.Method.class, catalog.occurrences().getFirst().owner()).outcome());
    assertSame(second, assertInstanceOf(Owner.Method.class, catalog.occurrences().getLast().owner()).outcome());
    assertEquals(catalog.occurrences(), catalog.relatedTo(cfg));
    assertSame(catalog.occurrences().getFirst(), catalog.forBlocker(first, 0));
    assertSame(catalog.occurrences().getLast(), catalog.forBlocker(second, 0));
    assertSame(catalog.occurrences().getLast(), catalog.relatedTo(run.qualification().reasons().getFirst()).getFirst());
    assertSame(catalog.occurrences().getFirst(), catalog.relatedTo(run.qualification().reasons().getLast()).getFirst());
    assertThrows(IllegalArgumentException.class, () -> catalog.forBlocker(first, -1));
    assertThrows(IllegalArgumentException.class, () -> catalog.forBlocker(first, first.blockers().size()));
    var foreignMethod = new MethodCfgOutcome(first.astIdentity(), first.methodKey(), first.state(), first.body(), first.graph(),
        first.blockers(), first.failure(), first.diagnostics());
    assertEquals(first, foreignMethod);
    assertThrows(IllegalArgumentException.class, () -> catalog.relatedTo(foreignMethod));
    assertThrows(IllegalArgumentException.class, () -> catalog.forBlocker(foreignMethod, 0));
    var foreignCfg = new CfgOutcome(cfg.request(), cfg.state(), cfg.methods(), cfg.diagnostics());
    assertThrows(IllegalArgumentException.class, () -> catalog.relatedTo(foreignCfg));
  }

  @Test
  void givenSourceReasonsAndRejectedSelection_whenComposed_thenEachOwnerUsesItsNativeSlotsBeforeApplicationEvents() {
    // Arrange
    var source = TestAnalysisRuns.fromSource("class Example { int field = 1; int value() { return 1; } }").representation().ast();
    var rejected = derivation().deriveExisting(source,
        new CfgRequest(source.result().orElseThrow().identity(), List.of("missing"), CfgProfile.JAVA_BOUNDED));
    var error = diagnostic("application", "Application error", DiagnosticStage.INPUT, DiagnosticSeverity.ERROR);
    // Act
    var run = AnalysisRun.create(rejected.identity(), rejected.representation(), List.of(error));
    var catalog = run.diagnostics();
    var cfg = run.representation().cfg().orElseThrow();
    // Assert
    assertFalse(source.diagnostics().isEmpty());
    var expected = new ArrayList<>(source.diagnostics());
    expected.addAll(cfg.diagnostics());
    expected.add(error);
    assertEquals(expected, catalog.values());
    assertSame(source, assertInstanceOf(Owner.Source.class, catalog.occurrences().getFirst().owner()).outcome());
    var requestEvent = catalog.relatedTo(cfg).getFirst();
    assertSame(cfg, assertInstanceOf(Owner.Request.class, requestEvent.owner()).outcome());
    assertEquals(source.diagnostics().size(), catalog.positionOf(requestEvent));
    for (var reason : source.qualification().reasons()) {
      assertEquals(reason.diagnostics().stream().map(reference -> reference.diagnostic()).toList(),
          catalog.relatedTo(reason).stream().map(AnalysisDiagnosticOccurrence::diagnostic).toList());
      assertEquals(reason.diagnostics().stream().map(reference -> reference.position()).toList(),
          catalog.relatedTo(reason).stream().map(AnalysisDiagnosticOccurrence::localPosition).toList());
    }
    var reason = source.qualification().reasons().getFirst();
    var foreignReason = new SourceQualificationReason(reason.category(), reason.role(), reason.subject(), reason.origins(),
        reason.diagnostics(), reason.evidence());
    assertEquals(reason, foreignReason);
    assertThrows(IllegalArgumentException.class, () -> catalog.relatedTo(foreignReason));
    var selection = run.qualification().reasons().stream().filter(RequestQualificationReason.Selection.class::isInstance)
        .findFirst().orElseThrow();
    assertEquals(catalog.relatedTo(cfg), catalog.relatedTo(selection));
  }

  @Test
  void givenReversedSelectionWithFailedAndBodylessMethods_whenComposed_thenReasonsAndOccurrencesRetainTheirOwnOrder() {
    // Arrange
    var source = TestAnalysisRuns.fromSource("interface Example { void absent(); static void compute() {} }").representation().ast();
    var ast = source.result().orElseThrow();
    var bodylessKey = ast.program().methods().getFirst().methodKey();
    var failedKey = ast.program().methods().getLast().methodKey();
    var warning = diagnostic("shared", "Warning", DiagnosticStage.CONTROL_FLOW, DiagnosticSeverity.WARNING);
    var error = diagnostic("shared", "Error", DiagnosticStage.CONTROL_FLOW, DiagnosticSeverity.ERROR);
    var bodyless = new MethodCfgOutcome(ast.identity(), bodylessKey, MethodCfgState.NOT_APPLICABLE, Optional.empty(), Optional.empty(),
        List.of(), Optional.empty(), List.of(warning, error, error));
    var failed = new MethodCfgOutcome(ast.identity(), failedKey, MethodCfgState.FAILED, ast.bodyRef(failedKey), Optional.empty(),
        List.of(), Optional.of(new IllegalStateException("Declared construction failure")), List.of(error));
    var request = new CfgRequest(ast.identity(), List.of(failedKey, bodylessKey), CfgProfile.JAVA_BOUNDED);
    var cfg = new CfgOutcome(request, CfgState.PARTIAL, List.of(failed, bodyless), List.of(error, warning, error, error));

    // Act
    var run = AnalysisCapabilities.existingSourceDerivation((ignored, invoked) -> cfg).deriveExisting(source, request);
    var catalog = run.diagnostics();
    var reasons = run.qualification().reasons();
    var occurrences = catalog.occurrences();

    // Assert
    assertEquals(AnalysisCompletion.FAILED, run.completion());
    assertEquals(List.of(new Method(failed), new MethodDiagnostic(bodyless, 1), new MethodDiagnostic(bodyless, 2)), reasons);
    assertEquals(List.of(warning, error, error, error), catalog.values());
    assertEquals(List.of(0, 1, 2, 0), occurrences.stream().map(AnalysisDiagnosticOccurrence::localPosition).toList());
    assertEquals(List.of(3, 1, 2), reasons.stream().flatMap(reason -> catalog.relatedTo(reason).stream())
        .map(catalog::positionOf).toList());
    var bodylessOccurrences = occurrences.subList(0, 3);
    assertEquals(bodylessOccurrences, catalog.relatedTo(bodyless));
    assertTrue(bodylessOccurrences.stream().allMatch(occurrence -> occurrence.owner() instanceof Owner.Method owner
        && owner.outcome() == bodyless));
    assertSame(failed, assertInstanceOf(Owner.Method.class, occurrences.getLast().owner()).outcome());
    assertThrows(IllegalArgumentException.class, () -> catalog.forBlocker(bodyless, 0));
    assertThrows(IllegalArgumentException.class, () -> new Owner.Request(cfg));
  }

  @Test
  void givenUnavailableSource_whenComposed_thenNoResultIdentityOrDiagnosticEventIsInvented() {
    // Arrange
    var run = TestAnalysisRuns.rejected();
    // Act
    var catalog = run.diagnostics();
    // Assert
    assertTrue(run.representation().ast().result().isEmpty());
    assertEquals(run.representation().ast().diagnostics(), catalog.values());
    assertTrue(catalog.occurrences().isEmpty());
    assertTrue(run.qualification().reasons().stream().allMatch(reason -> catalog.relatedTo(reason).isEmpty()));
    assertTrue(run.representation().ast().qualification().reasons().stream().allMatch(reason -> catalog.relatedTo(reason).isEmpty()));
  }

  @Test
  void givenEqualCopiedRepresentation_whenQueryingCoverage_thenAssessedNativeOwnersAndValueIdentityRemain() {
    // Arrange
    var original = TestAnalysisRuns.fromSource("interface Example { static int value(int x) { (x) = 1; return x; } }");
    var cfg = original.representation().cfg().orElseThrow();
    var copiedMethods = cfg.methods().stream().map(method -> new MethodCfgOutcome(method.astIdentity(), method.methodKey(), method.state(),
        method.body(), method.graph(), method.blockers(), method.failure(), method.diagnostics())).toList();
    var copiedCfg = new CfgOutcome(cfg.request(), cfg.state(), copiedMethods, cfg.diagnostics());
    var ast = original.representation().ast().result().orElseThrow();
    var copied = new ProgramRepresentation(original.representation().ast(), Optional.of(copiedCfg));
    var nativeMethod = cfg.methods().getFirst();
    var nativeDeclaration = ast.method(nativeMethod.methodKey()).orElseThrow();
    var runHash = original.hashCode();
    var qualificationHash = original.qualification().hashCode();

    // Act
    var run = new AnalysisRun(original.identity(), copied, original.qualification(), original.applicationDiagnostics());
    var coverage = run.methodCoverage(nativeMethod.methodKey()).orElseThrow();
    var collective = run.methodCoverages();
    var repeated = run.methodCoverages();

    // Assert
    assertEquals(copied, run.representation());
    assertSame(original.representation(), run.representation());
    assertSame(original.diagnostics(), run.diagnostics());
    assertEquals(nativeMethod, copiedMethods.getFirst());
    assertNotSame(nativeMethod, copiedMethods.getFirst());
    assertEquals(List.of(coverage), collective);
    assertEquals(collective, repeated);
    for (var row : List.of(coverage, collective.getFirst(), repeated.getFirst())) {
      assertSame(nativeDeclaration, row.declaration());
      assertSame(nativeMethod, row.cfg().orElseThrow());
      assertEquals(run.diagnostics().relatedTo(nativeMethod), run.diagnostics().relatedTo(row.cfg().orElseThrow()));
    }
    assertThrows(UnsupportedOperationException.class, collective::clear);
    assertEquals(original, run);
    assertEquals(runHash, run.hashCode());
    assertEquals(qualificationHash, run.qualification().hashCode());
    assertEquals(original.isPublishable(), run.isPublishable());
    assertEquals(run.diagnostics().occurrences(), run.diagnostics().relatedTo(run.representation().cfg().orElseThrow()));
    assertThrows(IllegalArgumentException.class, () -> run.diagnostics().relatedTo(copiedCfg));
    assertThrows(IllegalArgumentException.class, () -> run.diagnostics().relatedTo(copiedMethods.getFirst()));
  }

  @Test
  void givenMethodOutsideCanonicalSource_whenAssessingItsDiagnostics_thenItsEventsCannotBeSilentlyOmitted() {
    // Arrange
    var source = TestAnalysisRuns.completed().representation().ast();
    var ast = source.result().orElseThrow();
    var warning = diagnostic("unknown", "Method notice", DiagnosticStage.CONTROL_FLOW, DiagnosticSeverity.WARNING);
    var method = new MethodCfgOutcome(ast.identity(), "missing", MethodCfgState.NOT_APPLICABLE, Optional.empty(), Optional.empty(),
        List.of(), Optional.empty(), List.of(warning));
    var cfg = new CfgOutcome(new CfgRequest(ast.identity(), List.of("missing"), CfgProfile.JAVA_BOUNDED), CfgState.COMPLETE,
        List.of(method), List.of(warning));
    var representation = new ProgramRepresentation(source, Optional.of(cfg));
    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> RequestQualification.assess(representation, List.of()));
    // Assert
    assertTrue(failure.getMessage().contains("canonical source declarations"));
  }

  private static ExistingSourceDerivation derivation() {
    return AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard());
  }

  private static AnalysisDiagnostic diagnostic(String key, String message, DiagnosticStage stage, DiagnosticSeverity severity) {
    return new AnalysisDiagnostic(key, "analysis.notice", stage, severity, message, List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }
}
