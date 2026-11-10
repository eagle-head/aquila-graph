package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import org.aquilagraph.retention.consumer.PublicAstObservation;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.aquilagraph.testsupport.OwnedProcess;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;

@Tag("persistence")
@ExtendWith(Neo4jIntegration.class)
class PreparedConsumerNeo4jIT {
  @TempDir Path directory;

  @Test
  void givenPreparedModuleAndRetainedSource_whenFreshRestrictedQueriesRun_thenRealValuesAndIdentityRefusalsAreObserved() throws Exception {
    // Arrange
    var root = Path.of("").toRealPath();
    var receipt = Path.of(System.getProperty("aquila.consumer.receipt"));
    var invocation = System.getProperty("aquila.consumer.invocation");
    var consumer = PreparedConsumer.open(root, receipt, invocation);
    var settings = Neo4jIntegration.settings();
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Subject.java"),
        "class Subject { int value(int input) { if (input > 0) return input + 7; return -1; } }")));
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.toString());
    assertTrue(outcome.qualification().reasons().isEmpty());
    var original = outcome.result().orElseThrow();
    var service = AnalysisCapabilities.astRetention(Neo4jRepositories.astRetention(() -> settings));
    assertEquals(PublicationStatus.SAVED, service.retainAst(new AstPublicationRequest(outcome,
        PublicationIntent.SAVE_COMPLETE_AST)).status());
    try {
      // Act
      var first = query(consumer, settings, original.identity(), "first");
      var second = query(consumer, settings, original.identity(), "second");
      var identity = original.identity();
      var absent = AstIdentity.fromContent(identity.softwareSignature(), identity.sourceModelVersion(), identity.astProfileVersion(),
          identity.semanticProductionRevision(), "0".repeat(64));
      var missing = query(consumer, settings, absent, "missing");
      var wrongDatabase = new Neo4jConnectionSettings(settings.uri(), settings.username(), settings.password(), "absent-consumer-database");
      var mismatched = query(consumer, wrongDatabase, identity, "mismatched");

      // Assert
      for (var observed : List.of(first, second)) {
        assertEquals(0, observed.exit(), observed.log());
        var actual = Files.readString(observed.output());
        assertEquals(PublicAstObservation.describe(original), actual);
        assertTrue(actual.contains(snapshot.sourceFiles().getFirst().content()));
        assertTrue(actual.contains("coverage-result"));
        assertTrue(actual.contains("org.aquilagraph.source.api.SourceQualification"));
        assertTrue(actual.contains("5:graph"));
        assertTrue(observed.log().contains("Closed independent query: " + identity.astResultKey()), observed.log());
      }
      assertNotEquals(first.pid(), second.pid());
      assertNotEquals(0, missing.exit(), missing.log());
      assertTrue(missing.log().contains("Expected retained source:"), missing.log());
      assertFalse(Files.exists(missing.output()));
      assertNotEquals(0, mismatched.exit(), mismatched.log());
      assertFalse(Files.exists(mismatched.output()));
      assertThrows(IOException.class, () -> PreparedConsumer.open(root, directory.resolve("missing.properties"), invocation));
      assertThrows(IOException.class, () -> PreparedConsumer.open(root, receipt, UUID.randomUUID().toString()));
    } finally {
      try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
        Neo4jIntegration.discardAstFixture(session, original.identity().astResultKey());
      }
    }
  }

  private Query query(PreparedConsumer consumer, Neo4jConnectionSettings settings, AstIdentity identity, String name) throws Exception {
    var output = directory.resolve(name + ".ast");
    var log = directory.resolve(name + ".log");
    var executable = Path.of(System.getProperty("java.home"), "bin", BuildRequest.windows() ? "java.exe" : "java");
    var arguments = new ArrayList<>(List.of(executable.toString(), "-Xms64m", "-Xmx1g", "--limit-modules",
        "java.base,java.logging,java.naming,java.management,jdk.unsupported,org.aquilagraph.retention.consumer,org.neo4j.bolt.connection.netty",
        "-Djava.io.tmpdir=" + directory, "--module-path", consumer.modulePath(), "--module",
        "org.aquilagraph.retention.consumer/org.aquilagraph.retention.consumer.RetainedAstQueryProcess", identity.softwareSignature(),
        identity.sourceModelVersion(), identity.astProfileVersion(), identity.semanticProductionRevision(), identity.canonicalContentDigest(),
        output.toString()));
    var builder = new ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(log.toFile());
    builder.environment().clear();
    for (var key : List.of("SystemRoot", "WINDIR", "TEMP", "TMP")) {
      var value = System.getenv(key);
      if (value != null) builder.environment().put(key, value);
    }
    builder.environment().put("AQUILA_GRAPH_NEO4J_URI", settings.uri().toString());
    builder.environment().put("AQUILA_GRAPH_NEO4J_USERNAME", settings.username());
    builder.environment().put("AQUILA_GRAPH_NEO4J_PASSWORD", settings.password());
    builder.environment().put("AQUILA_GRAPH_NEO4J_DATABASE", settings.database());
    try (var process = OwnedProcess.start(builder)) {
      assertTrue(process.await(Duration.ofSeconds(60)), () -> "Query timed out: " + process.handle().pid());
      var diagnostics = Files.readString(log);
      System.out.println("Prepared query process " + process.handle().pid() + ": " + name + "; exit " + process.exitValue());
      System.out.print(diagnostics);
      return new Query(process.handle().pid(), process.exitValue(), diagnostics, output);
    }
  }

  private record Query(long pid, int exit, String log, Path output) {}
}
