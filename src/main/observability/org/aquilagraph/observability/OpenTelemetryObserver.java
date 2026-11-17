package org.aquilagraph.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.aquilagraph.frontend.AnalysisObserver;
import org.jspecify.annotations.Nullable;

/** Owns the SDK without changing the embedding application's global telemetry or logging. */
final class OpenTelemetryObserver implements AnalysisObserver {
  static final ContextKey<String> EXECUTION = ContextKey.named("aquila.execution.id");
  private static final String PRODUCTION = "production";
  private static final String SCOPE = "org.aquilagraph.frontend";
  private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
  private final OpenTelemetrySdk sdk;
  private final Tracer tracer;
  private final Logger logger;
  private final DoubleHistogram duration;
  private final LongCounter completed;
  private final LongCounter files;
  private final LongCounter nodes;
  private final LongUpDownCounter active;
  private final JvmObservations jvm;
  private final ExecutionObservations executions;
  private final AtomicBoolean closed = new AtomicBoolean();

  OpenTelemetryObserver(OpenTelemetrySdk sdk) {
    this.sdk = sdk;
    tracer = sdk.getTracer(SCOPE);
    logger = sdk.getLogsBridge().get(SCOPE);
    var meter = sdk.getMeter(SCOPE);
    duration = meter.histogramBuilder("aquila.analysis.stage.duration").setUnit("s").setDescription("Analysis stage elapsed time")
        .setExplicitBucketBoundariesAdvice(
            List.of(0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0, 600.0))
        .build();
    completed = meter.counterBuilder("aquila.analysis.completed").setDescription("Finished analysis attempts").build();
    files = meter.counterBuilder("aquila.analysis.source.files").setDescription("Source files in finished analyses").build();
    nodes = meter.counterBuilder("aquila.analysis.source.nodes").setDescription("Admitted AST nodes in finished analyses").build();
    active = meter.upDownCounterBuilder("aquila.analysis.active").setDescription("Analyses currently executing").build();
    jvm = new JvmObservations(meter);
    executions = new ExecutionObservations(meter);
  }

  @Override
  public Observation start(String stage, Map<String, String> attributes) {
    if (closed.get()) {
      return AnalysisObserver.none().start(stage, attributes);
    }
    var previous = CURRENT.get();
    var parent = previous == null ? Context.current() : previous;
    var execution = parent.get(EXECUTION);
    if (PRODUCTION.equals(stage) || execution == null) {
      execution = UUID.randomUUID().toString();
    }
    var builder = tracer.spanBuilder("aquila.analysis." + stage).setParent(parent).setAttribute("aquila.execution.id", execution);
    attributes.forEach(builder::setAttribute);
    var span = builder.startSpan();
    var context = parent.with(span).with(EXECUTION, execution);
    var dimensions = Attributes.builder().put("aquila.stage", stage)
        .put("aquila.profile", attributes.getOrDefault("aquila.profile", "unknown")).build();
    if (PRODUCTION.equals(stage)) {
      active.add(1, dimensions);
    }
    executions.start(execution, span.getSpanContext().getTraceId(), stage, attributes);
    var observation = new StageObservation(this, span, context, previous, dimensions, attributes, execution, PRODUCTION.equals(stage));
    CURRENT.set(context);
    return observation;
  }

  void log(String name, Context context, Map<String, String> values, String execution, String stage) {
    executions.event(execution, name, values);
    var record = logger.logRecordBuilder().setContext(context).setBody(name)
        .setSeverity("analysis.failure".equals(name) ? Severity.ERROR : Severity.INFO)
        .setAttribute(AttributeKey.stringKey("aquila.execution.id"), execution).setAttribute(AttributeKey.stringKey("aquila.stage"), stage);
    values.forEach(
        (key, value) -> record.setAttribute(AttributeKey.stringKey(key), value.length() > 2048 ? value.substring(0, 2048) : value));
    record.emit();
  }

  void restore(@Nullable Context previous) {
    if (previous == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(previous);
    }
  }

  void finish(Context context, Attributes dimensions, boolean root, String outcome, long started, Map<String, String> input,
      Map<String, String> result) {
    var qualified = dimensions.toBuilder().put("aquila.outcome", outcome).build();
    duration.record((System.nanoTime() - started) / 1_000_000_000.0, qualified, context);
    var execution = context.get(EXECUTION);
    var stage = dimensions.get(AttributeKey.stringKey("aquila.stage"));
    if (execution != null && stage != null) {
      executions.finish(execution, stage, root, outcome, result);
    }
    if (root) {
      active.add(-1, dimensions);
      completed.add(1, qualified);
      files.add(Long.parseLong(input.getOrDefault("aquila.source.files", "0")), qualified);
      nodes.add(Math.max(0, Long.parseLong(result.getOrDefault("aquila.source.nodes", "0"))), qualified);
      await("execution metrics flush", sdk.getSdkMeterProvider()::forceFlush);
      executions.prune();
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      await("flush", () -> CompletableResultCode.ofAll(List.of(sdk.getSdkTracerProvider().forceFlush(),
          sdk.getSdkLoggerProvider().forceFlush(), sdk.getSdkMeterProvider().forceFlush())));
      jvm.close();
      executions.close();
      await("shutdown", () -> CompletableResultCode.ofAll(
          List.of(sdk.getSdkTracerProvider().shutdown(), sdk.getSdkLoggerProvider().shutdown(), sdk.getSdkMeterProvider().shutdown())));
    }
  }

  private static void await(String operation, Supplier<CompletableResultCode> action) {
    var interrupted = Thread.interrupted();
    try {
      var result = action.get();
      result.join(5, TimeUnit.SECONDS);
      if (!result.isSuccess()) {
        System.getLogger(OpenTelemetryObserver.class.getName()).log(Level.WARNING,
            "Aquila telemetry " + operation + " did not succeed within its deadline; telemetry may be missing");
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
