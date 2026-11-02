package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.application.AnalysisApplicationService;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisDerivationException;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.CompletedRunPublicationService;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgBlocker;
import org.aquilagraph.cfg.api.CfgDerivationException;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeResultPresentationTest {
  @TempDir Path directory;

  @Test
  void givenBoundedMultiCatch_whenActualCommandRuns_thenNativeQualificationExplainsPartialSourceWithoutInventingEvents() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "class Example { void run() { try {} catch (IllegalArgumentException | IllegalStateException failure) {} } }");
    var streams = new Streams();
    var runner = runner();
    var run = new AtomicReference<AnalysisRun>();
    var service = service(runner, noRepository());
    var command = new AnalyzeCommand(request -> {
      var execution = service.execute(request);
      run.set(execution.run());
      return execution;
    });

    // Act
    var code = command.run(new String[] {directory.toString()}, streams.output, streams.error);

    // Assert
    assertEquals(1, code);
    assertEquals(AnalysisCompletion.PARTIAL, run.get().completion());
    assertEquals(List.of(), run.get().diagnostics());
    assertTrue(streams.out().contains("Completion: PARTIAL"));
    assertTrue(streams.out().contains("AST: INCOMPLETE"));
    assertTrue(streams.out().contains("CFG: COMPLETE"));
    assertTrue(streams.err().contains("Qualification: SOURCE PROFILE_RESTRICTION"));
    assertTrue(streams.err().contains("Qualification: APPLICATION SOURCE"));
    var applicationReason = streams.err().indexOf("Qualification: APPLICATION SOURCE");
    assertEquals(List.of("Qualification: APPLICATION SOURCE", "  Profile: \"java-source-bounded/1\"",
        "  Subject: \"" + run.get().representation().ast().result().orElseThrow().identity().astResultKey() + "\""),
        streams.err().subList(applicationReason, applicationReason + 3));
    assertFalse(streams.err().contains("Diagnostic:"));
    assertFalse(streams.err().contains("frontend.unsupported-tree-kind.union-type"));
  }

  @Test
  void givenCompleteSourceAndRepositoryFailure_whenActualCommandRuns_thenComputationAndFailureRemainIndependent() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static void run() {} }");
    var cause = new RepositoryException("database unavailable", new IllegalStateException("offline"));
    var service = service(runner(), () -> { throw cause; });
    var failure = new AtomicReference<AnalysisRepositoryException>();
    var command = new AnalyzeCommand(request -> {
      try {
        return service.execute(request);
      } catch (AnalysisRepositoryException exception) {
        failure.set(exception);
        throw exception;
      }
    });
    var streams = new Streams();

    // Act
    var code = command.run(new String[] {directory.toString(), "--persist"}, streams.output, streams.error);

    // Assert
    assertEquals(3, code);
    assertSame(cause, failure.get().repositoryFailure());
    assertEquals(AnalysisCompletion.COMPLETE, failure.get().analysisRun().completion());
    assertTrue(streams.out().contains("Completion: COMPLETE"));
    assertEquals("Repository: FAILED", streams.out().getLast());
    assertEquals(List.of("Repository save failed: database unavailable"), streams.err());
  }

  @Test
  void givenNormalizedCommandRequest_whenActualCommandRuns_thenOneFaithfulRequestAndCompleteResultReachBorrowedStreams() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static void run() {} }");
    var requests = new ArrayList<AnalysisRequest>();
    var service = service(runner(), noRepository());
    var command = new AnalyzeCommand(request -> {
      requests.add(request);
      return service.execute(request);
    });
    var streams = new Streams();

    // Act
    var code = command.run(new String[] {directory.resolve("child").resolve("..").toString(), "--release", "25"}, streams.output, streams.error);
    streams.output.println("still writable");
    streams.error.println("still writable");

    // Assert
    assertEquals(0, code);
    assertEquals(1, requests.size());
    var request = requests.getFirst();
    assertEquals(directory.toAbsolutePath().normalize(), ((SourceInput.Root) request.input()).path());
    assertEquals(CompilerConfiguration.forRelease(JavaRelease.JAVA_25), request.configuration());
    assertEquals(AstProfile.JAVA_SOURCE_BOUNDED, request.astProfile());
    assertEquals(Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), request.cfgSelection());
    assertEquals(PublicationIntent.DO_NOT_SAVE, request.publicationIntent());
    assertEquals("still writable", streams.out().getLast());
    assertEquals(List.of("still writable"), streams.err());
  }

  @Test
  void givenInvalidArgumentsAndNamedFailures_whenActualCommandRuns_thenAdmissionExitsAndSeparateChannelsRemainExact() {
    // Arrange
    var streams = new Streams();
    var command = new AnalyzeCommand(request -> { throw new AssertionError("Rejected arguments must not invoke analysis"); });

    // Act
    var rejected = command.run(new String[] {"source", "--persist", "--persist"}, streams.output, streams.error);

    // Assert
    assertEquals(2, rejected);
    assertEquals(List.of(), streams.out());
    assertEquals(List.of("--persist may be supplied only once", AnalyzeCommandParser.USAGE), streams.err());
    for (var failure : List.of(new AnalysisInputException("source unreadable", new IOException("unreadable")), new AnalysisEnvironmentException("compiler unavailable"))) {
      // Arrange
      var captured = new Streams();
      var failing = new AnalyzeCommand(request -> { throw failure; });
      // Act
      var code = failing.run(new String[] {"source"}, captured.output, captured.error);
      // Assert
      assertEquals(failure instanceof AnalysisInputException ? 2 : 1, code);
      assertEquals(List.of(), captured.out());
      assertEquals(List.of(failure instanceof AnalysisInputException ? "Input rejected: source unreadable" : "Analysis failed: compiler unavailable"), captured.err());
    }
  }

  @Test
  void givenAbruptDerivationWithRealPrefix_whenActualCommandRuns_thenExactCarrierEscapesWithoutOutputOrRepositoryAcquisition() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static int first() { return 1; } static int second() { return 2; } }");
    var source = new AtomicReference<AstOutcome>();
    var carrier = new AtomicReference<CfgDerivationException>();
    var cause = new IllegalStateException("construction interrupted");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var runner = new SourceAnalysisRunner(request -> {
      var produced = frontend.produce(request);
      source.set(produced);
      return produced;
    }, (ast, request) -> {
      var prefix = CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), request.methodKeys().subList(0, 1), request.profile()));
      var failure = new CfgDerivationException(request, prefix.methods(), cause);
      carrier.set(failure);
      throw failure;
    });
    var command = new AnalyzeCommand(service(runner, noRepository()));
    var streams = new Streams();

    // Act
    var observed = assertThrows(AnalysisDerivationException.class,
        () -> command.run(new String[] {directory.toString(), "--persist"}, streams.output, streams.error));

    // Assert
    assertSame(source.get(), observed.ast());
    assertSame(carrier.get(), observed.derivationFailure());
    assertSame(cause, observed.derivationFailure().getCause());
    assertEquals(source.get().result().orElseThrow().identity(), observed.derivationFailure().request().astIdentity());
    assertEquals(source.get().result().orElseThrow().program().methods().stream().map(method -> method.methodKey()).toList(), observed.derivationFailure().request().methodKeys());
    assertEquals(1, observed.derivationFailure().completedMethods().size());
    assertEquals(MethodCfgState.AVAILABLE, observed.derivationFailure().completedMethods().getFirst().state());
    assertTrue(observed.derivationFailure().completedMethods().getFirst().graph().isPresent());
    assertEquals(List.of(), streams.out());
    assertEquals(List.of(), streams.err());
  }

  @Test
  void givenUnmatchedPrimaryWithSuppressedRepositoryFailure_whenActualCommandRuns_thenPrimaryAndSuppressedEvidenceEscape() {
    // Arrange
    var primary = new IllegalStateException("unmatched primary");
    var suppressed = new AnalysisRepositoryException(CliTestFixtures.completedRun(), new RepositoryException("close failed", new IllegalStateException("close cause")));
    primary.addSuppressed(suppressed);
    var command = new AnalyzeCommand(request -> { throw primary; });
    var streams = new Streams();
    // Act
    var observed = assertThrows(IllegalStateException.class, () -> command.run(new String[] {"source"}, streams.output, streams.error));
    // Assert
    assertSame(primary, observed);
    assertSame(suppressed, observed.getSuppressed()[0]);
    assertEquals(List.of(), streams.out());
    assertEquals(List.of(), streams.err());
  }

  @Test
  void givenRecoverableConstructionFailure_whenPresented_thenFailedCompletionRetainsSourceFailureAndExactMethodCause() throws IOException {
    // Arrange
    var ast = produce("interface Example { static int run() { return 1; } }");
    var key = ast.result().orElseThrow().program().methods().getFirst().methodKey();
    var cause = new IllegalStateException("construction\nfailed\t\"once\"");
    var runner = new SourceAnalysisRunner(request -> ast, RecoverableCfgFailures.atMethod(key, cause));
    var run = runner.deriveExisting(ast, new CfgRequest(ast.result().orElseThrow().identity(), List.of(key), CfgProfile.JAVA_BOUNDED));
    var streams = new Streams();
    // Act
    var code = present(run, streams);
    // Assert
    assertEquals(1, code);
    assertEquals(AnalysisCompletion.FAILED, run.completion());
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertSame(cause, run.representation().cfg().orElseThrow().methods().getFirst().failure().orElseThrow());
    assertTrue(streams.out().contains("Completion: FAILED"));
    assertTrue(streams.out().contains("AST: COMPLETE"));
    assertTrue(streams.out().contains("CFG: PARTIAL"));
    assertTrue(streams.err().contains("Qualification: CFG FAILED"));
    assertTrue(streams.err().contains("  Failure type: \"java.lang.IllegalStateException\""));
    assertTrue(streams.err().contains("  Failure message: \"construction\\nfailed\\t\\\"once\\\"\""));
    assertTrue(streams.err().contains("  Related diagnostic: \"" + run.diagnostics().getFirst().diagnosticKey() + "\" occurrence=1"));
  }

  @Test
  void givenAstOnlyAndEmptyExplicitSelections_whenPresented_thenAbsentRequestAndEmptyRequestRemainDistinct() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "interface Example { void absent(); static void run() {} }")));
    var runner = runner();
    var astOnly = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED, Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var empty = runner.deriveExisting(astOnly.representation().ast(), new CfgRequest(astOnly.representation().ast().result().orElseThrow().identity(), List.of(), CfgProfile.JAVA_BOUNDED));
    var absentStreams = new Streams();
    var emptyStreams = new Streams();
    // Act
    present(astOnly, absentStreams);
    present(empty, emptyStreams);
    // Assert
    assertTrue(absentStreams.out().containsAll(List.of("CFG: NOT_REQUESTED", "CFG profile: not-requested", "CFG graph model: not-requested", "CFG selection: NOT_REQUESTED", "CFG binding: NOT_REQUESTED")));
    assertTrue(emptyStreams.out().containsAll(List.of("CFG: COMPLETE", "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/2\"", "CFG selection: METHODS")));
    assertFalse(emptyStreams.out().stream().anyMatch(line -> line.startsWith("Selected method:")));
    assertTrue(absentStreams.out().stream().filter(line -> line.startsWith("Method CFG:")).allMatch(line -> line.endsWith(" NOT_REQUESTED")));
    assertEquals(List.of(), absentStreams.err());
    assertEquals(List.of(), emptyStreams.err());
  }

  @Test
  void givenRejectedProfilesAndDuplicateUnknownSelections_whenPresented_thenRequestedTextBindingAndEverySelectionSlotSurvive() throws IOException {
    // Arrange
    var ast = produce("interface Example { static void run() {} }");
    var key = ast.result().orElseThrow().program().methods().getFirst().methodKey();
    var profile = new CfgProfile("policy\r\n\"\\\t\u0001\u007f", "model\r\n\"\\\t\u0002");
    var foreign = produce("interface Example { static void run() { return; } }");
    var request = new CfgRequest(foreign.result().orElseThrow().identity(), List.of(key, key, "unknown"), profile);
    var run = runner().deriveExisting(ast, request);
    var streams = new Streams();
    // Act
    var code = present(run, streams);
    // Assert
    assertEquals(2, code);
    assertSame(request, run.representation().cfg().orElseThrow().request());
    assertTrue(streams.out().contains("CFG profile: \"policy\\r\\n\\\"\\\\\\t\\u0001\\u007f\""));
    assertTrue(streams.out().contains("CFG graph model: \"model\\r\\n\\\"\\\\\\t\\u0002\""));
    assertFalse(ast.result().orElseThrow().identity().equals(request.astIdentity()));
    assertEquals(1, streams.out().stream().filter(line -> line.startsWith("CFG profile:")).count());
    assertEquals(1, streams.out().stream().filter(line -> line.startsWith("CFG graph model:")).count());
    assertEquals(List.of("Selected method: \"" + key + "\"", "Selected method: \"" + key + "\"", "Selected method: \"unknown\""), streams.out().stream().filter(line -> line.startsWith("Selected method:")).toList());
    assertTrue(streams.out().contains("CFG binding: " + request.astIdentity().astResultKey()));
    assertTrue(streams.err().contains("Qualification: CFG REJECTED"));
    var sentinel = runner().deriveExisting(ast, new CfgRequest(request.astIdentity(), List.of(), new CfgProfile("not-requested", "not-requested")));
    var literal = new Streams();
    present(sentinel, literal);
    assertTrue(literal.out().containsAll(List.of("CFG profile: \"not-requested\"", "CFG graph model: \"not-requested\"")));
  }

  @Test
  void givenReverseMethodSelection_whenPresented_thenNativeEventsStayCanonicalAndReasonsRetainRequestOrder() throws IOException {
    // Arrange
    var ast = produce("interface Example { static int first(int x) { (x) = 1; return x; } static int second(int x) { (x) = 2; return x; } }");
    var methods = ast.result().orElseThrow().program().methods();
    var first = methods.getFirst().methodKey();
    var second = methods.getLast().methodKey();
    var run = runner().deriveExisting(ast, new CfgRequest(ast.result().orElseThrow().identity(), List.of(second, first), CfgProfile.JAVA_BOUNDED));
    var cfg = run.representation().cfg().orElseThrow();
    var streams = new Streams();
    // Act
    present(run, streams);
    // Assert
    assertTrue(streams.out().containsAll(List.of("Completion: PARTIAL", "AST: COMPLETE", "CFG: PARTIAL")));
    assertEquals(List.of(cfg.methods().getLast().diagnostics().getFirst(), cfg.methods().getFirst().diagnostics().getFirst()), run.diagnostics());
    assertEquals(run.diagnostics().stream().map(diagnostic -> "Diagnostic: \"" + diagnostic.diagnosticKey() + "\"").toList(), streams.err().stream().filter(line -> line.startsWith("Diagnostic:")).toList());
    assertEquals(List.of("  Subject: \"" + second + "\"", "  Subject: \"" + first + "\"", "  Subject: \"" + second + "\"", "  Subject: \"" + first + "\""), streams.err().stream().filter(line -> line.startsWith("  Subject:")).toList());
    assertEquals(List.of("  Related diagnostic: \"" + cfg.methods().getFirst().diagnostics().getFirst().diagnosticKey() + "\" occurrence=2", "  Related diagnostic: \"" + cfg.methods().getLast().diagnostics().getFirst().diagnosticKey() + "\" occurrence=1", "  Related diagnostic: \"" + cfg.methods().getFirst().diagnostics().getFirst().diagnosticKey() + "\" occurrence=2", "  Related diagnostic: \"" + cfg.methods().getLast().diagnostics().getFirst().diagnosticKey() + "\" occurrence=1"), streams.err().stream().filter(line -> line.startsWith("  Related diagnostic:")).toList());
  }

  @Test
  void givenEqualAndSameKeyUnequalApplicationErrors_whenPresented_thenEveryReasonUsesItsExactNativeOccurrenceAndEscapedValue() {
    // Arrange
    var original = CliTestFixtures.analyze("interface Example { static int run(int x) { (x) = 1; return x; } }");
    var first = new AnalysisDiagnostic("shared:key", "application.test", DiagnosticStage.INPUT, DiagnosticSeverity.ERROR, "first\n\r\t\"\\\u0000\u007f", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var second = new AnalysisDiagnostic("shared:key", "application.test", DiagnosticStage.INPUT, DiagnosticSeverity.ERROR, "second", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(first, first, second));
    var streams = new Streams();
    // Act
    present(run, streams);
    // Assert
    assertEquals(List.of("  Occurrence: 1", "  Occurrence: 2", "  Occurrence: 3", "  Occurrence: 4"), streams.err().stream().filter(line -> line.startsWith("  Occurrence:")).toList());
    assertEquals(List.of("  Related diagnostic: \"shared:key\" occurrence=2", "  Related diagnostic: \"shared:key\" occurrence=3", "  Related diagnostic: \"shared:key\" occurrence=4"), streams.err().stream().filter(line -> line.startsWith("  Related diagnostic: \"shared:key\"")).toList());
    assertEquals(List.of("  Message: \"first\\n\\r\\t\\\"\\\\\\u0000\\u007f\"", "  Message: \"first\\n\\r\\t\\\"\\\\\\u0000\\u007f\"", "  Message: \"second\""), streams.err().stream().filter(line -> line.startsWith("  Message:")).skip(1).toList());
  }

  @Test
  void givenMultipleCapturedAndForeignOrigins_whenPresented_thenRangesKeysOrderAndMissingPathsRemainExact() {
    // Arrange
    var original = CliTestFixtures.completedRun();
    var captured = original.representation().ast().capturedInput().orElseThrow().sourceFiles().getFirst();
    var foreign = SourceFile.fromContent(Path.of("Outside.java"), "class Outside {}");
    var origins = List.of(new SourceSpan(captured.sourceFileKey(), 1, 3), new SourceSpan(foreign.sourceFileKey(), 2, 5), new SourceSpan(captured.sourceFileKey(), 1, 3));
    var diagnostic = new AnalysisDiagnostic("origin:notice", "application.notice", DiagnosticStage.INPUT, DiagnosticSeverity.INFO, "notice", origins, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(diagnostic));
    var streams = new Streams();
    // Act
    var code = present(run, streams);
    // Assert
    assertEquals(0, code);
    assertEquals(List.of("  Origin: \"" + captured.sourceFileKey() + "\" file=\"Example.java\" range=[1,3)", "  Origin: \"" + foreign.sourceFileKey() + "\" file=unavailable range=[2,5)", "  Origin: \"" + captured.sourceFileKey() + "\" file=\"Example.java\" range=[1,3)"), streams.err().stream().filter(line -> line.startsWith("  Origin:")).toList());
  }

  @Test
  void givenBlockerWithEvidenceOutsideNativeStream_whenPresented_thenEvidenceRetainsFrontendOwnershipWithoutInventedOccurrence() throws IOException {
    // Arrange
    var ast = produce("interface Example { static void run() {} }");
    var source = ast.result().orElseThrow();
    var key = source.program().methods().getFirst().methodKey();
    var primary = new AnalysisDiagnostic("cfg:primary", "control-flow.evidence", DiagnosticStage.CONTROL_FLOW, DiagnosticSeverity.ERROR, "required evidence is erroneous", List.of(), AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var evidence = new AnalysisDiagnostic("frontend:evidence", "frontend.evidence", DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, "original producer wording", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var blocker = new CfgBlocker(MethodCfgState.ERRONEOUS_EVIDENCE, source.identity(), key, Optional.empty(), "binding", primary, Optional.of(evidence));
    var method = new MethodCfgOutcome(source.identity(), key, blocker.kind(), source.bodyRef(key), Optional.empty(), List.of(blocker), Optional.empty(), List.of(primary));
    var request = new CfgRequest(source.identity(), List.of(key), CfgProfile.JAVA_BOUNDED);
    var cfg = new CfgOutcome(request, CfgState.PARTIAL, List.of(method), List.of(primary));
    var run = new SourceAnalysisRunner(ignored -> ast, (input, selected) -> cfg).deriveExisting(ast, request);
    var streams = new Streams();
    // Act
    present(run, streams);
    // Assert
    assertEquals(List.of(primary), run.diagnostics());
    assertEquals(List.of("Diagnostic: \"cfg:primary\""), streams.err().stream().filter(line -> line.startsWith("Diagnostic:")).toList());
    assertTrue(streams.err().containsAll(List.of("  Evidence diagnostic: \"frontend:evidence\"", "    Severity: ERROR", "    Stage: FRONTEND", "    Cause: OTHER_ANALYSIS_ERROR", "    Code: \"frontend.evidence\"", "    Message: \"original producer wording\"", "    Origins: unavailable", "  Related diagnostic: \"cfg:primary\" occurrence=1")));
    assertEquals(1, streams.err().stream().filter(line -> line.contains("Occurrence:")).count());
    assertFalse(streams.err().stream().anyMatch(line -> line.startsWith("  Related diagnostic: \"frontend:evidence\"")));
  }

  @Test
  void givenSuppliedBodylessMethodError_whenPresented_thenNativeEventAndApplicationReasonRemainWithoutSyntheticBlockers() throws IOException {
    // Arrange
    var ast = produce("interface Example { void absent(); }");
    var source = ast.result().orElseThrow();
    var key = source.program().methods().getFirst().methodKey();
    var diagnostic = new AnalysisDiagnostic("cfg:bodyless", "control-flow.notice", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, "Supplied bodyless method error", List.of(), AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var method = new MethodCfgOutcome(source.identity(), key, MethodCfgState.NOT_APPLICABLE, Optional.empty(), Optional.empty(),
        List.of(), Optional.empty(), List.of(diagnostic));
    var request = new CfgRequest(source.identity(), List.of(key), CfgProfile.JAVA_BOUNDED);
    var cfg = new CfgOutcome(request, CfgState.PARTIAL, List.of(method), List.of(diagnostic));
    var run = new SourceAnalysisRunner(ignored -> ast, (input, selected) -> cfg).deriveExisting(ast, request);
    var streams = new Streams();

    // Act
    var code = present(run, streams);

    // Assert
    assertEquals(1, code);
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertTrue(streams.out().contains("Method CFG: \"" + key + "\" NOT_APPLICABLE"));
    assertEquals(List.of("Diagnostic: \"cfg:bodyless\"", "  Occurrence: 1", "  Severity: ERROR", "  Stage: CONTROL_FLOW",
        "  Cause: CONTROL_FLOW_UNAVAILABLE", "  Code: \"control-flow.notice\"", "  Message: \"Supplied bodyless method error\"",
        "  Origins: unavailable", "Qualification: APPLICATION METHOD_DIAGNOSTIC", "  Profile: \"java-cfg-bounded/1\"",
        "  Subject: \"" + key + "\"", "  Origins: unavailable", "  Related diagnostic: \"cfg:bodyless\" occurrence=1"), streams.err());
    assertEquals(List.of(), method.blockers());
  }

  private AstOutcome produce(String text) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), text))), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED);
  }

  private static SourceAnalysisRunner runner() {
    return new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
  }

  private static AnalysisApplicationService service(SourceAnalysisRunner runner, AnalysisRunRepositoryFactory repositories) {
    return new AnalysisApplicationService(runner, new AstRetentionService(context -> { throw new AssertionError("AST repository must not open"); }), new CompletedRunPublicationService(repositories));
  }

  private static AnalysisRunRepositoryFactory noRepository() {
    return () -> { throw new AssertionError("Repository must not open"); };
  }

  private static int present(AnalysisRun run, Streams streams) {
    return new AnalyzeCommandPresenter().present(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)), streams.output, streams.error);
  }

  private static final class Streams {
    private final ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
    private final PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);

    List<String> out() { return outputBytes.toString(StandardCharsets.UTF_8).lines().toList(); }
    List<String> err() { return errorBytes.toString(StandardCharsets.UTF_8).lines().toList(); }
  }
}
