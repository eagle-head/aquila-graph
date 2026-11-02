package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisDerivationException;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.SourceAnalysisRunner;
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
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class AnalyzeCommandTest {
  @TempDir Path directory;

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
          var code = command.run(tokens.toArray(String[]::new), streams.output(), streams.error());

          // Assert
          assertEquals(AnalyzeCommand.EXIT_COMPLETED, code);
          assertEquals(List.of(new AnalysisRequest(new SourceInput.Root(directory.toAbsolutePath().normalize()),
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED,
              Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), PublicationIntent.SAVE_COMPLETED)), useCase.requests());
          assertTrue(streams.outputLines().contains("Completion: COMPLETE"));
          assertEquals("Repository: SAVED", streams.outputLines().getLast());
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
        command.run(
            new String[] {directory.toString()},
            streams.output(),
            streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_INCOMPLETE, exitCode);
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
        command.run(
            new String[] {directory.toString(), "--release", "25"},
            streams.output(),
            streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_INCOMPLETE, exitCode);
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
        command.run(
            new String[] {directory.toString(), "--release", "25"},
            streams.output(),
            streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, exitCode);
    assertEquals("", streams.outputText());
    assertTrue(streams.errorText().contains("Input rejected:"));
    assertTrue(streams.errorText().contains(malformedSource.toString()));
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
        command.run(
            new String[] {directory.toString(), "--release", "25", "--persist"},
            streams.output(),
            streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_INCOMPLETE, exitCode);
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
    var code = command.run(new String[] {directory.toString()}, streams.output(), streams.error());

    // Assert
    assertEquals(1, code);
    assertEquals(AnalysisCompletion.PARTIAL, run.get().completion());
    assertEquals(List.of(), run.get().diagnostics());
    assertTrue(streams.outputLines().contains("Completion: PARTIAL"));
    assertTrue(streams.outputLines().contains("AST: INCOMPLETE"));
    assertTrue(streams.outputLines().contains("CFG: COMPLETE"));
    assertTrue(streams.errorLines().contains("Qualification: SOURCE PROFILE_RESTRICTION"));
    assertTrue(streams.errorLines().contains("Qualification: APPLICATION SOURCE"));
    var applicationReason = streams.errorLines().indexOf("Qualification: APPLICATION SOURCE");
    assertEquals(List.of("Qualification: APPLICATION SOURCE", "  Profile: \"java-source-bounded/1\"",
        "  Subject: \"" + run.get().representation().ast().result().orElseThrow().identity().astResultKey() + "\""),
        streams.errorLines().subList(applicationReason, applicationReason + 3));
    assertFalse(streams.errorLines().contains("Diagnostic:"));
    assertFalse(streams.errorLines().contains("frontend.unsupported-tree-kind.union-type"));
  }

  @Test
  void givenCompleteSourceAndRepositoryFailure_whenActualCommandRuns_thenComputationAndFailureRemainIndependent() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static void run() {} }");
    var cause = new RepositoryException("database unavailable", new IllegalStateException("offline"));
    var service = CliTestFixtures.service(CliTestFixtures.runner(), () -> { throw cause; });
    var failure = new AtomicReference<AnalysisRepositoryException>();
    var command = new AnalyzeCommand(request -> {
      try {
        return service.execute(request);
      } catch (AnalysisRepositoryException exception) {
        failure.set(exception);
        throw exception;
      }
    });
    var streams = new CliStreams();

    // Act
    var code = command.run(new String[] {directory.toString(), "--persist"}, streams.output(), streams.error());

    // Assert
    assertEquals(3, code);
    assertSame(cause, failure.get().repositoryFailure());
    assertEquals(AnalysisCompletion.COMPLETE, failure.get().analysisRun().completion());
    assertTrue(streams.outputLines().contains("Completion: COMPLETE"));
    assertEquals("Repository: FAILED", streams.outputLines().getLast());
    assertEquals(List.of("Repository save failed: database unavailable"), streams.errorLines());
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
    var code = command.run(new String[] {directory.resolve("child").resolve("..").toString(), "--release", "25"}, streams.output(), streams.error());
    streams.output().println("still writable");
    streams.error().println("still writable");

    // Assert
    assertEquals(0, code);
    assertEquals(1, useCase.requests().size());
    var request = useCase.requests().getFirst();
    assertEquals(directory.toAbsolutePath().normalize(), ((SourceInput.Root) request.input()).path());
    assertEquals(CompilerConfiguration.forRelease(JavaRelease.JAVA_25), request.configuration());
    assertEquals(AstProfile.JAVA_SOURCE_BOUNDED, request.astProfile());
    assertEquals(Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), request.cfgSelection());
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
    var command = new AnalyzeCommand(CliTestFixtures.service(runner, CliTestFixtures.repositoriesMustNotOpen()));
    var streams = new CliStreams();

    // Act
    var observed = assertThrows(AnalysisDerivationException.class,
        () -> command.run(new String[] {directory.toString(), "--persist"}, streams.output(), streams.error()));

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
    var suppressed = new AnalysisRepositoryException(CliTestFixtures.completedRun(), new RepositoryException("close failed", new IllegalStateException("close cause")));
    primary.addSuppressed(suppressed);
    var command = new AnalyzeCommand(request -> { throw primary; });
    var streams = new CliStreams();
    // Act
    var observed = assertThrows(IllegalStateException.class, () -> command.run(new String[] {"source"}, streams.output(), streams.error()));
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
    var code = command.run(new String[] {"source"}, streams.output(), streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_REJECTED, code);
    assertEquals(List.of(), streams.outputLines());
    assertEquals(List.of("Input rejected: source unreadable"), streams.errorLines());
  }

  @Test
  void givenNamedEnvironmentFailure_whenCommandRuns_thenIncompleteExitAndOnlyEnvironmentErrorAreRendered() {
    // Arrange
    var failure = new AnalysisEnvironmentException("compiler unavailable");
    var command = new AnalyzeCommand(request -> { throw failure; });
    var streams = new CliStreams();

    // Act
    var code = command.run(new String[] {"source"}, streams.output(), streams.error());

    // Assert
    assertEquals(AnalyzeCommand.EXIT_INCOMPLETE, code);
    assertEquals(List.of(), streams.outputLines());
    assertEquals(List.of("Analysis failed: compiler unavailable"), streams.errorLines());
  }
}
