package org.aquilagraph.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InterruptedObservationShutdownTest {
  @Test
  void givenInterruptedExecution_whenClosingObserver_thenExportAndShutdownDrainWithoutLosingInterrupt() {
    // Arrange
    var exporter = new InterruptCheckingExporter();
    var reader = PeriodicMetricReader.builder(exporter).setInterval(Duration.ofHours(1)).build();
    var sdk = OpenTelemetrySdk.builder().setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build()).build();

    // Act
    try {
      try (var observer = new OpenTelemetryObserver(sdk)) {
        try (var root = observer.start("production", Map.of())) {
          Thread.currentThread().interrupt();
          root.event("analysis.failure", Map.of("operation.state", "cancelled"));
        }
      }

      // Assert
      assertTrue(Thread.currentThread().isInterrupted());
      assertTrue(exporter.exports.get() > 0);
      assertEquals(1, exporter.shutdowns.get());
      assertFalse(exporter.interrupted.get());
    } finally {
      Thread.interrupted();
    }
  }

  private static final class InterruptCheckingExporter implements MetricExporter {
    private final AtomicBoolean interrupted = new AtomicBoolean();
    private final AtomicInteger exports = new AtomicInteger();
    private final AtomicInteger shutdowns = new AtomicInteger();

    @Override
    public AggregationTemporality getAggregationTemporality(InstrumentType type) {
      return AggregationTemporality.CUMULATIVE;
    }

    @Override
    public CompletableResultCode export(Collection<MetricData> metrics) {
      exports.incrementAndGet();
      return observed();
    }

    @Override
    public CompletableResultCode flush() {
      return observed();
    }

    @Override
    public CompletableResultCode shutdown() {
      shutdowns.incrementAndGet();
      return observed();
    }

    private CompletableResultCode observed() {
      if (Thread.currentThread().isInterrupted()) {
        interrupted.set(true);
        return CompletableResultCode.ofFailure();
      }
      return CompletableResultCode.ofSuccess();
    }
  }
}
