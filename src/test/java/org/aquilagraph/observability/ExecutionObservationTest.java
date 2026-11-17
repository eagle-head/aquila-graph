package org.aquilagraph.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CancellationException;
import org.aquilagraph.frontend.AnalysisObserver;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionObservationTest {
  @TempDir Path directory;

  @Test
  void givenAstOutcomes_whenExecutionsComplete_thenStatesRetainTheirQualifications() {
    // Arrange
    var reader = InMemoryMetricReader.create();
    var outcomes = Map.of("COMPLETE", 1.0, "INCOMPLETE", 2.0, "UNAVAILABLE", 8.0);
    var expected = new HashMap<String, Double>();
    try (var observer = observer(reader)) {
      // Act
      for (var outcome : outcomes.entrySet()) {
        try (var root = observer.start("production", Map.of())) {
          expected.put(root.executionId(), outcome.getValue());
          root.complete(Map.of("aquila.completion", outcome.getKey()));
        }
      }

      // Assert
      assertEquals(3, points(reader, "state").size());
      for (var point : points(reader, "state")) {
        assertEquals(expected.get(id(point)), point.getValue());
      }
    }
  }

  @Test
  void givenReusedSession_whenNewExecutionStarts_thenMetricsKeepIndependentStatesAndInputCounts() {
    // Arrange
    var reader = InMemoryMetricReader.create();
    try (var observer = observer(reader)) {
      String first;
      try (var root = observer.start("production", Map.of("aquila.source.files", "7"))) {
        first = root.executionId();
        root.complete(Map.of("aquila.completion", "COMPLETE", "aquila.source.nodes", "17"));
      }
      var previousElapsed = point(reader, "elapsed", first).getValue();

      // Act
      try (var root = observer.start("production", Map.of("aquila.source.files", "87"));
          var stage = observer.start("coverage-reconciliation", Map.of())) {
        var second = root.executionId();

        // Assert
        assertNotEquals(first, second);
        assertEquals(1, point(reader, "state", first).getValue());
        assertEquals(0, point(reader, "state", second).getValue());
        assertEquals(7, point(reader, "source.files", first).getValue());
        assertEquals(87, point(reader, "source.files", second).getValue());
        assertEquals(17, point(reader, "source.nodes", first).getValue());
        assertFalse(points(reader, "source.nodes").stream().anyMatch(value -> second.equals(id(value))));
        assertEquals(previousElapsed, point(reader, "elapsed", first).getValue());
        assertTrue(points(reader, "stage.elapsed").stream().anyMatch(value -> second.equals(id(value))
            && "coverage-reconciliation".equals(value.getAttributes().get(AttributeKey.stringKey("aquila.stage"))) && value.getValue() >= 0));
        assertTrue(point(reader, "heartbeat", second).getValue() >= point(reader, "started", second).getValue());
        assertFalse(points(reader, "jvm.memory.used").isEmpty());
        stage.event("analysis.failure", Map.of("operation.state", "cancelled"));
        root.event("analysis.failure", Map.of("operation.state", "cancelled"));
      }
      assertEquals(List.of(1.0, 4.0), points(reader, "state").stream().map(DoublePointData::getValue).sorted().toList());
    }
  }

  @Test
  void givenManyCompletedExecutions_whenCollecting_thenRetainedSnapshotsStayBounded() {
    // Arrange
    var reader = InMemoryMetricReader.create();
    try (var observer = observer(reader)) {
      // Act
      for (var index = 0; index < 40; index++) {
        try (var root = observer.start("production", Map.of())) {
          root.complete(Map.of("aquila.completion", "COMPLETE"));
        }
      }

      // Assert
      assertEquals(32, points(reader, "state").size());
      assertTrue(points(reader, "state").stream().allMatch(value -> value.getValue() == 1));
    }
  }

  @Test
  void givenInterruptedReconciliation_whenProducingAst_thenCancellationClosesExecutionAndPreservesInterrupt() {
    // Arrange
    var reader = InMemoryMetricReader.create();
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "class Example { int increment(int value) { return value + 1; } }")));
    var request = new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    try (var observer = observer(reader)) {
      AnalysisObserver interrupted = (stage, attributes) -> {
        var scope = observer.start(stage, attributes);
        if ("coverage-reconciliation".equals(stage)) {
          Thread.currentThread().interrupt();
        }
        return scope;
      };

      // Act
      try {
        assertThrows(CancellationException.class, () -> JavaFrontends.observed(interrupted).produce(request));

        // Assert
        assertTrue(Thread.currentThread().isInterrupted());
        assertEquals(4, points(reader, "state").getFirst().getValue());
        assertTrue(points(reader, "source.translated.nodes").getFirst().getValue() > 0);
        assertTrue(points(reader, "source.nodes").isEmpty());
        assertEquals(4, points(reader, "stage.state").stream().filter(value ->
            "coverage-reconciliation".equals(value.getAttributes().get(AttributeKey.stringKey("aquila.stage"))))
            .findFirst().orElseThrow().getValue());
      } finally {
        Thread.interrupted();
      }
    }
  }

  private static OpenTelemetryObserver observer(InMemoryMetricReader reader) {
    return new OpenTelemetryObserver(OpenTelemetrySdk.builder()
        .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build()).build());
  }

  private static List<DoublePointData> points(InMemoryMetricReader reader, String name) {
    return reader.collectAllMetrics().stream().filter(metric -> metric.getName().equals("aquila.execution." + name))
        .flatMap(metric -> metric.getDoubleGaugeData().getPoints().stream()).toList();
  }

  private static DoublePointData point(InMemoryMetricReader reader, String name, String execution) {
    return points(reader, name).stream().filter(value -> execution.equals(id(value))).findFirst().orElseThrow();
  }

  private static String id(DoublePointData point) {
    return point.getAttributes().get(AttributeKey.stringKey("aquila.execution.id"));
  }
}
