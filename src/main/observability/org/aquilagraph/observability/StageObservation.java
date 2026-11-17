package org.aquilagraph.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import java.util.Map;
import org.aquilagraph.frontend.AnalysisObserver.Observation;
import org.jspecify.annotations.Nullable;

/** A synchronous stage scope, closed on its originating analysis thread. */
final class StageObservation implements Observation {
  private static final String FAILURE = "analysis.failure";
  private static final String ABORTED = "ABORTED";
  private final OpenTelemetryObserver owner;
  private final Span span;
  private final Context context;
  private final @Nullable Context previous;
  private final Attributes dimensions;
  private final Map<String, String> input;
  private final String execution;
  private final boolean root;
  private final long started = System.nanoTime();
  private Map<String, String> result = Map.of();
  private String outcome = ABORTED;
  private boolean closed;

  StageObservation(OpenTelemetryObserver owner, Span span, Context context, @Nullable Context previous, Attributes dimensions,
      Map<String, String> input, String execution, boolean root) {
    this.owner = owner;
    this.span = span;
    this.context = context;
    this.dimensions = dimensions;
    this.input = Map.copyOf(input);
    this.execution = execution;
    this.root = root;
    this.previous = previous;
    owner.log("analysis.stage.started", context, input, execution, stage());
  }

  private String stage() {
    var name = dimensions.get(AttributeKey.stringKey("aquila.stage"));
    return name == null ? "unknown" : name;
  }

  @Override
  public String executionId() {
    return execution;
  }

  @Override
  public String traceId() {
    return span.getSpanContext().getTraceId();
  }

  @Override
  public String spanId() {
    return span.getSpanContext().getSpanId();
  }

  @Override
  public void complete(Map<String, String> attributes) {
    result = Map.copyOf(attributes);
    var completion = attributes.getOrDefault("aquila.completion", "");
    outcome = completion.isBlank() ? "SUCCEEDED" : completion;
    attributes.forEach(span::setAttribute);
  }

  @Override
  public void event(String name, Map<String, String> attributes) {
    var values = Attributes.builder();
    attributes.forEach(values::put);
    span.addEvent(name, values.build());
    if (FAILURE.equals(name)) {
      outcome = "cancelled".equals(attributes.get("operation.state")) ? "CANCELLED" : "FAILED";
      span.setStatus(StatusCode.ERROR);
    }
    owner.log(name, context, attributes, execution, stage());
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      try {
        span.setAttribute("aquila.outcome", outcome);
        if (ABORTED.equals(outcome)) {
          span.setStatus(StatusCode.ERROR);
        }
        owner.finish(context, dimensions, root, outcome, started, input, result);
        owner.log("analysis.stage.finished", context, Map.of("aquila.outcome", outcome), execution, stage());
      } finally {
        span.end();
        owner.restore(previous);
      }
    }
  }
}
