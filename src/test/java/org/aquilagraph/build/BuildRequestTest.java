package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildRequestTest {
  @TempDir
  Path directory;

  @Test
  void givenCompileRequest_whenArgumentsAreBuilt_thenTheCatalogOwnsSkipAndGoal() throws Exception {
    // Arrange
    var arguments = new String[]{"--root", Path.of("").toRealPath().toString(), "compile", "-o"};

    // Act
    var request = BuildRequest.parse(arguments);

    // Assert
    assertEquals(List.of("-Dmaven.repo.local=" + request.root().resolve("target/maven-repository"), "-o", "-Dmaven.test.skip=true", "compile"),
        request.mavenArguments());
    assertEquals(List.of("compile"), request.operation().goals());
  }

  @Test
  void givenAdditionalGoalOrOverride_whenFlagsAreAdmitted_thenEachUnsupportedInputIsRefused() {
    // Arrange
    var unsupported = List.of("clean", "exec:exec", "-fother.xml", "-Dmaven.test.skip=false", "-Dexec.skip=true", "-Punknown", "-Dit.test=SomeIT");

    // Act
    var failures = new ArrayList<IllegalArgumentException>();
    for (var flag : unsupported) {
      failures.add(assertThrows(IllegalArgumentException.class, () -> BuildRequest.validateFlag(BuildOperation.COMPILE, flag)));
    }

    // Assert
    assertEquals(unsupported.size(), failures.size());
    failures.forEach(failure -> assertTrue(failure.getMessage().contains("Unsupported flag for compile")));
  }

  @Test
  void givenUnitSelector_whenTheOperationChanges_thenCoverageAndIntegrationCannotBeNarrowed() {
    // Arrange
    var selector = "-Dtest=SelectedTest#givenInput_whenRunning_thenValue";

    // Act
    BuildRequest.validateFlag(BuildOperation.TEST, selector);
    var coverage = assertThrows(IllegalArgumentException.class, () -> BuildRequest.validateFlag(BuildOperation.COVERAGE, selector));
    var integration = assertThrows(IllegalArgumentException.class, () -> BuildRequest.validateFlag(BuildOperation.INTEGRATION_TEST_NEO4J, selector));

    // Assert
    assertTrue(coverage.getMessage().contains("coverage"));
    assertTrue(integration.getMessage().contains("integration-test-neo4j"));
  }

  @Test
  void givenQuotedAdapterFlags_whenWordsAreParsed_thenLiteralValuesSurviveWithoutShellEvaluation() {
    // Arrange
    var text = "-B '-Dtest=Name & other' \"a\\\"b\" '$HOME;$(echo value)'";

    // Act
    var actual = ArgumentWords.parse(text);

    // Assert
    assertEquals(List.of("-B", "-Dtest=Name & other", "a\"b", "$HOME;$(echo value)"), actual);
    assertEquals(List.of("single\\"), ArgumentWords.parse("'single\\'"));
    assertThrows(IllegalArgumentException.class, () -> ArgumentWords.parse("'unfinished"));
  }

  @Test
  void givenAmbientGoalOrJvmProperty_whenEnvironmentIsValidated_thenUnmodeledInfluenceIsVisible() {
    // Arrange
    var request = new BuildRequest(directory, directory, Path.of("mvnw"), BuildOperation.COMPILE, List.of());

    // Act
    var goal = assertThrows(IllegalArgumentException.class, () -> request.validateEnvironment(Map.of("MAVEN_ARGS", "clean")));
    var property = assertThrows(IllegalArgumentException.class, () -> request.validateEnvironment(Map.of("MAVEN_OPTS", "-Dexec.skip=true")));
    var debug = assertThrows(IllegalArgumentException.class, () -> request.validateEnvironment(Map.of("MAVEN_DEBUG_OPTS", "-Dexec.skip=true")));

    // Assert
    assertTrue(goal.getMessage().contains("MAVEN_ARGS"));
    assertTrue(property.getMessage().contains("Unsupported Maven JVM option"));
    assertFalse(property.getMessage().contains("exec.skip"));
    assertTrue(debug.getMessage().contains("MAVEN_DEBUG_OPTS"));
    assertFalse(debug.getMessage().contains("exec.skip"));
  }

  @Test
  void givenProjectMavenGoals_whenEnvironmentIsValidated_thenTheConfigurationCannotAddAnOperation() throws Exception {
    // Arrange
    Files.createDirectories(directory.resolve(".mvn"));
    Files.writeString(directory.resolve(".mvn/maven.config"), "clean\n");
    var request = new BuildRequest(directory, directory, Path.of("mvnw"), BuildOperation.COMPILE, List.of());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> request.validateEnvironment(Map.of()));

    // Assert
    assertTrue(failure.getMessage().contains("maven.config"));
  }
}
