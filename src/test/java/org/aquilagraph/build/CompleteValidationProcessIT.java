package org.aquilagraph.build;

import static org.aquilagraph.build.BuildResourceAssertions.assertClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.aquilagraph.testsupport.IsolatedBuild;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

@Tag("process")
class CompleteValidationProcessIT {
  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path directory;

  @Test
  void givenAnUnavailableSelectedDockerEngine_whenFullValidationStarts_thenTheRealDependencyFailurePreventsTestEffects() throws Exception {
    // Arrange
    var fixture = new CompleteBuildFixture(directory);
    fixture.requireDocker();
    var endpoint = IsolatedBuild.windows() ? "tcp://127.0.0.1:1"
        : "unix:///tmp/aquila-graph-unavailable-" + UUID.randomUUID() + ".sock";
    var builder = fixture.build().builder("verify-all");
    builder.environment().remove("DOCKER_CONTEXT");
    builder.environment().put("DOCKER_HOST", endpoint);

    // Act
    var result = fixture.retain(fixture.build().run(builder));

    // Assert
    assertNotEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("require-docker-engine"), result.output());
    assertTrue(result.output().contains(endpoint), result.output());
    assertFalse(Files.exists(directory.resolve("process.marker")));
    assertEquals("FAILED", BuildFiles.read(fixture.resultDirectory().resolve("result.properties")).getProperty("status"));
    assertClosed(directory);

    // Keep TCP timeout propagation distinct from complete-command startup and local absence.
    var tcp = fixture.build().run(fixture.dockerProbe("tcp://127.0.0.1:1"));
    assertNotEquals(0, tcp.exitCode(), tcp.output());
    assertTrue(tcp.output().contains("127.0.0.1:1"), tcp.output());
    assertFalse(Files.exists(directory.resolve("process.marker")));
    System.out.println("Configured Docker TCP refusal exit " + tcp.exitCode() + ": " + tcp.output().strip());
  }

  @Test
  void givenAnEffectiveGateBypass_whenFullValidationIsAdmitted_thenTheDisabledRequiredCheckIsRefused() throws Exception {
    // Arrange
    var fixture = new CompleteBuildFixture(directory);
    var pom = directory.resolve("pom.xml");
    Files.writeString(pom, Files.readString(pom).replace("<includeResources>false</includeResources>",
        "<includeResources>false</includeResources><skip>true</skip>"));

    // Act
    var result = fixture.run();

    // Assert
    assertNotEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("refuses effective gate bypass: skip=true"), result.output());
    assertFalse(Files.exists(directory.resolve("process.marker")));
    assertEquals("FAILED", BuildFiles.read(fixture.resultDirectory().resolve("result.properties")).getProperty("status"));
    assertClosed(directory);
  }

  @Test
  void givenCompleteRealWorkload_whenOneNativeOperationRuns_thenUnitsIntegrationsGatesAndUnitOnlyCoverageReconcile() throws Exception {
    // Arrange
    var fixture = new CompleteBuildFixture(directory);

    // Act
    var result = fixture.run();

    // Assert
    assertEquals(0, result.exitCode(), result.output());
    var record = BuildFiles.read(fixture.resultDirectory().resolve("result.properties"));
    assertEquals("PASSED", record.getProperty("status"));
    assertEquals("true", record.getProperty("complete.validation"));
    assertTrue(Files.isRegularFile(Path.of(record.getProperty("owner.closure.record"))));
    assertTrue(result.output().contains("check-control-source"), result.output());
    assertEquals(1, result.output().lines().filter(line -> line.contains("--- surefire:")).count(), result.output());
    assertEquals(1, result.output().lines().filter(line -> line.contains("--- failsafe:") && line.contains(":integration-test ")).count());
    var methods = BuildXml.read(directory.resolve("target/site/jacoco/jacoco.xml")).getElementsByTagName("method");
    var unitCovered = false;
    var integrationUncovered = false;
    for (var index = 0; index < methods.getLength(); index++) {
      var method = (Element) methods.item(index);
      var counters = method.getElementsByTagName("counter");
      for (var counterIndex = 0; counterIndex < counters.getLength(); counterIndex++) {
        var counter = (Element) counters.item(counterIndex);
        if ("INSTRUCTION".equals(counter.getAttribute("type"))) {
          unitCovered |= "unitValue".equals(method.getAttribute("name")) && Integer.parseInt(counter.getAttribute("covered")) > 0;
          integrationUncovered |= "integrationValue".equals(method.getAttribute("name")) && "0".equals(counter.getAttribute("covered"));
        }
      }
    }
    assertTrue(unitCovered, "The real unit-only method must be instrumented and measured");
    assertTrue(integrationUncovered, "A real integration-only method must remain uncovered in unit data");
    assertClosed(directory);
  }

  @Test
  void givenUnitIntegrationOrStaticFailure_whenCompleteValidationRuns_thenItsOwnDiagnosticAndFailedResultReachTheCaller() throws Exception {
    // Arrange
    var modes = List.of("unit", "integration", "static");

    // Act
    for (var mode : modes) {
      var fixture = new CompleteBuildFixture(Files.createDirectories(directory.resolve(mode)));
      if ("static".equals(mode)) {
        var source = fixture.build().root().resolve("src/main/java/fixture/FixtureApplication.java");
        Files.writeString(source, Files.readString(source).replace("package fixture;", "package fixture;\nimport java.util.*;"));
      } else {
        fixture.source("unit".equals(mode) ? "ExampleTest" : "ExamplePersistenceIT", "unit".equals(mode) ? "" : "@Tag(\"persistence\")", """
            @Test void givenMismatch_whenCompared_thenTheRunnerFails() {
              // Arrange
              var expected = 7;
              // Act
              var actual = 11;
              // Assert
              assertEquals(expected, actual, "Controlled required value mismatch");
            }
            """);
      }
      var result = fixture.run();

      // Assert
      assertNotEquals(0, result.exitCode(), result.output());
      assertTrue(result.output().contains("static".equals(mode) ? "AvoidStarImport" : "Controlled required value mismatch"), result.output());
      var completion = BuildFiles.read(fixture.resultDirectory().resolve("result.properties"));
      assertEquals("FAILED", completion.getProperty("status"));
      assertEquals(Boolean.toString("static".equals(mode)), completion.getProperty("resource.closure.verified"));
      assertClosed(fixture.build().root());
    }
  }

  @Test
  void givenNarrowingSkipsOrGateOverrides_whenFullModeIsRequested_thenAdmissionRefusesBeforeEffects() throws Exception {
    // Arrange
    var fixture = new CompleteBuildFixture(directory);
    var flags = List.of("-DskipTests=true", "-Dmaven.test.skip=true", "-Dtest=ExampleTest", "-Dit.test=ExampleProcessIT",
        "-Dgroups=process", "-Dbuild.integration.classes=ExampleProcessIT", "-Dcheckstyle.skip=true", "-Dmaven.test.failure.ignore=true",
        "-Dbuild.compilation.force=false");

    // Act
    for (var flag : flags) {
      var result = fixture.run(flag);

      // Assert
      assertNotEquals(0, result.exitCode(), result.output());
      assertTrue(result.output().contains("Unsupported flag for verify-all"), result.output());
      assertFalse(Files.exists(directory.resolve("process.marker")));
    }
    var builder = fixture.build().builder("verify-all");
    builder.environment().put("MAVEN_ARGS", "-DskipTests=true");
    var environment = fixture.build().run(builder);
    assertNotEquals(0, environment.exitCode(), environment.output());
    assertTrue(environment.output().contains("Unsupported flag for verify-all"), environment.output());
  }

  @Test
  void givenChangedInputsCoverageOrUnfinishedCleanup_whenTheCandidateFinishes_thenNoCompleteSuccessIsPublished() throws Exception {
    // Arrange
    var modes = List.of("source", "coverage", "cleanup");

    // Act
    for (var mode : modes) {
      var fixture = new CompleteBuildFixture(Files.createDirectories(directory.resolve(mode)));
      fixture.verificationAction(switch (mode) {
        case "source" -> "<echo file=\"${project.basedir}/src/main/java/fixture/FixtureApplication.java\" append=\"true\"> </echo>";
        case "coverage" -> "<delete file=\"${project.build.directory}/jacoco.exec\" />";
        default -> "<mkdir dir=\"${project.build.directory}/neo4j-integration/pending\" />"
            + "<echo file=\"${project.build.directory}/neo4j-integration/pending/resources.properties\">state=running</echo>";
      });
      var result = fixture.run();

      // Assert
      assertNotEquals(0, result.exitCode(), result.output());
      var diagnostic = switch (mode) {
        case "source" -> "candidate inputs changed";
        case "coverage" -> "Missing current unit coverage execution data";
        default -> "unfinished database cleanup";
      };
      assertTrue(result.output().contains(diagnostic), result.output());
      assertEquals("false", BuildFiles.read(fixture.resultDirectory().resolve("result.properties")).getProperty("complete.validation"));
      assertClosed(fixture.build().root());
    }
  }

  @Test
  void givenAStaleReportAtARequiredPath_whenItsGateWritesElsewhere_thenTheOldReportCannotCertifySuccess() throws Exception {
    // Arrange
    var fixture = new CompleteBuildFixture(directory);
    var pom = directory.resolve("pom.xml");
    Files.writeString(pom, Files.readString(pom).replace("<includeResources>false</includeResources>",
        "<includeResources>false</includeResources><outputFile>${project.build.directory}/alternate-analysis.xml</outputFile>"));
    Files.writeString(directory.resolve("target/checkstyle-result.xml"), "old report");

    // Act
    var result = fixture.run();

    // Assert
    assertNotEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("Missing required current control analysis report"), result.output());
    assertFalse(Files.exists(directory.resolve("target/checkstyle-result.xml")));
    assertEquals("FAILED", BuildFiles.read(fixture.resultDirectory().resolve("result.properties")).getProperty("status"));
    assertClosed(directory);
  }
}
