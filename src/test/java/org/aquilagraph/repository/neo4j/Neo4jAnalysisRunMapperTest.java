package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Neo4jAnalysisRunMapperTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenSharedMapper_whenDifferentRunsMappedConcurrently_thenEachProjectionIsIsolated()
      throws Exception {
    // Arrange
    var sourceRoot =
        Path.of("analysis-targets/calculator/src/main/java").toAbsolutePath().normalize();
    var firstDependency = Files.createDirectory(temporaryDirectory.resolve("first-dependency"));
    var secondDependency = Files.createDirectory(temporaryDirectory.resolve("second-dependency"));
    Files.writeString(firstDependency.resolve("marker.txt"), "first");
    Files.writeString(secondDependency.resolve("marker.txt"), "second");
    var firstRun =
        new AnalysisEngine()
            .analyze(
                new JavaFrontend(new JavaSourceLoader())
                    .analyze(
                        sourceRoot,
                        new CompilerConfiguration(
                            JavaRelease.JAVA_25,
                            List.of(firstDependency),
                            List.of(),
                            List.of())));
    var secondRun =
        new AnalysisEngine()
            .analyze(
                new JavaFrontend(new JavaSourceLoader())
                    .analyze(
                        sourceRoot,
                        new CompilerConfiguration(
                            JavaRelease.JAVA_25,
                            List.of(secondDependency),
                            List.of(),
                            List.of())));
    var mapper = new Neo4jAnalysisRunMapper();
    var runs = List.of(firstRun, secondRun);
    var expectedProjections =
        List.of(mapper.map(firstRun), mapper.map(secondRun));
    var workerCount = 8;
    var ready = new CountDownLatch(workerCount);
    var start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(workerCount)) {
      var pendingProjections = new ArrayList<Future<Neo4jGraphProjection>>();
      for (var worker = 0; worker < workerCount; worker++) {
        var run = runs.get(worker % runs.size());
        pendingProjections.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start projection");
                  }
                  return mapper.map(run);
                }));
      }
      assertTrue(ready.await(30, TimeUnit.SECONDS));

      // Act
      start.countDown();
      var actualProjections = new ArrayList<Neo4jGraphProjection>();
      for (var pendingProjection : pendingProjections) {
        actualProjections.add(pendingProjection.get(30, TimeUnit.SECONDS));
      }

      // Assert
      assertNotEquals(firstRun.runId(), secondRun.runId());
      for (var worker = 0; worker < workerCount; worker++) {
        assertEquals(
            expectedProjections.get(worker % expectedProjections.size()),
            actualProjections.get(worker),
            "Every concurrent call must produce its own complete projection");
      }
    }
  }

  @Test
  void givenCompletedCalculatorRun_whenMapped_thenIdentityOriginsAndTypedFlowArePreserved()
      throws IOException {
    // Arrange
    var sourceRoot =
        Path.of("analysis-targets/calculator/src/main/java").toAbsolutePath().normalize();
    var dependency = Files.createDirectory(temporaryDirectory.resolve("dependency"));
    var run =
        new AnalysisEngine()
            .analyze(
                new JavaFrontend(new JavaSourceLoader())
                    .analyze(
                        sourceRoot,
                        new CompilerConfiguration(
                            JavaRelease.JAVA_25, List.of(dependency), List.of(), List.of())));

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    assertTrue(projection.nodes().stream().allMatch(node -> node.properties().get("runId").equals(run.runId())));
    assertTrue(
        projection.relationships().stream()
            .allMatch(relationship -> relationship.properties().get("runId").equals(run.runId())));
    assertTrue(projection.nodes().stream().anyMatch(node -> node.label().equals("SourceOrigin")));
    assertTrue(projection.nodes().stream().anyMatch(node -> node.label().equals("FlowValue")));
    assertTrue(
        projection.nodes().stream()
            .anyMatch(node -> node.label().equals("AnalysisEnvironmentSnapshot")));
    assertTrue(
        projection.nodes().stream().anyMatch(node -> node.label().equals("DependencySnapshot")));
    assertTrue(
        projection.nodes().stream()
            .filter(node -> node.label().equals("OperationNode"))
            .anyMatch(node -> node.properties().containsKey("kind")));
    assertTrue(
        projection.nodes().stream()
            .filter(node -> node.label().equals("SemanticReference"))
            .anyMatch(node -> node.properties().get("typeAlternativeNames") instanceof List<?> types && !types.isEmpty()));
    assertTrue(
        projection.nodes().stream()
            .filter(node -> node.label().equals("SemanticReference"))
            .anyMatch(node -> node.properties().get("assignableToTypeNames") instanceof List<?> types && !types.isEmpty()));
    assertFalse(
        projection.nodes().stream().anyMatch(node -> node.label().startsWith("Normalized")));
    assertTrue(projection.relationships().stream().anyMatch(relation -> relation.type().equals("IN_SOURCE")));
    assertTrue(
        projection.relationships().stream()
            .anyMatch(relation -> relation.type().equals("HAS_ENVIRONMENT")));
    assertTrue(
        projection.relationships().stream()
            .anyMatch(relation -> relation.type().equals("HAS_CLASS_PATH_ENTRY")));
    assertFalse(
        projection.relationships().stream()
            .anyMatch(
                relation ->
                    Set.of("HAS_NORMALIZED_BODY", "HAS_OPERATION", "REPRESENTS_OPERATION")
                        .contains(relation.type())));
    var flowTypes =
        projection.relationships().stream()
            .map(Neo4jRelationship::type)
            .filter(type -> type.endsWith("_FLOW"))
            .collect(Collectors.toSet());
    assertTrue(flowTypes.containsAll(Set.of("NORMAL_FLOW", "RETURN_FLOW", "EXCEPTIONAL_FLOW")));
    assertEquals(run.runId(), projection.runId());
  }

  @Test
  void givenRejectedRun_whenMapped_thenRunIsRejected() throws IOException {
    // Arrange
    var run =
        new AnalysisEngine()
            .analyze(
                new JavaFrontend(new JavaSourceLoader())
                    .analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25)));
    var mapper = new Neo4jAnalysisRunMapper();

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> mapper.map(run));

    // Assert
    assertEquals("Only completed analysis runs may be stored", exception.getMessage());
  }
}
