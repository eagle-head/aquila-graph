package org.aquilagraph.build;

import static org.aquilagraph.build.BuildResourceAssertions.assertClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

@Tag("process")
class IntegrationSelectionProcessIT {
  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path directory;

  @Test
  void givenClassifiedFactories_whenFocusedGroupsRun_thenOnlyRequestedEffectsAndVerifiedDynamicChildrenAreObserved() throws Exception {
    // Arrange
    var fixture = new IntegrationBuildFixture(directory);

    // Act
    var process = fixture.run("check-process");
    assertEquals(0, process.exitCode(), process.output());
    assertFalse(Files.exists(directory.resolve("persistence.marker")));
    assertFalse(Files.exists(directory.resolve("lifecycle.marker")));
    var persistence = fixture.run("check-persistence");
    assertEquals(0, persistence.exitCode(), persistence.output());
    assertFalse(Files.exists(directory.resolve("lifecycle.marker")));
    var lifecycle = fixture.run("check-lifecycle");

    // Assert
    assertEquals(0, process.exitCode(), process.output());
    assertEquals(0, persistence.exitCode(), persistence.output());
    assertEquals(0, lifecycle.exitCode(), lifecycle.output());
    assertEquals("observed process", Files.readString(directory.resolve("process.marker")));
    assertEquals("first", Files.readString(directory.resolve("first.marker")));
    assertEquals("second", Files.readString(directory.resolve("second.marker")));
    assertEquals("persistence", Files.readString(directory.resolve("persistence.marker")));
    assertEquals("lifecycle", Files.readString(directory.resolve("lifecycle.marker")));
    assertFalse(Files.exists(directory.resolve("first-unit.marker")));
    assertTrue(process.output().contains("Verified integration scope: process; complete validation: incomplete"), process.output());
    assertClosed(directory);
  }

  @Test
  void givenUnclassifiedConflictingOrUnknownTags_whenTheRealCatalogRuns_thenTestsAreRefusedBeforeTheirEffects() throws Exception {
    // Arrange
    var variants = List.of("", "@Tag(\"process\") @Tag(\"persistence\")", "@Tag(\"proces\")");

    // Act
    for (var index = 0; index < variants.size(); index++) {
      var fixture = new IntegrationBuildFixture(directory.resolve("classification-" + index));
      fixture.source("UnownedIT", variants.get(index), IntegrationBuildFixture.marker("unowned"));
      var result = fixture.run("check-process");

      // Assert
      assertNotEquals(0, result.exitCode(), result.output());
      assertTrue(result.output().contains("exactly one known functional tag"), result.output());
      assertFalse(Files.exists(fixture.build().root().resolve("unowned.marker")));
      assertFalse(Files.exists(fixture.build().root().resolve("process.marker")));
      assertClosed(fixture.build().root());
    }
  }

  @Test
  void givenEmptyMisspelledOrOutOfGroupSelector_whenFocusedExecutionIsRequested_thenEachRequestFails() throws Exception {
    // Arrange
    var fixture = new IntegrationBuildFixture(directory);
    var selectors = List.of("", "MissingIT", "ExamplePersistenceIT");

    // Act
    for (var selector : selectors) {
      var result = fixture.run("check-process", "-Dbuild.integration.classes=" + selector);

      // Assert
      assertNotEquals(0, result.exitCode(), result.output());
      assertTrue(result.output().contains(selector.isEmpty() ? "Unsupported flag" : "class selector"), result.output());
      assertFalse(Files.exists(directory.resolve("process.marker")));
      if (selector.isEmpty()) {
        assertFalse(Files.exists(directory.resolve(".build-state/invocations")));
      } else {
        assertClosed(directory);
      }
    }
  }

  @Test
  void givenNoMembersInRequestedGroup_whenFocusedExecutionRuns_thenAnEmptyRealDiscoveryFails() throws Exception {
    // Arrange
    var fixture = new IntegrationBuildFixture(directory);
    Files.delete(directory.resolve("src/test/java/fixture/ExampleLifecycleIT.java"));

    // Act
    var result = fixture.run("check-lifecycle");

    // Assert
    assertNotEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("Requested integration selection is empty"), result.output());
    assertFalse(Files.exists(directory.resolve("lifecycle.marker")));
    assertClosed(directory);
  }

  @Test
  void givenFailingSelectedTest_whenFailsafeVerificationRuns_thenFailureReachesTheNativeCaller() throws Exception {
    // Arrange
    var fixture = new IntegrationBuildFixture(directory);
    fixture.source("ExampleProcessIT", "@Tag(\"process\")", """
        @Test void givenDeliberateMismatch_whenObserved_thenTheRunnerReportsFailure() {
          // Arrange
          var expected = "required";
          // Act
          var observed = "different";
          // Assert
          assertEquals(expected, observed, "Deliberate process observation mismatch");
        }
        """);

    // Act
    var result = fixture.run("check-process");

    // Assert
    assertNotEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("Deliberate process observation mismatch"), result.output());
    assertTrue(result.output().contains("failsafe:3.6.0:verify"), result.output());
    assertFalse(result.output().contains("Verified integration scope:"), result.output());
    assertClosed(directory);
  }

  @Test
  void givenIntegrationGroupProperty_whenUnitsRun_thenBothIndependentlyTaggedUnitsStillExecute() throws Exception {
    // Arrange
    var fixture = new IntegrationBuildFixture(directory);

    // Act
    var result = fixture.run("test", "-Dbuild.integration.groups=persistence");

    // Assert
    assertEquals(0, result.exitCode(), result.output());
    assertEquals("first", Files.readString(directory.resolve("first-unit.marker")));
    assertEquals("second", Files.readString(directory.resolve("second-unit.marker")));
    assertFalse(Files.exists(directory.resolve("persistence.marker")));
    assertTrue(result.output().contains("Tests run: 2, Failures: 0, Errors: 0, Skipped: 0"), result.output());
    assertClosed(directory);
  }
}
