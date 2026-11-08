package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisRequestIdentity.ExistingSelection;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgBlocker;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaMethod;
import org.junit.jupiter.api.Test;

class MethodCoverageTest {
  @Test
  void givenEveryAdmittedCfgState_whenConstructingAndQueryingCoverage_thenExplicitStatesAndNativeOwnersAgree() {
    // Arrange
    var source = source();
    var ast = source.result().orElseThrow();
    var expectations = expectations(source);
    var constructed = new ArrayList<MethodCoverage>();
    var queried = new ArrayList<MethodCoverage>();

    // Act
    for (var expectation : expectations) {
      var outcome = expectation.outcome();
      var declaration = ast.method(outcome.methodKey()).orElseThrow();
      constructed.add(new MethodCoverage(declaration, expectation.state(), Optional.of(outcome)));
      queried.add(run(source, outcome).methodCoverage(outcome.methodKey()).orElseThrow());
    }

    // Assert
    assertEquals(7, expectations.size());
    for (var index = 0; index < expectations.size(); index++) {
      var expectation = expectations.get(index);
      var declaration = ast.method(expectation.outcome().methodKey()).orElseThrow();
      for (var coverage : List.of(constructed.get(index), queried.get(index))) {
        assertEquals(expectation.state(), coverage.state());
        assertSame(declaration, coverage.declaration());
        assertSame(expectation.outcome(), coverage.cfg().orElseThrow());
      }
      assertEquals(constructed.get(index), queried.get(index));
      assertEquals(constructed.get(index).hashCode(), queried.get(index).hashCode());
    }
  }

  @Test
  void givenRequestOnlyStates_whenAdmittingAbsentOrPresentOutcomes_thenOnlyAbsenceIsAccepted() {
    // Arrange
    var source = source();
    var outcome = expectations(source).getFirst().outcome();
    var declaration = source.result().orElseThrow().method(outcome.methodKey()).orElseThrow();
    var states = List.of(MethodCoverageState.NOT_REQUESTED, MethodCoverageState.REQUEST_REJECTED);
    var admitted = new ArrayList<MethodCoverage>();

    // Act
    for (var state : states) {
      admitted.add(new MethodCoverage(declaration, state, Optional.empty()));
      assertThrows(IllegalArgumentException.class, () -> new MethodCoverage(declaration, state, Optional.of(outcome)));
    }

    // Assert
    assertEquals(states, admitted.stream().map(MethodCoverage::state).toList());
    assertTrue(admitted.stream().allMatch(value -> value.cfg().isEmpty()));
    assertTrue(admitted.stream().allMatch(value -> value.declaration() == declaration));
  }

  @Test
  void givenMappedStates_whenAdmittingMissingOrContradictoryOutcomes_thenEachIndependentContradictionIsRejected() {
    // Arrange
    var source = source();
    var expectations = expectations(source);
    var missing = new ArrayList<IllegalArgumentException>();
    var contradictory = new ArrayList<IllegalArgumentException>();

    // Act
    for (var expectation : expectations) {
      var declaration = source.result().orElseThrow().method(expectation.outcome().methodKey()).orElseThrow();
      missing.add(assertThrows(IllegalArgumentException.class,
          () -> new MethodCoverage(declaration, expectation.state(), Optional.empty())));
      var wrong = expectation.state() == MethodCoverageState.AVAILABLE ? MethodCoverageState.UNSUPPORTED : MethodCoverageState.AVAILABLE;
      contradictory.add(assertThrows(IllegalArgumentException.class,
          () -> new MethodCoverage(declaration, wrong, Optional.of(expectation.outcome()))));
    }

    // Assert
    assertEquals(7, missing.size());
    assertEquals(7, contradictory.size());
  }

  @Test
  void givenAnOutcomeAndAnotherCanonicalMethod_whenConstructingCoverage_thenTheForeignKeyIsRejected() {
    // Arrange
    var source = source();
    var outcome = expectations(source).getFirst().outcome();
    var other = declaration(source, "other");

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new MethodCoverage(other, MethodCoverageState.AVAILABLE, Optional.of(outcome)));

    // Assert
    assertNotEquals(other.methodKey(), outcome.methodKey());
    assertEquals(IllegalArgumentException.class, failure.getClass());
  }

  @Test
  void givenEqualMethodKeysFromDifferentSources_whenConstructingStandaloneCoverage_thenAdmissionRequiresOnlyKeyAndStateAssociation() {
    // Arrange
    var source = source();
    var outcome = expectations(source).getFirst().outcome();
    var foreign = TestAnalysisRuns.fromSource("interface Example { static int available() { return 9; } }").representation().ast();
    var declaration = declaration(foreign, "available");

    // Act
    var coverage = new MethodCoverage(declaration, MethodCoverageState.AVAILABLE, Optional.of(outcome));

    // Assert
    assertNotEquals(source.result().orElseThrow().identity(), foreign.result().orElseThrow().identity());
    assertEquals(declaration.methodKey(), outcome.methodKey());
    assertSame(declaration, coverage.declaration());
    assertSame(outcome, coverage.cfg().orElseThrow());
  }

  @Test
  void givenEachNullCoverageComponent_whenConstructingThePublicValue_thenEachNullPreconditionRejectsIndependently() {
    // Arrange
    var source = source();
    var declaration = declaration(source, "available");

    // Act
    var missingDeclaration = assertThrows(NullPointerException.class,
        () -> new MethodCoverage(null, MethodCoverageState.NOT_REQUESTED, Optional.empty()));
    var missingState = assertThrows(NullPointerException.class,
        () -> new MethodCoverage(declaration, null, Optional.empty()));
    var missingOutcome = assertThrows(NullPointerException.class,
        () -> new MethodCoverage(declaration, MethodCoverageState.NOT_REQUESTED, null));

    // Assert
    assertEquals(NullPointerException.class, missingDeclaration.getClass());
    assertEquals(NullPointerException.class, missingState.getClass());
    assertEquals(NullPointerException.class, missingOutcome.getClass());
  }

  @Test
  void givenMalformedAndUnknownQueryKeys_whenQueryingCoverage_thenMalformedKeysRejectAndUnknownKeysRemainAbsent() {
    // Arrange
    var run = TestAnalysisRuns.fromSource("interface Example { static int available() { return 1; } }");

    // Act
    var nullKey = assertThrows(NullPointerException.class, () -> run.methodCoverage(null));
    var emptyKey = assertThrows(IllegalArgumentException.class, () -> run.methodCoverage(""));
    var blankKey = assertThrows(IllegalArgumentException.class, () -> run.methodCoverage(" \t"));
    var unknown = run.methodCoverage("unknown-canonical-method");

    // Assert
    assertEquals(NullPointerException.class, nullKey.getClass());
    assertEquals(IllegalArgumentException.class, emptyKey.getClass());
    assertEquals(IllegalArgumentException.class, blankKey.getClass());
    assertTrue(unknown.isEmpty());
  }

  @Test
  void givenRejectedDuplicateUnknownForeignAndIncompatibleRequests_whenQueryingKnownDeclarations_thenExactSelectionDeterminesCoverage() {
    // Arrange
    var source = source();
    var ast = source.result().orElseThrow();
    var selected = declaration(source, "available").methodKey();
    var unselected = declaration(source, "other").methodKey();
    var foreign = TestAnalysisRuns.fromSource("interface Foreign {}").representation().ast().result().orElseThrow().identity();
    var incompatible = new CfgProfile("unrecognized-profile", CfgProfile.JAVA_BOUNDED.graphModelVersion());
    var requests = List.of(new CfgRequest(ast.identity(), List.of(selected, selected), CfgProfile.JAVA_BOUNDED),
        new CfgRequest(ast.identity(), List.of(selected, "unknown-canonical-method"), CfgProfile.JAVA_BOUNDED),
        new CfgRequest(foreign, List.of(selected), CfgProfile.JAVA_BOUNDED),
        new CfgRequest(ast.identity(), List.of(selected), incompatible));
    var derivation = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard());
    var runs = new ArrayList<AnalysisRun>();

    // Act
    for (var request : requests) {
      runs.add(derivation.deriveExisting(source, request));
    }

    // Assert
    for (var index = 0; index < runs.size(); index++) {
      var run = runs.get(index);
      var cfg = run.representation().cfg().orElseThrow();
      assertSame(requests.get(index), cfg.request());
      assertSame(requests.get(index), ((ExistingSelection) run.identity().selection()).request());
      assertEquals(CfgState.REJECTED, cfg.state());
      assertTrue(cfg.methods().isEmpty());
      assertEquals(MethodCoverageState.REQUEST_REJECTED, run.methodCoverage(selected).orElseThrow().state());
      assertEquals(MethodCoverageState.NOT_REQUESTED, run.methodCoverage(unselected).orElseThrow().state());
      assertFalse(run.methodCoverage(selected).orElseThrow().cfg().isPresent());
    }
  }

  @Test
  void givenBodylessDeclarationInARejectedRequest_whenQueryingCoverage_thenRequestRejectionPrecedesBodylessApplicability() {
    // Arrange
    var source = source();
    var bodyless = declaration(source, "bodyless");
    var request = new CfgRequest(source.result().orElseThrow().identity(), List.of(bodyless.methodKey(), bodyless.methodKey()),
        CfgProfile.JAVA_BOUNDED);

    // Act
    var run = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(source, request);
    var coverage = run.methodCoverage(bodyless.methodKey()).orElseThrow();

    // Assert
    assertFalse(bodyless.hasBody());
    assertEquals(CfgState.REJECTED, run.representation().cfg().orElseThrow().state());
    assertSame(bodyless, coverage.declaration());
    assertEquals(MethodCoverageState.REQUEST_REJECTED, coverage.state());
    assertTrue(coverage.cfg().isEmpty());
  }

  @Test
  void givenUnavailableSourceWithInvalidRetainedSelection_whenQueryingCoverage_thenNoMethodCoverageIsInvented() {
    // Arrange
    var available = source();
    var unavailable = TestAnalysisRuns.rejected().representation().ast();
    var key = declaration(available, "available").methodKey();
    var request = new CfgRequest(available.result().orElseThrow().identity(), List.of(key, key), CfgProfile.JAVA_BOUNDED);
    var derivation = AnalysisCapabilities.existingSourceDerivation((source, selection) -> {
      throw new AssertionError("Unavailable source must skip derivation");
    });

    // Act
    var run = derivation.deriveExisting(unavailable, request);
    var coverage = run.methodCoverage(key);

    // Assert
    assertTrue(unavailable.result().isEmpty());
    assertSame(request, ((ExistingSelection) run.identity().selection()).request());
    assertTrue(run.representation().cfg().isEmpty());
    assertTrue(coverage.isEmpty());
  }

  private static AstOutcome source() {
    return TestAnalysisRuns.fromSource("interface Example { void bodyless(); static int available() { return 1; } static int other() { return 2; } }")
        .representation().ast();
  }

  private static JavaMethod declaration(AstOutcome source, String name) {
    return source.result().orElseThrow().program().methods().stream().filter(method -> method.name().equals(name)).findFirst().orElseThrow();
  }

  private static List<CoverageExpectation> expectations(AstOutcome source) {
    var ast = source.result().orElseThrow();
    var selected = declaration(source, "available").methodKey();
    var bodyless = declaration(source, "bodyless").methodKey();
    var outcomes = CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), List.of(selected, bodyless), CfgProfile.JAVA_BOUNDED)).methods();
    var diagnostic = diagnostic("construction-failed", AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var failed = new MethodCfgOutcome(ast.identity(), selected, MethodCfgState.FAILED, ast.bodyRef(selected), Optional.empty(),
        List.of(), Optional.of(new IllegalStateException("Supplied declared construction condition")), List.of(diagnostic));
    return List.of(new CoverageExpectation(MethodCoverageState.AVAILABLE, outcomes.getFirst()),
        new CoverageExpectation(MethodCoverageState.NOT_APPLICABLE, outcomes.getLast()),
        new CoverageExpectation(MethodCoverageState.SOURCE_UNAVAILABLE, refused(source, selected, MethodCfgState.SOURCE_UNAVAILABLE)),
        new CoverageExpectation(MethodCoverageState.UNSUPPORTED, refused(source, selected, MethodCfgState.UNSUPPORTED)),
        new CoverageExpectation(MethodCoverageState.ERRONEOUS_EVIDENCE, refused(source, selected, MethodCfgState.ERRONEOUS_EVIDENCE)),
        new CoverageExpectation(MethodCoverageState.INSUFFICIENT_EVIDENCE, refused(source, selected, MethodCfgState.INSUFFICIENT_EVIDENCE)),
        new CoverageExpectation(MethodCoverageState.FAILED, failed));
  }

  private static MethodCfgOutcome refused(AstOutcome source, String key, MethodCfgState state) {
    var ast = source.result().orElseThrow();
    var diagnostic = diagnostic(state.name(), state == MethodCfgState.ERRONEOUS_EVIDENCE
        ? AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE : AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var blocker = new CfgBlocker(state, ast.identity(), key, Optional.empty(), "body", diagnostic);
    return new MethodCfgOutcome(ast.identity(), key, state,
        state == MethodCfgState.SOURCE_UNAVAILABLE ? Optional.empty() : ast.bodyRef(key), Optional.empty(),
        List.of(blocker), Optional.empty(), List.of(diagnostic));
  }

  private static AnalysisDiagnostic diagnostic(String key, AnalysisOutcomeKind kind) {
    return new AnalysisDiagnostic("diagnostic:" + key, "control-flow.supplied", DiagnosticStage.CONTROL_FLOW, DiagnosticSeverity.ERROR,
        "Supplied coverage outcome", List.of(), kind);
  }

  private static AnalysisRun run(AstOutcome source, MethodCfgOutcome outcome) {
    var request = new CfgRequest(outcome.astIdentity(), List.of(outcome.methodKey()), CfgProfile.JAVA_BOUNDED);
    var state = outcome.state() == MethodCfgState.AVAILABLE || outcome.state() == MethodCfgState.NOT_APPLICABLE
        ? CfgState.COMPLETE : CfgState.PARTIAL;
    var cfg = new CfgOutcome(request, state, List.of(outcome), outcome.diagnostics());
    var identity = new AnalysisRequestIdentity(AnalysisOperation.DERIVE_EXISTING, AnalysisRequestIdentity.sourceSubject(source),
        new ExistingSelection(request));
    return AnalysisRun.create(identity, new ProgramRepresentation(source, Optional.of(cfg)), List.of());
  }

  private record CoverageExpectation(MethodCoverageState state, MethodCfgOutcome outcome) {}
}
