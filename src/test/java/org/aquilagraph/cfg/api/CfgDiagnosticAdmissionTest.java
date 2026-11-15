package org.aquilagraph.cfg.api;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class CfgDiagnosticAdmissionTest {
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED);
  private static final List<DiagnosticStage> FOREIGN_STAGES = List.of(DiagnosticStage.INPUT, DiagnosticStage.FRONTEND,
      DiagnosticStage.PERSISTENCE);
  @TempDir Path directory;

  @Test
  void givenSuppliedForeignStagePrimaryDiagnostic_whenConstructingBlocker_thenCreatingOwnerIsRequired() {
    verifyProfiles(fixture -> {
      var blocker = fixture.outcome().methods().get(2).blockers().getFirst();
      assertAll(FOREIGN_STAGES.stream().map(stage -> (Executable) () -> assertThrows(IllegalArgumentException.class,
          () -> new CfgBlocker(blocker.kind(), blocker.astIdentity(), blocker.methodKey(), blocker.occurrence(), blocker.slot(),
              diagnostic(stage), blocker.evidenceDiagnostic()))));
    });
  }

  @Test
  void givenSuppliedForeignStageBodylessDiagnostic_whenConstructingMethodOutcome_thenCreatingOwnerIsRequired() {
    verifyProfiles(fixture -> {
      var bodyless = fixture.outcome().methods().getLast();
      assertAll(FOREIGN_STAGES.stream().map(stage -> (Executable) () -> assertThrows(IllegalArgumentException.class,
          () -> copy(bodyless, List.of(diagnostic(stage))))));
    });
  }

  @Test
  void givenSuppliedForeignStageFailureDiagnostic_whenConstructingMethodOutcome_thenCreatingOwnerIsRequired() {
    verifyProfiles(fixture -> {
      var failed = fixture.outcome().methods().get(1);
      assertAll(FOREIGN_STAGES.stream().map(stage -> (Executable) () -> assertThrows(IllegalArgumentException.class,
          () -> copy(failed, List.of(diagnostic(stage))))));
    });
  }

  @Test
  void givenSuppliedForeignStageRejection_whenConstructingRequestOutcome_thenCreatingOwnerIsRequired() {
    verifyProfiles(fixture -> assertAll(FOREIGN_STAGES.stream().map(stage -> (Executable) () ->
        assertThrows(IllegalArgumentException.class,
            () -> new CfgOutcome(fixture.outcome().request(), CfgState.REJECTED, List.of(), List.of(diagnostic(stage)))))));
  }

  @Test
  void givenRealRecoverableFailure_whenItsDiagnosticIsStripped_thenPublicConstructionRejectsIt() {
    verifyProfiles(fixture -> assertThrows(IllegalArgumentException.class,
        () -> copy(fixture.outcome().methods().get(1), List.of())));
  }

  @Test
  void givenSuppliedForeignStageRefusal_whenCrossingEitherApplicationEntry_thenInvalidNativeValueCannotEscape() {
    verifyProfiles(fixture -> assertAll(FOREIGN_STAGES.stream().map(stage -> (Executable) () -> {
      // Arrange
      CfgDeriver cfgDeriver = (source, request) -> {
        var refused = fixture.outcome().methods().get(2);
        var original = refused.blockers().getFirst();
        var blocker = new CfgBlocker(original.kind(), original.astIdentity(), original.methodKey(), original.occurrence(),
            original.slot(), diagnostic(stage));
        var method = new MethodCfgOutcome(refused.astIdentity(), refused.methodKey(), refused.state(), refused.body(),
            refused.graph(), List.of(blocker), refused.failure(), List.of(blocker.diagnostic()));
        return new CfgOutcome(request, CfgState.PARTIAL, List.of(method), method.diagnostics());
      };
      var runner = AnalysisCapabilities.computation(ignored -> fixture.ast(), cfgDeriver);
      var derivation = AnalysisCapabilities.existingSourceDerivation(cfgDeriver);
      var request = selected(fixture, fixture.outcome().methods().get(2));
      // Act
      var existing = assertThrows(IllegalArgumentException.class, () -> derivation.deriveExisting(fixture.ast(), request));
      var produced = assertThrows(IllegalArgumentException.class, () -> runner.analyze(productionRequest(fixture.ast(), request)));
      // Assert
      assertEquals(existing.getMessage(), produced.getMessage());
      assertEquals(AstCompletion.COMPLETE, fixture.ast().completion());
    })));
  }

  @Test
  void givenSuppliedFailureWithoutDiagnostic_whenCrossingEitherApplicationEntry_thenInvalidNativeValueCannotEscape() {
    verifyProfiles(fixture -> {
      // Arrange
      var failed = fixture.outcome().methods().get(1);
      var request = selected(fixture, failed);
      CfgDeriver cfgDeriver = (source, invoked) -> {
        var stripped = copy(failed, List.of());
        return new CfgOutcome(invoked, CfgState.PARTIAL, List.of(stripped), List.of());
      };
      var runner = AnalysisCapabilities.computation(ignored -> fixture.ast(), cfgDeriver);
      var derivation = AnalysisCapabilities.existingSourceDerivation(cfgDeriver);
      // Act
      var existing = assertThrows(IllegalArgumentException.class, () -> derivation.deriveExisting(fixture.ast(), request));
      var produced = assertThrows(IllegalArgumentException.class, () -> runner.analyze(productionRequest(fixture.ast(), request)));
      // Assert
      assertEquals(existing.getMessage(), produced.getMessage());
      assertSame(fixture.cause(), failed.failure().orElseThrow());
      assertEquals(AstCompletion.COMPLETE, fixture.ast().completion());
    });
  }

  @Test
  void givenRealFailureAndRepeatedCfgOccurrences_whenCrossingEitherApplicationEntry_thenCauseGraphsSourceAndOrderRemainExact() {
    verifyProfiles(fixture -> {
      // Arrange
      var original = fixture.outcome();
      var failed = original.methods().get(1);
      var repeated = List.of(failed.diagnostics().getFirst(), failed.diagnostics().getFirst());
      var methods = new ArrayList<>(original.methods());
      methods.set(1, copy(failed, repeated));
      var diagnostics = methods.stream().flatMap(method -> method.diagnostics().stream()).toList();
      var retained = new CfgOutcome(original.request(), original.state(), methods, diagnostics);
      CfgDeriver cfgDeriver = (source, request) -> retained;
      var runner = AnalysisCapabilities.computation(ignored -> fixture.ast(), cfgDeriver);
      var derivation = AnalysisCapabilities.existingSourceDerivation(cfgDeriver);
      // Act
      var runs = List.of(derivation.deriveExisting(fixture.ast(), original.request()),
          runner.analyze(productionRequest(fixture.ast(), original.request())));
      // Assert
      for (var run : runs) {
        assertEquals(AnalysisCompletion.FAILED, run.completion());
        assertSame(fixture.ast(), run.representation().ast());
        assertSame(retained, run.representation().cfg().orElseThrow());
        assertEquals(diagnostics, run.diagnostics().values());
        assertSame(fixture.cause(), retained.methods().get(1).failure().orElseThrow());
        assertSame(original.methods().getFirst().graph().orElseThrow(), retained.methods().getFirst().graph().orElseThrow());
        assertTrue(retained.methods().get(1).graph().isEmpty());
        assertEquals(failed.body(), retained.methods().get(1).body());
      }
    });
  }

  @Test
  void givenSuppliedOriginatingFrontendError_whenRetainingErroneousEvidence_thenSeparateEvidenceSlotKeepsItsCreatingStage() {
    verifyProfiles(fixture -> {
      var original = fixture.outcome().methods().get(2).blockers().getFirst();
      var cfg = diagnostic(DiagnosticStage.CONTROL_FLOW);
      var frontend = diagnostic(DiagnosticStage.FRONTEND);
      var blocker = new CfgBlocker(MethodCfgState.ERRONEOUS_EVIDENCE, original.astIdentity(), original.methodKey(), original.occurrence(),
          original.slot(), cfg, Optional.of(frontend));
      var refused = new MethodCfgOutcome(blocker.astIdentity(), blocker.methodKey(), blocker.kind(),
          fixture.outcome().methods().get(2).body(), Optional.empty(), List.of(blocker), Optional.empty(), List.of(cfg));
      assertSame(cfg, refused.diagnostics().getFirst());
      assertSame(frontend, refused.blockers().getFirst().evidenceDiagnostic().orElseThrow());
      assertEquals(DiagnosticStage.CONTROL_FLOW, cfg.stage());
      assertEquals(DiagnosticStage.FRONTEND, frontend.stage());
    });
  }

  private void verifyProfiles(FixtureAssertion assertion) {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> assertion.verify(fixture(profile))));
  }

  private Fixture fixture(AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("DiagnosticInputs.java")), """
        interface DiagnosticInputs {
          int bodyless();
          default int available() { return 7; }
          default int failed() { return 11; }
          default int refused(int input) { return switch (input) { case 1 -> 2; default -> 3; }; }
        }
        """)));
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    var result = ast.result().orElseThrow();
    var keys = List.of("available", "failed", "refused", "bodyless").stream().map(name -> result.program().methods().stream()
        .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();
    var request = new CfgRequest(result.identity(), keys, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);
    var cause = new IllegalStateException("Declared recoverable construction observation");
    var outcome = RecoverableCfgFailures.atMethod(keys.get(1), cause).derive(result, request);
    assertEquals(CfgState.PARTIAL, outcome.state());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.FAILED, MethodCfgState.UNSUPPORTED, MethodCfgState.NOT_APPLICABLE),
        outcome.methods().stream().map(MethodCfgOutcome::state).toList());
    assertEquals(List.of("control-flow.construction-failed", "control-flow.unsupported"),
        outcome.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
    return new Fixture(ast, outcome, cause);
  }

  private static AnalysisDiagnostic diagnostic(DiagnosticStage stage) {
    return new AnalysisDiagnostic("provided:diagnostic", "provided.observation", stage, DiagnosticSeverity.ERROR,
        "Supplied diagnostic occurrence", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }

  private static MethodCfgOutcome copy(MethodCfgOutcome original, List<AnalysisDiagnostic> diagnostics) {
    return new MethodCfgOutcome(original.astIdentity(), original.methodKey(), original.state(), original.body(), original.graph(),
        original.blockers(), original.failure(), diagnostics);
  }

  private static CfgRequest selected(Fixture fixture, MethodCfgOutcome method) {
    return new CfgRequest(method.astIdentity(), List.of(method.methodKey()), fixture.outcome().request().profile());
  }

  private static AnalysisComputationRequest productionRequest(AstOutcome ast, CfgRequest request) {
    var source = new AstProductionRequest(ast.capturedInput().orElseThrow(), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), ast.profile());
    return new AnalysisComputationRequest(source, Optional.of(new CfgSelection(request.methodKeys(), request.profile())));
  }

  private record Fixture(AstOutcome ast, CfgOutcome outcome, Throwable cause) {
  }

  @FunctionalInterface
  private interface FixtureAssertion {
    void verify(Fixture fixture);
  }
}
