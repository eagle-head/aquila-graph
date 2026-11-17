package org.aquilagraph.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.aquilagraph.frontend.AnalysisObserver;
import org.aquilagraph.frontend.AnalysisObserverFactory;

/** Creates an isolated SDK with bounded queues and host-controlled lifetime. */
public final class OpenTelemetryObserverFactory implements AnalysisObserverFactory {
  /** Creates the service provider. */
  public OpenTelemetryObserverFactory() {
  }

  @Override
  public AnalysisObserver open(Map<String, String> configuration) {
    var endpoint = configuration.getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://127.0.0.1:4318").replaceAll("/+$", "");
    var uri = URI.create(endpoint);
    if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null) {
      throw new IllegalArgumentException("OTEL_EXPORTER_OTLP_ENDPOINT must be an absolute HTTP or HTTPS URL");
    }
    var attributes = Attributes.builder().put("service.name", configuration.getOrDefault("OTEL_SERVICE_NAME", "aquila-graph"))
        .put("service.version", configuration.getOrDefault("AQUILA_VERSION", "development"))
        .put("service.instance.id", UUID.randomUUID().toString()).put("process.runtime.version", Runtime.version().toString())
        .put("os.type", System.getProperty("os.name")).build();
    var resource = Resource.getDefault().merge(Resource.create(attributes));
    var timeout = Duration.ofSeconds(2);
    var meters = SdkMeterProvider.builder().setResource(resource)
        .registerMetricReader(
            PeriodicMetricReader.builder(OtlpHttpMetricExporter.builder().setEndpoint(endpoint + "/v1/metrics").setTimeout(timeout).build())
                .setInterval(Duration.ofSeconds(5)).build())
        .build();
    var traces = SdkTracerProvider.builder().setResource(resource).addSpanProcessor(BatchSpanProcessor
        .builder(OtlpHttpSpanExporter.builder().setEndpoint(endpoint + "/v1/traces").setTimeout(timeout).build()).setMaxQueueSize(2048)
        .setMaxExportBatchSize(256).setScheduleDelay(Duration.ofMillis(200)).setExporterTimeout(timeout).setMeterProvider(meters).build())
        .build();
    var logs = SdkLoggerProvider.builder().setResource(resource).addLogRecordProcessor(BatchLogRecordProcessor
        .builder(OtlpHttpLogRecordExporter.builder().setEndpoint(endpoint + "/v1/logs").setTimeout(timeout).build()).setMaxQueueSize(2048)
        .setMaxExportBatchSize(256).setScheduleDelay(Duration.ofMillis(200)).setExporterTimeout(timeout).setMeterProvider(meters).build())
        .build();
    return new OpenTelemetryObserver(
        OpenTelemetrySdk.builder().setTracerProvider(traces).setMeterProvider(meters).setLoggerProvider(logs).build());
  }
}
