package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.junit.jupiter.api.Test;

class CommandOutcomeIdentityTest {
  @Test
  void givenReturnedNamedFailureCarriers_whenNativeSerializationRestoresThem_thenOriginalOperationalRelationshipsCannotBeFabricated()
      throws IOException {
    // Arrange
    var input = new AnalysisInputException("input", new IOException("cause"));
    var environment = new AnalysisEnvironmentException("environment");
    var carriers = List.of(new AnalyzeCommandOutcome.InputRejected(input), new AnalyzeCommandOutcome.EnvironmentFailed(environment));
    for (var carrier : carriers) {
      var bytes = new ByteArrayOutputStream();
      try (var output = new ObjectOutputStream(bytes)) { output.writeObject(carrier); }

      // Act
      try (var restored = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        var failure = assertThrows(InvalidObjectException.class, restored::readObject);

        // Assert
        assertEquals("Command failure requires an original named analysis failure", failure.getMessage());
      }
    }
    assertSame(input, carriers.getFirst().getCause());
    assertSame(environment, carriers.getLast().getCause());
  }

  @Test
  void givenOriginalOperationalValues_whenOutcomesAreCreated_thenEveryOriginalIsRetainedByIdentity() {
    // Arrange
    var execution = CliTestFixtures.withoutRepositoryRequest(CliTestFixtures.completedRun());
    var cause = new IOException("input cause");
    var input = new AnalysisInputException("source description", cause);
    var environment = new AnalysisEnvironmentException("environment description", cause);
    var rejection = new ArgumentRejection.UnsupportedRelease("+26", 26);

    // Act
    var executed = new AnalyzeCommandOutcome.Executed(execution);
    var rejected = new AnalyzeCommandOutcome.InputRejected(input);
    var failed = new AnalyzeCommandOutcome.EnvironmentFailed(environment);
    var usage = new AnalyzeCommandOutcome.UsageRejected(rejection);

    // Assert
    assertSame(execution, executed.execution());
    assertSame(input, rejected.failure());
    assertSame(environment, failed.failure());
    assertSame(rejection, usage.reason());
    assertEquals("+26", rejection.value());
    assertEquals(26, rejection.feature());
  }

  @Test
  void givenAbsentOperationalValues_whenOutcomesAreCreated_thenCallerContractViolationsRemainAbrupt() {
    // Arrange
    // Act
    var execution = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.Executed(null));
    var input = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.InputRejected(null));
    var environment = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.EnvironmentFailed(null));
    var usage = assertThrows(NullPointerException.class, () -> new AnalyzeCommandOutcome.UsageRejected(null));

    // Assert
    assertEquals("execution", execution.getMessage());
    assertEquals("failure", input.getMessage());
    assertEquals("failure", environment.getMessage());
    assertEquals("reason", usage.getMessage());
  }
}
