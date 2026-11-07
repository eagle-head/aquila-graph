package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.ApplicationRepositoryOperationException;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cli.AdapterFailureDetail;
import org.aquilagraph.cli.AnalyzeCommand;
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
    final int exit;

    // Act
    try (var stdout = new PrintStream(output, true, StandardCharsets.UTF_8); var stderr = new PrintStream(error, true, StandardCharsets.UTF_8)) {
      exit = command.run(new String[] {directory.toString(), "--persist"}, stdout, stderr);
    }
    var outcome = command.execute(new String[] {directory.toString(), "--persist"});

    // Assert
    assertEquals(3, exit);
    var observed = assertInstanceOf(AnalyzeCommandOutcome.Executed.class, outcome);
    assertSame(returned.get(), observed.execution());
    assertEquals(Optional.of(new AdapterFailureDetail("Neo4j", "REPLACEMENT")), details.project(carrier.get()));
    assertSame(primary, observed.execution().publication().failure().orElseThrow().effectivePrimary());
    assertTrue(error.toString(StandardCharsets.UTF_8).contains("  Adapter: \"Neo4j\"" + System.lineSeparator()
        + "  Adapter phase: \"REPLACEMENT\""));
    assertTrue(output.toString(StandardCharsets.UTF_8).endsWith("Commit knowledge: UNKNOWN" + System.lineSeparator()));
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
}
