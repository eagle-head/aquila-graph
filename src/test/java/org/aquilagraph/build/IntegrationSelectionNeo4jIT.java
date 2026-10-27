package org.aquilagraph.build;

import static org.aquilagraph.build.BuildResourceAssertions.assertClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

@Tag("lifecycle")
class IntegrationSelectionNeo4jIT {
  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path directory;

  @Test
  void givenUnavailableDockerProtocol_whenFocusedDatabaseRuns_thenDependencyFailureCannotBecomeASkipOrSuccess() throws Exception {
    // Arrange
    var fixture = new IntegrationBuildFixture(directory);
    fixture.source("ExamplePersistenceIT", "import org.aquilagraph.testsupport.ManagedNeo4jDatabase;",
        "@Tag(\"persistence\")", """
        @Test void givenIncompatibleDockerProtocol_whenTheRealDatabaseStarts_thenItsDependencyFailureReachesFailsafe() throws Exception {
          // Arrange
          System.setProperty("api.version", "1.99");
          // Act
          try (var database = ManagedNeo4jDatabase.start(Path.of("target/unavailable-database"), false)) {
            // Assert
            System.out.println("Unexpected database readiness: " + database.settings().database());
          }
        }
        """);

    // Act
    var result = fixture.run("check-persistence");

    // Assert
    assertNotEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("Docker environment") || result.output().contains("client version"), result.output());
    assertTrue(result.output().contains("Errors: 1"), result.output());
    assertFalse(result.output().contains("Verified integration scope:"), result.output());
    assertClosed(directory);
  }

  @Test
  void givenActualDatabaseObservationMismatch_whenFocusedVerificationRuns_thenFailurePropagatesAndResourcesClose() throws Exception {
    // Arrange
    var fixture = new IntegrationBuildFixture(directory);
    fixture.source("ExamplePersistenceIT", """
        import org.aquilagraph.testsupport.Neo4jIntegration;
        import org.junit.jupiter.api.extension.ExtendWith;
        import org.neo4j.driver.AuthTokens;
        import org.neo4j.driver.GraphDatabase;
        """, "@Tag(\"persistence\") @ExtendWith(Neo4jIntegration.class)", """
        @Test void givenActualQuery_whenObserved_thenTheRunnerPreservesTheDeliberateMismatch() {
          // Arrange
          var settings = Neo4jIntegration.settings();
          try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
              var session = driver.session()) {
            // Act
            var observed = session.run("RETURN 7 AS value").single().get("value").asInt();
            System.out.println("ACTUAL_DATABASE_OBSERVATION=" + observed);
            // Assert
            assertEquals(8, observed, "Deliberate database observation mismatch");
          }
        }
        """);

    // Act
    var result = fixture.run("check-persistence");

    // Assert
    assertNotEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("ACTUAL_DATABASE_OBSERVATION=7"), result.output());
    assertTrue(result.output().contains("Deliberate database observation mismatch"), result.output());
    assertTrue(result.output().contains("failsafe:3.6.0:verify"), result.output());
    assertFalse(result.output().contains("Verified integration scope:"), result.output());
    try (var resources = Files.walk(directory.resolve("target/neo4j-integration"))) {
      var records = resources.filter(path -> path.getFileName().toString().equals("resources.properties")).toList();
      assertFalse(records.isEmpty(), "The real managed database must record its resource identities");
      for (var file : records) {
        var data = new Properties();
        try (var input = Files.newInputStream(file)) {
          data.load(input);
        }
        assertEquals("closed", data.getProperty("state"), data.toString());
      }
    }
    assertClosed(directory);
  }
}
