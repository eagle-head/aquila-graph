package org.aquilagraph.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded execution snapshots, independent of cumulative fleet instruments. */
final class ExecutionObservations implements AutoCloseable {
  private static final int RETAINED_COMPLETIONS = 32;
  private final Map<String, ExecutionSnapshot> executions = new LinkedHashMap<>();
  private final Deque<String> completed = new ArrayDeque<>();
  private final Map<String, ObservableDoubleMeasurement> instruments = new LinkedHashMap<>();
  private final BatchCallback callback;

  ExecutionObservations(Meter meter) {
    instrument(meter, "started", "s", "Execution start Unix timestamp");
    instrument(meter, "heartbeat", "s", "Last execution observation Unix timestamp; frozen at completion");
    instrument(meter, "elapsed", "s", "Measured execution elapsed time; frozen at completion");
    instrument(meter, "state", "", "0 running, 1 complete, 2 incomplete, 3 failed, 4 cancelled, 6 succeeded, 7 aborted, 8 unavailable");
    instrument(meter, "source.files", "", "Known input source files");
    instrument(meter, "source.nodes", "", "Admitted AST nodes; absent until an outcome exists");
    instrument(meter, "source.translated.nodes", "", "Translated AST nodes before final coverage and outcome admission");
    instrument(meter, "diagnostics", "", "Emitted diagnostic count by severity");
    instrument(meter, "stage.elapsed", "s", "Exact stage elapsed time including unfinished stages");
    instrument(meter, "stage.state", "", "Stage state using the execution state codes");
    instrument(meter, "jvm.memory.used", "By", "JVM memory used while this execution is observed");
    instrument(meter, "jvm.memory.committed", "By", "JVM memory committed while this execution is observed");
    instrument(meter, "jvm.memory.limit", "By", "JVM memory limit when defined");
    instrument(meter, "jvm.threads", "", "JVM live and daemon thread counts");
    instrument(meter, "jvm.classes", "", "JVM currently loaded classes");
    instrument(meter, "jvm.cpu", "s", "Process CPU consumed since execution start, including other host work");
    instrument(meter, "jvm.processors", "", "Processors available to the JVM");
    instrument(meter, "jvm.gc.duration", "s", "MXBean GC collection time since execution start, not summed pause time");
    instrument(meter, "jvm.gc.collections", "", "GC collections since execution start");
    var measurements = instruments.values().toArray(ObservableDoubleMeasurement[]::new);
    callback = meter.batchCallback(this::record, measurements[0], Arrays.copyOfRange(measurements, 1, measurements.length));
  }

  private void instrument(Meter meter, String name, String unit, String description) {
    instruments.put(name, meter.gaugeBuilder("aquila.execution." + name).setUnit(unit).setDescription(description).buildObserver());
  }

  synchronized void start(String execution, String trace, String stage, Map<String, String> input) {
    executions.computeIfAbsent(execution, ignored -> new ExecutionSnapshot(execution, trace, input)).start(stage);
  }

  synchronized void event(String execution, String name, Map<String, String> attributes) {
    var snapshot = executions.get(execution);
    if (snapshot != null && "analysis.diagnostic".equals(name)) {
      snapshot.diagnostic(attributes.getOrDefault("diagnostic.severity", "UNKNOWN"));
    }
  }

  synchronized void finish(String execution, String stage, boolean root, String outcome, Map<String, String> result) {
    var snapshot = executions.get(execution);
    if (snapshot != null) {
      snapshot.finish(stage, root, outcome, result);
      if (root) {
        completed.addLast(execution);
      }
    }
  }

  synchronized void prune() {
    while (completed.size() > RETAINED_COMPLETIONS) {
      executions.remove(completed.removeFirst());
    }
  }

  private synchronized void record() {
    for (var execution : executions.values()) {
      execution.record(this::recordValue);
    }
  }

  private void recordValue(String name, double value, Attributes attributes) {
    var instrument = instruments.get(name);
    if (instrument != null) {
      instrument.record(value, attributes);
    }
  }

  @Override
  public void close() {
    callback.close();
  }
}
