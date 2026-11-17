package org.aquilagraph.observability;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenTelemetryObservationTest {
  @TempDir Path directory;

  @Test
  void givenObservedFrontend_whenProducingAst_thenIdentityStagesLogsMetricsAndJfrAgree() throws IOException {
    // Arrange
    var request = request();
    var expected = JavaFrontends.standard().produce(request);
    var spans = InMemorySpanExporter.create();
    var logs = InMemoryLogRecordExporter.create();
    var metrics = InMemoryMetricReader.create();
    var sdk = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build())
        .setLoggerProvider(SdkLoggerProvider.builder().addLogRecordProcessor(SimpleLogRecordProcessor.create(logs)).build())
        .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metrics).build()).build();
    var destination = directory.resolve("analysis.jfr");

    // Act
    try (var observer = new OpenTelemetryObserver(sdk); var recording = new Recording()) {
      recording.enable("org.aquilagraph.JavaAnalysisStage").withoutThreshold();
      recording.start();
      var actual = JavaFrontends.observed(observer).produce(request);
      recording.stop();
      recording.dump(destination);

      // Assert
      assertEquals(expected.result().orElseThrow().identity(), actual.result().orElseThrow().identity());
      var finished = spans.getFinishedSpanItems();
      var root = finished.stream().filter(span -> span.getName().equals("aquila.analysis.production")).findFirst().orElseThrow();
      assertEquals(10, finished.size());
      assertTrue(finished.stream().filter(span -> span != root).allMatch(span -> span.getParentSpanId().equals(root.getSpanId())));
      assertTrue(finished.stream().allMatch(span -> span.getTraceId().equals(root.getTraceId())));
      assertEquals("COMPLETE", root.getAttributes().get(AttributeKey.stringKey("aquila.outcome")));
      assertEquals(20, logs.getFinishedLogRecordItems().size());
      assertTrue(logs.getFinishedLogRecordItems().stream().allMatch(log -> log.getSpanContext().getTraceId().equals(root.getTraceId())));
      assertTrue(logs.getFinishedLogRecordItems().stream().allMatch(log -> root.getAttributes().get(AttributeKey.stringKey("aquila.execution.id"))
          .equals(log.getAttributes().get(AttributeKey.stringKey("aquila.execution.id")))));
      var collected = metrics.collectAllMetrics();
      var durations = collected.stream().filter(metric -> metric.getName().equals("aquila.analysis.stage.duration")).findFirst().orElseThrow();
      assertEquals("s", durations.getUnit());
      assertTrue(durations.getHistogramData().getPoints().stream().allMatch(point ->
          point.getBoundaries().getFirst() <= 0.005 && point.getBoundaries().getLast() >= 300));
      var count = collected.stream().filter(metric -> metric.getName().equals("aquila.analysis.completed")).findFirst().orElseThrow();
      assertEquals(1, count.getLongSumData().getPoints().iterator().next().getValue());
      var active = collected.stream().filter(metric -> metric.getName().equals("aquila.analysis.active")).findFirst().orElseThrow();
      assertEquals(0, active.getLongSumData().getPoints().iterator().next().getValue());
      var allowed = Set.of("aquila.stage", "aquila.profile", "aquila.outcome", "memory.type", "gc.name", "thread.kind",
          "aquila.execution.id", "aquila.trace.id", "diagnostic.severity");
      assertTrue(collected.stream().flatMap(metric -> metric.getData().getPoints().stream())
          .allMatch(point -> point.getAttributes().asMap().keySet().stream().allMatch(key -> allowed.contains(key.getKey()))));
      assertTrue(collected.stream().filter(metric -> !metric.getName().startsWith("aquila.execution."))
          .flatMap(metric -> metric.getData().getPoints().stream())
          .noneMatch(point -> point.getAttributes().get(AttributeKey.stringKey("aquila.execution.id")) != null));
      var events = RecordingFile.readAllEvents(destination).stream()
          .filter(event -> event.getEventType().getName().equals("org.aquilagraph.JavaAnalysisStage")).toList();
      assertEquals(20, events.size());
      assertTrue(events.stream().allMatch(event -> event.getString("traceId").equals(root.getTraceId())));
      assertEquals(Context.root(), Context.current());
    }
  }

  @Test
  void givenConcurrentExecutions_whenScopesClose_thenParentsDoNotLeakAcrossThreads() throws Exception {
    // Arrange
    var spans = InMemorySpanExporter.create();
    var sdk = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build()).build();
    try (var observer = new OpenTelemetryObserver(sdk); var executor = Executors.newFixedThreadPool(2)) {
      // Act
      var first = executor.submit(() -> observe(observer));
      var second = executor.submit(() -> observe(observer));
      var firstTrace = first.get();
      var secondTrace = second.get();

      // Assert
      assertNotEquals(firstTrace, secondTrace);
      assertEquals(4, spans.getFinishedSpanItems().size());
      assertEquals(2, spans.getFinishedSpanItems().stream().filter(span -> !span.getParentSpanContext().isValid()).count());
      assertEquals(Context.root(), Context.current());
    }
  }

  @Test
  void givenFailedStage_whenClosing_thenFailureAndCompletionMetricsRemainObservable() {
    // Arrange
    var spans = InMemorySpanExporter.create();
    var metrics = InMemoryMetricReader.create();
    var sdk = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build())
        .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metrics).build()).build();
    try (var observer = new OpenTelemetryObserver(sdk)) {
      // Act
      try (var stage = observer.start("production", Map.of())) {
        stage.event("analysis.failure", Map.of("exception.type", "java.io.IOException"));
      }

      // Assert
      var root = spans.getFinishedSpanItems().getFirst();
      assertEquals("FAILED", root.getAttributes().get(AttributeKey.stringKey("aquila.outcome")));
      assertEquals(StatusCode.ERROR, root.getStatus().getStatusCode());
      assertFalse(metrics.collectAllMetrics().isEmpty());
    }
  }

  private static String observe(OpenTelemetryObserver observer) {
    try (var root = observer.start("production", Map.of())) {
      try (var child = observer.start("parse", Map.of())) {
        assertEquals(root.traceId(), child.traceId());
        assertEquals(root.executionId(), child.executionId());
        child.complete(Map.of());
      }
      root.complete(Map.of("aquila.completion", "COMPLETE"));
      return root.traceId();
    }
  }

  private AstProductionRequest request() {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "class Example { int increment(int value) { return value + 1; } }")));
    return new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }
}
