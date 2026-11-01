package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.aquilagraph.application.AnalysisRequestIdentity.ExistingSelection;
import org.aquilagraph.application.AnalysisRequestIdentity.ProductionSelection;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgDerivationException;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceAnalysisRunnerTest {
  @TempDir Path directory;

  @Test
  void givenRealSourceAndInvalidSelection_whenAnalyzing_thenExplicitRequestCausesPreserveAstAndPreventRepositoryAcquisition() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), "class Stored { int value() { return 1; } }")));
    var selection = new CfgSelection(List.of("unknown", "unknown", ""), CfgProfile.JAVA_BOUNDED);
    var request = new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration(), AstProfile.JAVA_SOURCE_BOUNDED,
        Optional.of(selection), PublicationIntent.SAVE_COMPLETED);
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var publication = new CompletedRunPublicationService(() -> {
      throw new AssertionError("Rejected control flow must not acquire a repository");
    });

    // Act
    var run = runner.analyze(request);
    var stored = publication.publish(run);

    // Assert
    var ast = run.representation().ast().result().orElseThrow();
    var cfg = run.representation().cfg().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertEquals(2, ast.program().methods().size());
    assertEquals(List.of(), ast.diagnostics());
    assertEquals(AnalysisCompletion.REJECTED, run.completion());
    assertEquals(new CfgRequest(ast.identity(), selection.methodKeys(), selection.profile()), cfg.request());
    assertEquals(CfgState.REJECTED, cfg.state());
    assertSame(cfg, ((RequestQualificationReason.Selection) run.qualification().reasons().getFirst()).outcome());
    assertEquals(List.of("control-flow.unknown-method", "control-flow.duplicate-selection", "control-flow.unknown-method",
        "control-flow.unknown-method"), run.diagnostics().stream().map(value -> value.code()).toList());
    assertEquals(List.of(AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE, AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE,
        AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE, AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE),
        run.diagnostics().stream().map(value -> value.outcomeKind()).toList());
    assertEquals(cfg.diagnostics(), run.diagnostics());
    assertFalse(run.isPublishable());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, stored.status());
    assertEquals(CommitKnowledge.NOT_STARTED, stored.knowledge());
  }

  @Test
  void givenCfgPortReturningAnotherSelection_whenAnalyzing_thenContractViolationRemainsAbrupt() throws IOException {
    // Arrange
    var ast = produce("interface Stored { static int first() { return 1; } static int second() { return 2; } }");
    var selection = new CfgSelection(List.of(key(ast, "first")), CfgProfile.JAVA_BOUNDED);
    var unexpected = CfgDerivers.standard().derive(ast.result().orElseThrow(),
        new CfgRequest(ast.result().orElseThrow().identity(), List.of(key(ast, "second")), CfgProfile.JAVA_BOUNDED));
    var observedRequests = new ArrayList<CfgRequest>();
    var runner = new SourceAnalysisRunner(ignored -> ast, (source, request) -> {
      observedRequests.add(request);
      return unexpected;
    });

    // Act
    var failure = assertThrows(IllegalStateException.class,
        () -> runner.analyze(request(ast, Optional.of(selection), PublicationIntent.DO_NOT_SAVE)));

    // Assert
    assertEquals("CFG port returned a different requested identity or selection", failure.getMessage());
    assertEquals(List.of(new CfgRequest(ast.result().orElseThrow().identity(), selection.methodKeys(), selection.profile())), observedRequests);
    assertEquals(List.of(key(ast, "first")), selection.methodKeys());
    assertEquals(List.of(key(ast, "second")), unexpected.request().methodKeys());
  }

  @Test
  void givenAllBodySelection_whenProducingSource_thenCanonicalBodiesAreBoundOnceAndBodylessDeclarationsRemainUnselected()
      throws IOException {
    // Arrange
    var ast = produce("interface Stored { int absent(); static int first() { return 1; } default int second() { return 2; } }");
    var selection = CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED);
    var produced = new AtomicInteger();
    var runner = new SourceAnalysisRunner(ignored -> {
      produced.incrementAndGet();
      return ast;
    }, CfgDerivers.standard());

    // Act
    var run = runner.analyze(request(ast, Optional.of(selection), PublicationIntent.DO_NOT_SAVE));
    var binding = (ProductionSelection) run.identity().selection();
    var cfg = run.representation().cfg().orElseThrow();

    // Assert
    assertEquals(1, produced.get());
    assertSame(ast, run.representation().ast());
    assertSame(selection, binding.selection());
    assertTrue(selection.methodKeys().isEmpty());
    assertEquals(List.of(key(ast, "first"), key(ast, "second")), binding.methodKeys());
    assertEquals(binding.methodKeys(), cfg.request().methodKeys());
    assertEquals(binding.methodKeys(), cfg.methods().stream().map(value -> value.methodKey()).toList());
    assertEquals(ast.result().orElseThrow().identity(), cfg.request().astIdentity());
    assertEquals(MethodCoverageState.NOT_REQUESTED, run.methodCoverage(key(ast, "absent")).orElseThrow().state());
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
    assertTrue(run.qualification().reasons().isEmpty());
    assertTrue(run.isPublishable());
    assertTrue(cfg.methods().stream().allMatch(value -> value.state() == MethodCfgState.AVAILABLE));
  }

  @Test
  void givenBodylessAvailableRefusedAndUnselectedDeclarations_whenDerivingExisting_thenOrderedPartialCoveragePreservesTheSource()
      throws IOException {
    // Arrange
    var ast = produce("""
        abstract class Stored {
          abstract int absent();
          int available() { return 1; }
          int refused(int value) { return switch (value) { case 1 -> 2; default -> 3; }; }
          int unselected() { return 4; }
        }
        """);
    var result = ast.result().orElseThrow();
    var selected = List.of(key(ast, "absent"), key(ast, "available"), key(ast, "refused"));
    var request = new CfgRequest(result.identity(), selected, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);
    var runner = new SourceAnalysisRunner(ignored -> {
      throw new AssertionError("Existing derivation must not produce source");
    }, CfgDerivers.standard());

    // Act
    var run = runner.deriveExisting(ast, request);
    var cfg = run.representation().cfg().orElseThrow();

    // Assert
    assertSame(ast, run.representation().ast());
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertSame(request, cfg.request());
    assertEquals(CfgState.PARTIAL, cfg.state());
    assertEquals(selected, cfg.methods().stream().map(method -> method.methodKey()).toList());
    assertEquals(List.of(MethodCfgState.NOT_APPLICABLE, MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED),
        cfg.methods().stream().map(method -> method.state()).toList());
    assertTrue(cfg.methods().getFirst().body().isEmpty());
    assertTrue(cfg.methods().getFirst().graph().isEmpty());
    var graph = cfg.methods().get(1).graph().orElseThrow();
    assertEquals(result.bodyRef(key(ast, "available")).orElseThrow(), graph.body());
    assertEquals(result.identity(), graph.identity().astIdentity());
    var refusal = cfg.methods().getLast();
    assertEquals(1, run.qualification().reasons().size());
    assertSame(refusal, ((RequestQualificationReason.Method) run.qualification().reasons().getFirst()).outcome());
    assertTrue(refusal.graph().isEmpty());
    assertEquals(List.of(MethodCfgState.UNSUPPORTED), refusal.blockers().stream().map(blocker -> blocker.kind()).toList());
    assertEquals(List.of("construct"), refusal.blockers().stream().map(blocker -> blocker.slot()).toList());
    assertEquals(refusal.blockers().stream().map(blocker -> blocker.diagnostic()).toList(), cfg.diagnostics());
    assertTrue(refusal.blockers().stream().allMatch(blocker -> blocker.astIdentity().equals(result.identity())
        && result.node(blocker.occurrence().orElseThrow()).isPresent()));
    assertEquals(List.of(MethodCoverageState.NOT_APPLICABLE, MethodCoverageState.AVAILABLE, MethodCoverageState.UNSUPPORTED,
        MethodCoverageState.NOT_REQUESTED), List.of("absent", "available", "refused", "unselected").stream()
            .map(name -> run.methodCoverage(key(ast, name)).orElseThrow().state()).toList());
    assertTrue(result.program().methods().stream().allMatch(method -> run.methodCoverage(method.methodKey()).isPresent()));
  }

  @Test
  void givenCompleteSourceAndLaterRecoverableConstructionFailure_whenAnalyzing_thenFailedRunKeepsExactSourceAndUnaffectedGraphs()
      throws IOException {
    // Arrange
    var ast = produce("class Stored { int first() { return 1; } int second() { return 2; } int third() { return 3; } }");
    var selection = new CfgSelection(List.of(key(ast, "first"), key(ast, "second"), key(ast, "third")), CfgProfile.JAVA_BOUNDED);
    var cause = new IllegalStateException("Declared construction observation");
    var runner = new SourceAnalysisRunner(ignored -> ast, RecoverableCfgFailures.atMethod(key(ast, "second"), cause));

    // Act
    var run = runner.analyze(request(ast, Optional.of(selection), PublicationIntent.DO_NOT_SAVE));

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertSame(ast, run.representation().ast());
    assertEquals(AnalysisCompletion.FAILED, run.completion());
    var failed = run.qualification().reasons().stream().filter(RequestQualificationReason.Method.class::isInstance)
        .map(RequestQualificationReason.Method.class::cast).filter(reason -> reason.outcome().state() == MethodCfgState.FAILED).toList();
    assertEquals(1, failed.size());
    assertSame(cause, failed.getFirst().outcome().failure().orElseThrow());
    assertEquals(ast.completion() == AstCompletion.INCOMPLETE,
        run.qualification().reasons().stream().anyMatch(RequestQualificationReason.Source.class::isInstance));
    assertEquals(selection.methodKeys(), run.representation().cfg().orElseThrow().request().methodKeys());
    assertConstructionProgress(ast, run, cause);
  }

  @Test
  void givenIncompleteSourceAndLaterRecoverableConstructionFailure_whenDerivingExisting_thenFailedPrecedesPartialAndKeepsValidatedProgress()
      throws IOException {
    // Arrange
    var ast = produce("class Stored { int field = 1; int first() { return 1; } int second() { return 2; } int third() { return 3; } }");
    var request = new CfgRequest(ast.result().orElseThrow().identity(), List.of(key(ast, "first"), key(ast, "second"), key(ast, "third")),
        CfgProfile.JAVA_BOUNDED);
    var cause = new IllegalStateException("Declared construction observation");
    var runner = new SourceAnalysisRunner(ignored -> {
      throw new AssertionError("Existing derivation must not produce source");
    }, RecoverableCfgFailures.atMethod(key(ast, "second"), cause));

    // Act
    var run = runner.deriveExisting(ast, request);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertSame(ast, run.representation().ast());
    assertSame(request, run.representation().cfg().orElseThrow().request());
    assertSame(request, ((ExistingSelection) run.identity().selection()).request());
    assertEquals(AnalysisCompletion.FAILED, run.completion());
    var failed = run.qualification().reasons().stream().filter(RequestQualificationReason.Method.class::isInstance)
        .map(RequestQualificationReason.Method.class::cast).filter(reason -> reason.outcome().state() == MethodCfgState.FAILED).toList();
    assertEquals(1, failed.size());
    assertSame(cause, failed.getFirst().outcome().failure().orElseThrow());
    assertEquals(ast.completion() == AstCompletion.INCOMPLETE,
        run.qualification().reasons().stream().anyMatch(RequestQualificationReason.Source.class::isInstance));
    assertConstructionProgress(ast, run, cause);
  }

  private static void assertConstructionProgress(AstOutcome ast, AnalysisRun run, Throwable cause) {
    var cfg = run.representation().cfg().orElseThrow();
    assertEquals(CfgState.PARTIAL, cfg.state());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.FAILED, MethodCfgState.AVAILABLE),
        cfg.methods().stream().map(method -> method.state()).toList());
    assertSame(cause, cfg.methods().get(1).failure().orElseThrow());
    assertTrue(cfg.methods().get(1).graph().isEmpty());
    assertEquals(ast.result().orElseThrow().bodyRef(key(ast, "second")), cfg.methods().get(1).body());
    assertEquals(ast.result().orElseThrow().identity(), cfg.methods().getFirst().graph().orElseThrow().identity().astIdentity());
    assertEquals(ast.result().orElseThrow().identity(), cfg.methods().getLast().graph().orElseThrow().identity().astIdentity());
    assertEquals(List.of(MethodCoverageState.AVAILABLE, MethodCoverageState.FAILED, MethodCoverageState.AVAILABLE),
        List.of("first", "second", "third").stream().map(name -> run.methodCoverage(key(ast, name)).orElseThrow().state()).toList());
    assertEquals(List.of("control-flow.construction-failed"), cfg.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    var diagnostics = new ArrayList<>(ast.diagnostics());
    diagnostics.addAll(cfg.diagnostics());
    assertEquals(diagnostics, run.diagnostics());
  }

  @Test
  void givenAstOnlyRequest_whenAnalyzing_thenNoCfgPortIsCalledAndCanonicalDeclarationsAreNotRequested() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var calls = new AtomicInteger();
    var runner = new SourceAnalysisRunner(request -> ast, (source, request) -> {
      calls.incrementAndGet();
      throw new AssertionError("AST-only analysis must not derive control flow");
    });

    // Act
    var run = runner.analyze(request(ast, Optional.empty(), PublicationIntent.DO_NOT_SAVE));

    // Assert
    assertSame(ast, run.representation().ast());
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
    assertTrue(run.qualification().reasons().isEmpty());
    assertTrue(run.representation().cfg().isEmpty());
    assertEquals(0, calls.get());
    assertEquals(MethodCoverageState.NOT_REQUESTED, run.methodCoverage(key(ast, "value")).orElseThrow().state());
  }

  @Test
  void givenExistingSourceAndCallerSelection_whenDeriving_thenProducerIsNeverCalledAndTheExactRequestIsRetained() throws IOException {
    // Arrange
    var ast = produce("abstract class Stored { abstract int absent(); int value() { return 1; } }");
    var request = new CfgRequest(ast.result().orElseThrow().identity(), List.of(key(ast, "absent")), CfgProfile.JAVA_BOUNDED);
    var runner = new SourceAnalysisRunner(ignored -> {
      throw new AssertionError("Existing source derivation must not acquire a producer");
    }, CfgDerivers.standard());

    // Act
    var run = runner.deriveExisting(ast, request);

    // Assert
    assertSame(ast, run.representation().ast());
    assertSame(request, run.representation().cfg().orElseThrow().request());
    assertSame(request, ((ExistingSelection) run.identity().selection()).request());
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
    assertTrue(run.qualification().reasons().isEmpty());
    assertEquals(MethodCoverageState.NOT_APPLICABLE, run.methodCoverage(key(ast, "absent")).orElseThrow().state());
    assertEquals(MethodCoverageState.NOT_REQUESTED, run.methodCoverage(key(ast, "value")).orElseThrow().state());
  }

  @Test
  void givenUnavailableProductionAndInvalidSelection_whenAnalyzing_thenSelectionRemainsUnboundWithoutDeriverAcquisition() throws IOException {
    // Arrange
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory, List.of()),
        configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var selection = new CfgSelection(List.of("missing", "missing", ""), CfgProfile.JAVA_BOUNDED);
    var runner = new SourceAnalysisRunner(ignored -> ast, (source, request) -> {
      throw new AssertionError("Unavailable production must not call a deriver");
    });

    // Act
    var run = runner.analyze(request(ast, Optional.of(selection), PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    assertSame(ast, run.representation().ast());
    assertEquals(AnalysisCompletion.REJECTED, run.completion());
    assertEquals(1, run.qualification().reasons().size());
    assertSame(ast, ((RequestQualificationReason.Source) run.qualification().reasons().getFirst()).outcome());
    assertTrue(run.representation().cfg().isEmpty());
    var retained = (ProductionSelection) run.identity().selection();
    assertTrue(retained.boundIdentity().isEmpty());
    assertSame(selection, retained.selection());
    assertEquals(List.of("missing", "missing", ""), retained.selection().methodKeys());
  }

  @Test
  void givenUnavailableSourceAndExistingRequest_whenDeriving_thenTheCallerBindingSurvivesWithoutAnInventedCfgOutcome() throws IOException {
    // Arrange
    var available = produce("class Stored { int value() { return 1; } }");
    var unavailable = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory, List.of()),
        configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var request = new CfgRequest(available.result().orElseThrow().identity(), List.of(key(available, "value")), CfgProfile.JAVA_BOUNDED);
    var runner = new SourceAnalysisRunner(ignored -> {
      throw new AssertionError("Existing derivation must not produce source");
    }, (source, ignored) -> {
      throw new AssertionError("Unavailable source must not derive");
    });

    // Act
    var run = runner.deriveExisting(unavailable, request);

    // Assert
    assertSame(unavailable, run.representation().ast());
    assertSame(request, ((ExistingSelection) run.identity().selection()).request());
    assertEquals(AnalysisCompletion.REJECTED, run.completion());
    assertEquals(1, run.qualification().reasons().size());
    assertSame(unavailable, ((RequestQualificationReason.Source) run.qualification().reasons().getFirst()).outcome());
    assertTrue(run.representation().cfg().isEmpty());
  }

  @Test
  void givenInvalidProductionSelection_whenAnalyzing_thenRejectedCfgAndEstablishedAstAreBothRetained() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var key = key(ast, "value");
    var selection = new CfgSelection(List.of(key, key, "unknown"), CfgProfile.JAVA_BOUNDED);
    var runner = new SourceAnalysisRunner(ignored -> ast, CfgDerivers.standard());

    // Act
    var run = runner.analyze(request(ast, Optional.of(selection), PublicationIntent.DO_NOT_SAVE));

    // Assert
    assertSame(ast, run.representation().ast());
    assertEquals(AnalysisCompletion.REJECTED, run.completion());
    assertEquals(CfgState.REJECTED, run.representation().cfg().orElseThrow().state());
    assertEquals(selection.methodKeys(), run.representation().cfg().orElseThrow().request().methodKeys());
    assertEquals(MethodCoverageState.REQUEST_REJECTED, run.methodCoverage(key).orElseThrow().state());
    assertFalse(run.diagnostics().isEmpty());
  }

  @Test
  void givenPublicationIntentAndSelectionVariants_whenComputingIdentity_thenOnlyRepresentedSourceAndSelectionChangeTheKey() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var runner = new SourceAnalysisRunner(ignored -> ast, CfgDerivers.standard());
    var empty = Optional.of(new CfgSelection(List.of(), CfgProfile.JAVA_BOUNDED));

    // Act
    var noSave = runner.analyze(request(ast, Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var save = runner.analyze(request(ast, Optional.empty(), PublicationIntent.SAVE_COMPLETE_AST));
    var selectedEmpty = runner.analyze(request(ast, empty, PublicationIntent.DO_NOT_SAVE));
    var existingEmpty = runner.deriveExisting(ast,
        new CfgRequest(ast.result().orElseThrow().identity(), List.of(), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(noSave.identity().key(), save.identity().key());
    assertNotEquals(noSave.identity().key(), selectedEmpty.identity().key());
    assertNotEquals(selectedEmpty.identity().key(), existingEmpty.identity().key());
    assertEquals(AnalysisCompletion.COMPLETE, selectedEmpty.completion());
    assertTrue(selectedEmpty.qualification().reasons().isEmpty());
    assertEquals(AnalysisCompletion.COMPLETE, existingEmpty.completion());
    assertTrue(existingEmpty.qualification().reasons().isEmpty());
  }

  @Test
  void givenDeclaredAbruptCfgFailure_whenDeriving_thenApplicationExceptionRetainsTheEstablishedAstAndExactCfgFailure() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var request = new CfgRequest(ast.result().orElseThrow().identity(), List.of(key(ast, "value")), CfgProfile.JAVA_BOUNDED);
    var cause = new IllegalStateException("Declared construction observation");
    var cfgFailure = new CfgDerivationException(request, List.of(), cause);
    var runner = new SourceAnalysisRunner(ignored -> ast, (source, ignored) -> {
      throw cfgFailure;
    });

    // Act
    var failure = assertThrows(AnalysisDerivationException.class, () -> runner.deriveExisting(ast, request));

    // Assert
    assertSame(ast, failure.ast());
    assertSame(cfgFailure, failure.getCause());
    assertSame(cfgFailure, failure.derivationFailure());
    assertSame(cause, failure.derivationFailure().getCause());
  }

  @Test
  void givenBodylessMethodWithNativeErrorOccurrences_whenDerivingExisting_thenPartialQualificationExplainsEverySlotWithoutChangingMethodState()
      throws IOException {
    // Arrange
    var ast = produce("interface Stored { int absent(); }");
    var result = ast.result().orElseThrow();
    var key = key(ast, "absent");
    var error = new AnalysisDiagnostic("provided:bodyless", "control-flow.provided", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, "Supplied bodyless method error", result.method(key).orElseThrow().origins(),
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var diagnostics = List.of(error, error);
    var method = new MethodCfgOutcome(result.identity(), key, MethodCfgState.NOT_APPLICABLE, Optional.empty(), Optional.empty(),
        List.of(), Optional.empty(), diagnostics);
    var request = new CfgRequest(result.identity(), List.of(key), CfgProfile.JAVA_BOUNDED);
    var cfg = new CfgOutcome(request, CfgState.PARTIAL, List.of(method), diagnostics);
    var runner = new SourceAnalysisRunner(ignored -> {
      throw new AssertionError("Existing source must not be produced again");
    }, (source, invoked) -> cfg);

    // Act
    var run = runner.deriveExisting(ast, request);
    var reasons = run.qualification().reasons().stream().map(RequestQualificationReason.MethodDiagnostic.class::cast).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertSame(ast, run.representation().ast());
    assertSame(cfg, run.representation().cfg().orElseThrow());
    assertEquals(MethodCfgState.NOT_APPLICABLE, method.state());
    assertTrue(method.graph().isEmpty());
    assertEquals(diagnostics, run.diagnostics());
    assertEquals(List.of(0, 1), reasons.stream().map(RequestQualificationReason.MethodDiagnostic::position).toList());
    assertTrue(reasons.stream().allMatch(reason -> reason.outcome() == method));
    assertSame(error, reasons.getFirst().outcome().diagnostics().get(reasons.getFirst().position()));
    assertSame(error, reasons.getLast().outcome().diagnostics().get(reasons.getLast().position()));
    assertFalse(run.isPublishable());
  }

  private AstOutcome produce(String source) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), source))), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
  }

  private static AnalysisRequest request(AstOutcome ast, Optional<CfgSelection> selection, PublicationIntent publication) {
    return new AnalysisRequest(new SourceInput.Snapshot(ast.capturedInput().orElseThrow()), configuration(),
        AstProfile.JAVA_SOURCE_BOUNDED, selection, publication);
  }

  private static CompilerConfiguration configuration() {
    return new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of());
  }

  private static String key(AstOutcome ast, String name) {
    return ast.result().orElseThrow().program().methods().stream().filter(method -> method.name().equals(name))
        .map(method -> method.methodKey()).findFirst().orElseThrow();
  }
}
