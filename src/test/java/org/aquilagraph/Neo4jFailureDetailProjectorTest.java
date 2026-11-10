package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisPublicationException;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.ApplicationRepositoryOperationException;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cli.AdapterFailureDetail;
import org.aquilagraph.cli.AnalyzeCommand;
import org.aquilagraph.cli.CommandDestination;
import org.aquilagraph.cli.CommandRunner;
import org.aquilagraph.cli.CommandInvocation;
import org.aquilagraph.cli.ChannelEmission;
import org.aquilagraph.cli.AnalyzeCommandOutcome;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Neo4jFailureDetailProjectorTest {
  @TempDir Path directory;

  @Test
  void givenRealApplicationAndKnownAdapterCarrier_whenBootstrapConfiguredCommandRuns_thenPublicOutcomeAndAdapterPhaseAreUsableAcrossPackages()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static void run() {} }");
    var primary = new RepositoryException("replacement observation", new IOException("driver boundary"));
    var carrier = new AtomicReference<Neo4jRepositoryOperationException>();
    var returned = new AtomicReference<AnalysisExecution>();
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var execution = AnalysisCapabilities.execution(computation, context -> { throw new AssertionError("Unexpected AST repository"); },
        context -> new AnalysisRunRepository() {
          @Override public void save(AnalysisRun run) {
            var failure = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.UNKNOWN, primary);
            carrier.set(failure);
            throw failure;
          }
          @Override public void close() { }
        });
    var details = new Neo4jFailureDetailProjector();
    var command = new AnalyzeCommand(request -> {
      var result = execution.execute(request);
      returned.set(result);
      return result;
    }, JavaRelease.JAVA_25, details);
    var output = new ByteArrayOutputStream();
    var error = new ByteArrayOutputStream();
    final CommandInvocation invocation;

    // Act
    try (var stdout = new PrintStream(output, true, StandardCharsets.UTF_8); var stderr = new PrintStream(error, true, StandardCharsets.UTF_8)) {
      invocation = new CommandRunner(command).run(new String[] {directory.toString(), "--persist"}, CommandDestination.printStream(stdout), CommandDestination.printStream(stderr));
    }
    var outcome = invocation.outcome();

    // Assert
    assertEquals(3, invocation.exitCode());
    assertEquals(ChannelEmission.Status.COMPLETED, invocation.emission().output());
    assertEquals(ChannelEmission.Status.COMPLETED, invocation.emission().error());
    var observed = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, outcome);
    assertSame(returned.get(), observed.execution());
    assertEquals(Optional.of(new AdapterFailureDetail("Neo4j", "REPLACEMENT")), details.project(carrier.get()));
    assertSame(primary, observed.execution().publication().failure().orElseThrow().effectivePrimary());
    assertTrue(error.toString(StandardCharsets.UTF_8).contains("  Adapter: \"Neo4j\"" + System.lineSeparator()
        + "  Adapter phase: \"REPLACEMENT\""));
    assertTrue(output.toString(StandardCharsets.UTF_8).endsWith("Commit knowledge: UNKNOWN" + System.lineSeparator()));
  }

  @Test
  void givenUnmatchedSaveAndFailedCleanup_whenBootstrapConfiguredRunnerExecutes_thenItEmitsOrderedEvidenceAndRethrowsOriginal()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static void run() {} }");
    var primary = new IllegalStateException("Unmatched \"write\"\nobservation");
    var adapterCleanup = new LinkageError("Earlier adapter cleanup");
    var cleanup = new IllegalStateException("Repository close observation");
    var carrier = new AtomicReference<Neo4jRepositoryOperationException>();
    var suppliedRun = new AtomicReference<AnalysisRun>();
    var published = new AtomicReference<AnalysisPublicationException>();
    var operations = new ArrayList<String>();
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var execution = AnalysisCapabilities.execution(computation, context -> { throw new AssertionError("Unexpected AST repository"); },
        context -> {
          operations.add("acquire");
          return new AnalysisRunRepository() {
            @Override public void save(AnalysisRun run) {
              operations.add("save");
              suppliedRun.set(run);
              var failure = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.UNKNOWN, primary);
              failure.addSuppressed(adapterCleanup);
              carrier.set(failure);
              throw failure;
            }

            @Override public void close() {
              operations.add("close");
              throw cleanup;
            }
          };
        });
    var command = new AnalyzeCommand(request -> {
      operations.add("execute");
      try {
        return execution.execute(request);
      } catch (AnalysisPublicationException failure) {
        published.set(failure);
        throw failure;
      }
    }, JavaRelease.JAVA_25, new Neo4jFailureDetailProjector());
    var runner = new CommandRunner(command);
    var output = new ObservedWriter();
    var error = new ObservedWriter();

    // Act
    var observed = assertThrows(AnalysisPublicationException.class,
        () -> runner.run(new String[] {directory.toString(), "--persist"}, CommandDestination.throwingWriter(output),
            CommandDestination.throwingWriter(error)));

    // Assert
    assertSame(published.get(), observed);
    assertSame(primary, observed.getCause());
    assertSame(carrier.get(), observed.failure());
    var run = assertInstanceOf(Coordinated.class, observed.subject()).run();
    assertSame(suppliedRun.get(), run);
    assertEquals(RepositoryOperationContext.saveCompletedRun(run.identity().key()), observed.context());
    var retainedCleanup = List.of(observed.failure().getSuppressed());
    assertEquals(2, retainedCleanup.size());
    assertSame(adapterCleanup, retainedCleanup.getFirst());
    var closure = assertInstanceOf(ApplicationRepositoryOperationException.class, retainedCleanup.getLast());
    assertSame(cleanup, closure.effectivePrimary());
    assertEquals(List.of("execute", "acquire", "save", "close"), operations);
    assertEquals(List.of(), output.operations);
    assertEquals(List.of("write", "flush"), error.operations);
    var diagnostic = error.text.toString();
    var separator = System.lineSeparator();
    assertTrue(diagnostic.startsWith("Publication aborted: "), diagnostic);
    assertTrue(diagnostic.contains(String.join(separator, "Failure reference: 1", "Failure: 1",
        "  Type: \"" + AnalysisPublicationException.class.getName() + "\"", "  Message: \"Analysis publication failed\"",
        "  Cause: 2", "  Carrier: 3", "  Effective primary: 2")), diagnostic);
    assertTrue(diagnostic.contains(String.join(separator, "Failure: 2", "  Type: \"" + IllegalStateException.class.getName() + "\"",
        "  Message: \"Unmatched \\\"write\\\"\\nobservation\"")), diagnostic);
    assertTrue(diagnostic.contains(String.join(separator, "Failure: 3",
        "  Type: \"" + Neo4jRepositoryOperationException.class.getName() + "\"", "  Message: \"Repository operation failed\"",
        "  Cause: 2", "  Carrier: not-applicable", "  Effective primary: 2", "  Suppressed: 4", "  Suppressed: 5",
        "  Operation: SAVE_COMPLETED_RUN", "  Operation subject: COMPLETED_RUN \"" + run.identity().key() + "\"",
        "  Commit knowledge: UNKNOWN", "  Application boundary: not-applicable", "  Adapter: \"Neo4j\"",
        "  Adapter phase: \"REPLACEMENT\"", "Failure: 4", "  Type: \"" + LinkageError.class.getName() + "\"",
        "  Message: \"Earlier adapter cleanup\"")), diagnostic);
    assertTrue(diagnostic.contains(String.join(separator, "Failure: 5",
        "  Type: \"" + ApplicationRepositoryOperationException.class.getName() + "\"", "  Message: \"Repository operation failed\"",
        "  Cause: 6", "  Carrier: not-applicable", "  Effective primary: 6", "  Suppressed: none", "  Operation: SAVE_COMPLETED_RUN",
        "  Operation subject: COMPLETED_RUN \"" + run.identity().key() + "\"", "  Commit knowledge: UNKNOWN",
        "  Application boundary: CLOSURE")), diagnostic);
    assertTrue(diagnostic.contains("  Message: \"Repository close observation\"" + separator), diagnostic);
  }

  @Test
  void givenSameFatalFailureInSaveAndClose_whenBootstrapConfiguredRunnerExecutes_thenItPropagatesWithoutPresentation()
      throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static void run() {} }");
    var fatal = new LinkageError("Fatal repository operation");
    var operations = new ArrayList<String>();
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var execution = AnalysisCapabilities.execution(computation, context -> { throw new AssertionError("Unexpected AST repository"); },
        context -> new AnalysisRunRepository() {
          @Override public void save(AnalysisRun run) {
            operations.add("save");
            throw fatal;
          }

          @Override public void close() {
            operations.add("close");
            throw fatal;
          }
        });
    var runner = new CommandRunner(new AnalyzeCommand(execution, JavaRelease.JAVA_25, new Neo4jFailureDetailProjector()));
    var output = new ObservedWriter();
    var error = new ObservedWriter();

    // Act
    var observed = assertThrows(LinkageError.class,
        () -> runner.run(new String[] {directory.toString(), "--persist"}, CommandDestination.throwingWriter(output),
            CommandDestination.throwingWriter(error)));

    // Assert
    assertSame(fatal, observed);
    assertEquals(0, observed.getSuppressed().length);
    assertEquals(List.of("save", "close"), operations);
    assertEquals(List.of(), output.operations);
    assertEquals(List.of(), error.operations);
  }

  @Test
  void givenApplicationOwnedAcquisitionFailure_whenDetailIsProjected_thenUnknownAdapterRetainsExplicitAbsence() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Example.java"), "interface Example { static void run() {} }");
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var execution = AnalysisCapabilities.execution(computation, context -> { throw new AssertionError("Unexpected AST repository"); },
        context -> { throw new RepositoryException("acquisition", new IOException("port")); });
    var command = new AnalyzeCommand(execution);

    // Act
    var outcome = assertInstanceOf(AnalyzeCommandOutcome.Executed.class,
        command.execute(new String[] {directory.toString(), "--persist"}));
    var carrier = assertInstanceOf(ApplicationRepositoryOperationException.class,
        outcome.execution().publication().failure().orElseThrow().carrier());

    // Assert
    assertEquals(Optional.empty(), new Neo4jFailureDetailProjector().project(carrier));
    assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), carrier.knowledge());
  }

  private static final class ObservedWriter extends Writer {
    private final List<String> operations = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();

    @Override public void write(char[] characters, int offset, int length) {
      operations.add("write");
      text.append(characters, offset, length);
    }

    @Override public void flush() {
      operations.add("flush");
    }

    @Override public void close() {
      operations.add("close");
    }
  }
}
