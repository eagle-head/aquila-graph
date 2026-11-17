package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisDerivationException;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.MethodCoverage;
import org.aquilagraph.application.MethodCoverageState;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.RequestQualificationReason;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgDerivationException;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class AnalyzeCommandTest {
  @TempDir Path directory;

  @Test
  void givenSupportedArrayClone_whenCommandComputesWithoutPublication_thenCompleteSourceAndGraphAreReported() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"),
        "interface Example { static int[] copy(int[] values) { return values.clone(); } }");
    var streams = new CliStreams();
    var command = new AnalyzeCommand(CliTestFixtures.service(CliTestFixtures.runner(), CliTestFixtures.repositoriesMustNotOpen()));

    // Act
    var invocation = new CommandRunner(command).run(new String[] {directory.toString()}, CommandDestination.printStream(streams.output()),
        CommandDestination.printStream(streams.error()));

    // Assert
    assertEquals(CommandExitPolicy.EXIT_COMPLETED, invocation.exitCode(), streams.errorText());
    assertTrue(streams.outputLines().containsAll(List.of("Completion: COMPLETE", "AST: COMPLETE", "CFG: COMPLETE",
        "CFG graph model: \"java-cfg/3\"", "Repository: NOT_REQUESTED")), streams.outputText());
    assertEquals(List.of(), streams.errorLines());
  }

  @TestFactory
  Stream<DynamicTest> givenPersistenceOptionsInDifferentOrders_whenCommandRuns_thenExactNormalizedRequestAndSavedResultAreObserved() {
    // Arrange
    var root = directory.resolve("child").resolve("..").toString();
    var options = List.of(List.of(root, "--release", "25", "--persist"),
        List.of("--persist", root, "--release", "25"), List.of("--release", "25", "--persist", root));

    return options.stream().map(tokens -> DynamicTest.dynamicTest(
        "givenOptionOrder" + tokens.indexOf(root) + "_whenCommandRuns_thenRequestAndSavedResultArePreserved", () -> {
          // Arrange
          var run = CliTestFixtures.completedRun();
          var useCase = new CapturingAnalysisUseCase(request -> CliTestFixtures.saved(run));
          var command = new AnalyzeCommand(useCase);
          var streams = new CliStreams();

          // Act
          var code = new CommandRunner(command).run(tokens.toArray(String[]::new), CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();

          // Assert
          assertEquals(CommandExitPolicy.EXIT_COMPLETED, code);
          assertEquals(List.of(new AnalysisRequest(new AnalysisComputationRequest(
              new AstProductionRequest(directory.toAbsolutePath().normalize(), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED), Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))), PublicationIntent.SAVE_COMPLETED)), useCase.requests());
          assertTrue(streams.outputLines().contains("Completion: COMPLETE"));
          assertEquals(List.of("Repository: SAVED", "Publication intent: SAVE_COMPLETED", "Publication subject: COMPLETED_RUN \"" + run.identity().key() + "\"", "Commit knowledge: KNOWN_COMMIT"), streams.outputLines().subList(streams.outputLines().size() - 4, streams.outputLines().size()));
          assertEquals(List.of(), streams.errorLines());
        }));
  }

  @Test
  void givenDiagnosedJavaSourceRoot_whenCommandRuns_thenIncompleteSummaryIsPrinted() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "class Example { void run() { while (true) { } } }");
    var streams = new CliStreams();
    var command = new AnalyzeCommand(CliTestFixtures.service(CliTestFixtures.runner(), CliTestFixtures.repositoriesMustNotOpen()));

    // Act
    var exitCode =
        new CommandRunner(command).run(new String[] {directory.toString()}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();

    // Assert
    assertEquals(CommandExitPolicy.EXIT_INCOMPLETE, exitCode);
    assertTrue(streams.outputText().contains("Completion: PARTIAL"));
    assertTrue(streams.outputText().contains("Java release: 25"));
    assertTrue(
        streams.outputText().contains("Repository: NOT_REQUESTED"));
    assertTrue(streams.errorText()
        .contains("Qualification: SOURCE PROFILE_RESTRICTION"));
  }

  @Test
  void givenExplicitSupportedRelease_whenCommandRuns_thenSelectedReleaseIsUsed() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "class Example { void run() { while (true) { } } }");
    var streams = new CliStreams();
    var command = new AnalyzeCommand(CliTestFixtures.service(CliTestFixtures.runner(), CliTestFixtures.repositoriesMustNotOpen()));

    // Act
    var exitCode =
        new CommandRunner(command).run(new String[] {directory.toString(), "--release", "25"}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();

    // Assert
    assertEquals(CommandExitPolicy.EXIT_INCOMPLETE, exitCode);
    assertTrue(streams.outputText().contains("Java release: 25"));
    assertTrue(streams.errorText()
        .contains("Qualification: SOURCE PROFILE_RESTRICTION"));
  }

  @Test
  void givenMalformedUtf8Source_whenCommandRuns_thenInputIsRejected() throws IOException {
    // Arrange
    var malformedSource = directory.resolve("Malformed.java");
    Files.write(
        malformedSource, new byte[] {(byte) 0xC3, (byte) 0x28});
    var streams = new CliStreams();
    var command = new AnalyzeCommand(CliTestFixtures.service(CliTestFixtures.runner(), CliTestFixtures.repositoriesMustNotOpen()));

    // Act
    var exitCode =
        new CommandRunner(command).run(new String[] {directory.toString(), "--release", "25"}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();

    // Assert
    assertEquals(CommandExitPolicy.EXIT_REJECTED, exitCode);
    assertEquals("", streams.outputText());
    assertTrue(streams.errorText().contains("Input rejected:"));
    assertTrue(streams.errorText().contains(malformedSource.toString().replace("\\", "\\\\")));
    assertFalse(streams.errorText().contains("Analysis failed:"));
  }

  @Test
  void givenIncompleteAnalysisAndRepositoryRequest_whenSaveRuns_thenRepositoryIsNotOpened()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "class Example { void run() { while (true) { } } }");
    var streams = new CliStreams();
    var command = new AnalyzeCommand(CliTestFixtures.service(CliTestFixtures.runner(), CliTestFixtures.repositoriesMustNotOpen()));

    // Act
    var exitCode =
        new CommandRunner(command).run(new String[] {directory.toString(), "--release", "25", "--persist"}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();

    // Assert
    assertEquals(CommandExitPolicy.EXIT_INCOMPLETE, exitCode);
    assertTrue(streams.outputText().contains("Completion: PARTIAL"));
    assertTrue(streams.outputText().contains("Repository: NOT_ELIGIBLE"));
    assertTrue(streams.errorText()
        .contains("Qualification: SOURCE PROFILE_RESTRICTION"));
  }

  @Test
  void givenBoundedMultiCatch_whenActualCommandRuns_thenNativeQualificationExplainsPartialSourceWithoutInventingEvents() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "class Example { void run() { try {} catch (IllegalArgumentException | IllegalStateException failure) {} } }");
    var streams = new CliStreams();
    var runner = CliTestFixtures.runner();
    var run = new AtomicReference<AnalysisRun>();
    var service = CliTestFixtures.service(runner, CliTestFixtures.repositoriesMustNotOpen());
    var command = new AnalyzeCommand(request -> {
      var execution = service.execute(request);
      run.set(execution.run());
      return execution;
    });

    // Act
    var code = new CommandRunner(command).run(new String[] {directory.toString()}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();

    // Assert
    assertEquals(1, code);
    assertEquals(AnalysisCompletion.PARTIAL, run.get().completion());
    assertEquals(List.of(), run.get().diagnostics().values());
    assertTrue(streams.outputLines().contains("Completion: PARTIAL"));
    assertTrue(streams.outputLines().contains("AST: INCOMPLETE"));
    assertTrue(streams.outputLines().contains("CFG: COMPLETE"));
    assertTrue(streams.errorLines().contains("Qualification: SOURCE PROFILE_RESTRICTION"));
    assertTrue(streams.errorLines().contains("Qualification: APPLICATION SOURCE"));
    var applicationReason = streams.errorLines().indexOf("Qualification: APPLICATION SOURCE");
    assertEquals(List.of("Qualification: APPLICATION SOURCE", "  Profile: \"java-source-bounded/1\"",
        "  Subject: \"" + run.get().representation().ast().result().orElseThrow().identity().astResultKey() + "\""),
        streams.errorLines().subList(applicationReason, applicationReason + 3));
    assertFalse(streams.errorLines().stream().anyMatch(line -> line.startsWith("Diagnostic:")));
    assertFalse(streams.errorLines().stream().anyMatch(line -> line.contains("frontend.unsupported-tree-kind.union-type")));
  }

  @Test
  void givenBodylessAvailableAndRefusedMethods_whenCliAnalyzesAllBodies_thenExactSummaryAndNativeOwnersSurvive() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), """
        interface Example {
          void bodyless();
          static int available() { return 1; }
          static int refused(int value) { (value) = 1; return value; }
        }
        """);
    var runner = new CommandRunner(new AnalyzeCommand(
        CliTestFixtures.service(CliTestFixtures.runner(), CliTestFixtures.repositoriesMustNotOpen())));
    var streams = new CliStreams();

    // Act
    var invocation = runner.run(new String[] {directory.toString()}, CommandDestination.printStream(streams.output()),
        CommandDestination.printStream(streams.error()));

    // Assert
    assertEquals(1, invocation.exitCode());
    var run = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, invocation.outcome()).execution().run();
    var coverage = run.methodCoverages();
    var ast = run.representation().ast().result().orElseThrow();
    var declarations = ast.program().methods();
    var keys = declarations.stream().map(method -> method.methodKey()).toList();
    assertEquals(List.of("bodyless", "available", "refused"), declarations.stream().map(method -> method.name()).toList());
    assertEquals(List.of(MethodCoverageState.NOT_REQUESTED, MethodCoverageState.AVAILABLE, MethodCoverageState.UNSUPPORTED),
        coverage.stream().map(MethodCoverage::state).toList());
    var cfg = run.representation().cfg().orElseThrow();
    var selected = keys.subList(1, 3);
    assertEquals(selected, cfg.request().methodKeys());
    assertEquals(CfgProfile.JAVA_BOUNDED, cfg.request().profile());
    for (var index = 0; index < keys.size(); index++) {
      var row = coverage.get(index);
      assertSame(declarations.get(index), row.declaration());
      assertEquals(row, run.methodCoverage(keys.get(index)).orElseThrow());
    }
    assertTrue(coverage.getFirst().cfg().isEmpty());
    assertSame(cfg.methods().getFirst(), coverage.get(1).cfg().orElseThrow());
    assertSame(cfg.methods().getLast(), coverage.getLast().cfg().orElseThrow());
    var refusal = cfg.methods().getLast();
    var reason = assertInstanceOf(RequestQualificationReason.Method.class, run.qualification().reasons().getFirst());
    assertSame(refusal, reason.outcome());
    var occurrence = run.diagnostics().occurrences().getFirst();
    assertSame(refusal.diagnostics().getFirst(), run.diagnostics().values().getFirst());
    assertEquals(List.of(occurrence), run.diagnostics().relatedTo(reason));
    var expectedOutput = new ArrayList<>(List.of("Run: \"" + run.identity().key() + "\"",
        "Software version: \"" + ast.identity().softwareSignature() + "\"",
        "Completion: PARTIAL", "Java release: 25", "Java source files: 1", "AST: COMPLETE", "AST profile: \"java-source-bounded/1\"",
        "CFG: PARTIAL", "CFG profile: \"java-cfg-bounded/1\"", "CFG graph model: \"java-cfg/3\"", "CFG selection: ALL_BODIES",
        "CFG binding: \"" + ast.identity().astResultKey() + "\"", "Selected method: \"" + keys.get(1) + "\"",
        "Selected method: \"" + keys.get(2) + "\"", "Java types: 1", "Java methods: 3", "Analyzed method bodies: 1",
        "CFG operations: 2", "CFG nodes: 4", "CFG edges: 3", "Method CFG: \"" + keys.getFirst() + "\" NOT_REQUESTED",
        "Method CFG: \"" + keys.get(1) + "\" AVAILABLE", "Method CFG: \"" + keys.get(2) + "\" UNSUPPORTED",
        "Repository: NOT_REQUESTED"));
    expectedOutput.addAll(ReportTextExpectations.noPublication(run));
    assertEquals(String.join(System.lineSeparator(), expectedOutput) + System.lineSeparator(), streams.outputText());
    var blocker = refusal.blockers().getFirst();
    var diagnostic = refusal.diagnostics().getFirst();
    var span = diagnostic.origins().getFirst();
    var origin = "  Origin: \"" + span.sourceFileKey() + "\" file=\"Example.java\" range=[" + span.startOffset()
        + "," + span.endOffsetExclusive() + ")";
    var link = "  Related diagnostic: \"" + diagnostic.diagnosticKey() + "\" occurrence=1";
    var methodHeader = List.of("  Profile: \"java-cfg-bounded/1\"", "  Subject: \"" + keys.getLast() + "\"",
        "  AST result: \"" + ast.identity().astResultKey() + "\"", "  Body: WRITTEN",
        "  Body occurrence: \"" + ast.bodyRef(keys.getLast()).orElseThrow().sourceOccurrenceKey().orElseThrow() + "\"");
    var expectedError = new ArrayList<>(List.of("Diagnostic: \"" + diagnostic.diagnosticKey() + "\"", "  Occurrence: 1",
        "  Owner: CFG_METHOD", "  Native position: 0", "  Owner method: \"" + keys.getLast() + "\"", "  Severity: ERROR",
        "  Stage: CONTROL_FLOW", "  Cause: CONTROL_FLOW_UNAVAILABLE", "  Code: \"" + diagnostic.code() + "\"",
        "  Message: \"" + diagnostic.message() + "\"", origin, "Qualification: CFG UNSUPPORTED"));
    expectedError.addAll(methodHeader);
    expectedError.addAll(List.of("  Source occurrence: \"" + blocker.occurrence().orElseThrow().nodeKey() + "\"",
        "  Slot: \"" + blocker.slot() + "\"", origin, link, "Qualification: APPLICATION METHOD"));
    expectedError.addAll(methodHeader);
    expectedError.addAll(List.of(origin, link));
    assertEquals(String.join(System.lineSeparator(), expectedError) + System.lineSeparator(), streams.errorText());
  }

  @Test
  void givenCompleteSourceAndRepositoryFailure_whenActualCommandRuns_thenComputationAndFailureRemainIndependent() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static void run() {} }");
    var cause = new RepositoryException("database unavailable", new IllegalStateException("offline"));
    var service = CliTestFixtures.service(CliTestFixtures.runner(), context -> { throw cause; });
    var execution = new AtomicReference<AnalysisExecution>();
    var command = new AnalyzeCommand(request -> {
      var result = service.execute(request);
      execution.set(result);
      return result;
    });
    var streams = new CliStreams();

    // Act
    var code = new CommandRunner(command).run(new String[] {directory.toString(), "--persist"}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();

    // Assert
    assertEquals(3, code);
    assertSame(cause, execution.get().publication().failure().orElseThrow().effectivePrimary());
    assertEquals(AnalysisCompletion.COMPLETE, execution.get().run().completion());
    assertTrue(streams.outputLines().contains("Completion: COMPLETE"));
    assertEquals(List.of("Repository: FAILED", "Publication intent: SAVE_COMPLETED", "Publication subject: COMPLETED_RUN \"" + execution.get().run().identity().key() + "\"", "Commit knowledge: NOT_STARTED"), streams.outputLines().subList(streams.outputLines().size() - 4, streams.outputLines().size()));
    assertEquals(ReportTextExpectations.publicationFailure(execution.get().run(), "org.aquilagraph.application.ApplicationRepositoryOperationException", "ACQUISITION", "NOT_STARTED"), streams.errorLines());
  }

  @Test
  void givenNormalizedCommandRequest_whenActualCommandRuns_thenOneFaithfulRequestAndCompleteResultReachBorrowedStreams() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static void run() {} }");
    var service = CliTestFixtures.service(CliTestFixtures.runner(), CliTestFixtures.repositoriesMustNotOpen());
    var useCase = new CapturingAnalysisUseCase(service);
    var command = new AnalyzeCommand(useCase);
    var streams = new CliStreams();

    // Act
    var code = new CommandRunner(command).run(new String[] {directory.resolve("child").resolve("..").toString(), "--release", "25"}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();
    streams.output().println("still writable");
    streams.error().println("still writable");

    // Assert
    assertEquals(0, code);
    assertEquals(1, useCase.requests().size());
    var request = useCase.requests().getFirst();
    assertEquals(directory.toAbsolutePath().normalize(), ((SourceInput.Root) request.computation().source().input()).path());
    assertEquals(CompilerConfiguration.forRelease(JavaRelease.JAVA_25), request.computation().source().configuration());
    assertEquals(AstProfile.JAVA_SOURCE_BOUNDED, request.computation().source().profile());
    assertEquals(Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), request.computation().cfgSelection());
    assertEquals(PublicationIntent.DO_NOT_SAVE, request.publicationIntent());
    assertTrue(streams.outputLines().containsAll(List.of("Completion: COMPLETE", "Java release: 25", "Repository: NOT_REQUESTED")));
    assertEquals("still writable", streams.outputLines().getLast());
    assertEquals(List.of("still writable"), streams.errorLines());
  }

  @Test
  void givenAbruptDerivationWithRealPrefix_whenActualCommandRuns_thenExactCarrierEscapesWithoutOutputOrRepositoryAcquisition() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static int first() { return 1; } static int second() { return 2; } }");
    var source = new AtomicReference<AstOutcome>();
    var carrier = new AtomicReference<CfgDerivationException>();
    var cause = new IllegalStateException("construction interrupted");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var runner = AnalysisCapabilities.computation(request -> {
      var produced = frontend.produce(request);
      source.set(produced);
      return produced;
    }, (ast, request) -> {
      var prefix = CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), request.methodKeys().subList(0, 1), request.profile()));
      var failure = new CfgDerivationException(request, prefix.methods(), cause);
      carrier.set(failure);
      throw failure;
    });
    var command = new AnalyzeCommand(CliTestFixtures.service(runner, CliTestFixtures.repositoriesMustNotOpen()));
    var streams = new CliStreams();

    // Act
    var observed = assertThrows(AnalysisDerivationException.class,
        () -> new CommandRunner(command).run(new String[] {directory.toString(), "--persist"}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode());

    // Assert
    assertSame(source.get(), observed.ast());
    assertSame(carrier.get(), observed.derivationFailure());
    assertSame(cause, observed.derivationFailure().getCause());
    assertEquals(source.get().result().orElseThrow().identity(), observed.derivationFailure().request().astIdentity());
    assertEquals(source.get().result().orElseThrow().program().methods().stream().map(method -> method.methodKey()).toList(), observed.derivationFailure().request().methodKeys());
    assertEquals(1, observed.derivationFailure().completedMethods().size());
    assertEquals(MethodCfgState.AVAILABLE, observed.derivationFailure().completedMethods().getFirst().state());
    assertTrue(observed.derivationFailure().completedMethods().getFirst().graph().isPresent());
    assertEquals(List.of(), streams.outputLines());
    assertEquals(List.of(), streams.errorLines());
  }

  @Test
  void givenUnmatchedPrimaryWithSuppressedRepositoryFailure_whenActualCommandRuns_thenPrimaryAndSuppressedEvidenceEscape() {
    // Arrange
    var primary = new IllegalStateException("unmatched primary");
    var suppressed = new RepositoryException("close failed", new IllegalStateException("close cause"));
    primary.addSuppressed(suppressed);
    var command = new AnalyzeCommand(request -> { throw primary; });
    var streams = new CliStreams();
    // Act
    var observed = assertThrows(IllegalStateException.class, () -> new CommandRunner(command).run(new String[] {"source"}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode());
    // Assert
    assertSame(primary, observed);
    assertEquals(1, observed.getSuppressed().length);
    assertSame(suppressed, observed.getSuppressed()[0]);
    assertEquals(List.of(), streams.outputLines());
    assertEquals(List.of(), streams.errorLines());
  }

  @Test
  void givenNamedInputFailure_whenCommandRuns_thenRejectedExitAndOnlyInputErrorAreRendered() {
    // Arrange
    var failure = new AnalysisInputException("source unreadable", new IOException("unreadable"));
    var command = new AnalyzeCommand(request -> { throw failure; });
    var streams = new CliStreams();

    // Act
    var code = new CommandRunner(command).run(new String[] {"source"}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();

    // Assert
    assertEquals(CommandExitPolicy.EXIT_REJECTED, code);
    assertEquals(List.of(), streams.outputLines());
    assertEquals(ReportTextExpectations.namedInput("source unreadable", "java.io.IOException", "unreadable"), streams.errorLines());
  }

  @Test
  void givenNamedEnvironmentFailure_whenCommandRuns_thenIncompleteExitAndOnlyEnvironmentErrorAreRendered() {
    // Arrange
    var failure = new AnalysisEnvironmentException("compiler unavailable");
    var command = new AnalyzeCommand(request -> { throw failure; });
    var streams = new CliStreams();

    // Act
    var code = new CommandRunner(command).run(new String[] {"source"}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();

    // Assert
    assertEquals(CommandExitPolicy.EXIT_INCOMPLETE, code);
    assertEquals(List.of(), streams.outputLines());
    assertEquals(ReportTextExpectations.namedEnvironment("compiler unavailable"), streams.errorLines());
  }

  @Test
  void givenNamedFailureAndUsageRejection_whenCommandIsReused_thenOnlyValidInvocationsReachAnalysisWithFreshIntent() {
    // Arrange
    var failure = new AnalysisInputException("source unreadable", new IOException("unreadable"));
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRun());
    var useCase = new CapturingAnalysisUseCase(request -> {
      if (request.publicationIntent() == PublicationIntent.SAVE_COMPLETED) {
        throw failure;
      }
      return execution;
    });
    var command = new AnalyzeCommand(useCase);
    var failed = new CliStreams();
    var invalid = new CliStreams();
    var completed = new CliStreams();

    // Act
    var runner = new CommandRunner(command);
    var failedCode = runner.run(new String[] {"unreadable", "--persist"}, CommandDestination.printStream(failed.output()), CommandDestination.printStream(failed.error())).exitCode();
    var invalidCode = runner.run(new String[] {"--persist", "--unknown"}, CommandDestination.printStream(invalid.output()), CommandDestination.printStream(invalid.error())).exitCode();
    var completedCode = runner.run(new String[] {"readable"}, CommandDestination.printStream(completed.output()), CommandDestination.printStream(completed.error())).exitCode();

    // Assert
    assertEquals(CommandExitPolicy.EXIT_REJECTED, failedCode);
    assertEquals(List.of(), failed.outputLines());
    assertEquals(ReportTextExpectations.namedInput("source unreadable", "java.io.IOException", "unreadable"), failed.errorLines());
    assertEquals(CommandExitPolicy.EXIT_REJECTED, invalidCode);
    assertEquals(List.of(), invalid.outputLines());
    assertEquals(List.of("Unknown option: \"--unknown\"", AnalyzeCommandParser.USAGE), invalid.errorLines());
    assertEquals(CommandExitPolicy.EXIT_COMPLETED, completedCode);
    assertTrue(completed.outputLines().contains("Completion: COMPLETE"));
    assertEquals(ReportTextExpectations.noPublication(execution.run()), completed.outputLines().subList(completed.outputLines().size() - 8, completed.outputLines().size()));
    assertEquals(List.of(), completed.errorLines());
    assertEquals(List.of(new SourceInput.Root(Path.of("unreadable")), new SourceInput.Root(Path.of("readable"))),
        useCase.requests().stream().map(request -> request.computation().source().input()).toList());
    assertEquals(List.of(PublicationIntent.SAVE_COMPLETED, PublicationIntent.DO_NOT_SAVE),
        useCase.requests().stream().map(AnalysisRequest::publicationIntent).toList());
  }

  @Test
  void givenRejectedArguments_whenCommandRuns_thenCallerCanStillWriteToBothStreams() {
    // Arrange
    var command = new AnalyzeCommand(request -> { throw new AssertionError("Usage rejection must not invoke analysis"); });
    var streams = new CliStreams();

    // Act
    var code = new CommandRunner(command).run(new String[] {"--unknown"}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(streams.error())).exitCode();
    streams.output().println("caller output");
    streams.error().println("caller diagnostic");

    // Assert
    assertEquals(CommandExitPolicy.EXIT_REJECTED, code);
    assertEquals(List.of("caller output"), streams.outputLines());
    assertEquals(List.of("Unknown option: \"--unknown\"", AnalyzeCommandParser.USAGE, "caller diagnostic"), streams.errorLines());
  }

  @Test
  void givenNamedFailureFromDiagnosticStream_whenCommandPresentsCompletedRun_thenExactFailureAndPartialReportRemain() {
    // Arrange
    var run = CliTestFixtures.completedRunWithWarning();
    var execution = CliTestFixtures.withoutRepositoryRequest(run);
    var useCase = new CapturingAnalysisUseCase(request -> execution);
    var command = new AnalyzeCommand(useCase);
    var streams = new CliStreams();
    var diagnosticBytes = new ByteArrayOutputStream();
    var lineSeparator = System.lineSeparator();
    var diagnosticPrefix = String.join(lineSeparator, "Diagnostic: \"" + CliTestFixtures.warningDiagnostic().diagnosticKey() + "\"",
        "  Occurrence: 1", "  Owner: APPLICATION", "  Native position: 0", "  Severity: WARNING", "  Stage: FRONTEND", "  Cause: OTHER_ANALYSIS_ERROR", "  Code: \"cli.warning\"")
        + lineSeparator;
    var failureOffset = diagnosticPrefix.getBytes(StandardCharsets.UTF_8).length;
    var cause = new IOException("diagnostic sink failed");
    var primary = new AnalysisEnvironmentException("presentation failed", cause);
    var cleanup = new IllegalStateException("diagnostic sink cleanup failed");
    primary.addSuppressed(cleanup);
    var diagnosticSink = new OutputStream() {
      private boolean failureRaised;

      @Override
      public void write(int value) {
        if (!failureRaised && diagnosticBytes.size() == failureOffset) {
          failureRaised = true;
          throw primary;
        }
        diagnosticBytes.write(value);
      }
    };

    try (var error = new PrintStream(diagnosticSink, true, StandardCharsets.UTF_8)) {
      // Act
      var observed = assertThrows(CommandPresentationException.class,
          () -> new CommandRunner(command).run(new String[] {"source"}, CommandDestination.printStream(streams.output()), CommandDestination.printStream(error)).exitCode());
      streams.output().println("caller output");
      error.println("caller diagnostic");

      // Assert
      assertSame(primary, observed.getCause());
      assertSame(cause, observed.getCause().getCause());
      assertEquals(List.of(cleanup), List.of(observed.getCause().getSuppressed()));
      assertEquals(CommandPresentationException.Phase.WRITE, observed.phase());
      assertEquals(Optional.of(OutputChannel.STDERR), observed.channel());
      assertSame(execution, assertInstanceOf(AnalyzeCommandOutcome.Executed.class, observed.outcome()).execution());
      assertEquals(ChannelEmission.Status.COMPLETED, observed.progress().orElseThrow().output());
      assertEquals(1, useCase.requests().size());
      var output = streams.outputLines();
      assertTrue(output.contains("Completion: COMPLETE"));
      var methodKey = run.representation().ast().result().orElseThrow().program().methods().getFirst().methodKey();
      assertTrue(output.contains("Method CFG: \"" + methodKey + "\" AVAILABLE"));
      var expectedSuffix = new ArrayList<>(ReportTextExpectations.noPublication(run));
      expectedSuffix.add("caller output");
      assertEquals(expectedSuffix, output.subList(output.size() - expectedSuffix.size(), output.size()));
      assertEquals(diagnosticPrefix + "caller diagnostic" + lineSeparator, diagnosticBytes.toString(StandardCharsets.UTF_8));
    }
  }
}
