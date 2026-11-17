package org.aquilagraph.observability;

import io.opentelemetry.api.common.Attributes;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable only under the owning execution registry's monitor. */
final class ExecutionSnapshot {
  private static final String SOURCE_TRANSLATION = "source-translation";
  private final Attributes attributes;
  private final long started = System.nanoTime();
  private final double startTime = System.currentTimeMillis() / 1000.0;
  private final long files;
  private final double cpuBaseline = cpuSeconds();
  private final Map<String, Long> gcTimeBaseline = new LinkedHashMap<>();
  private final Map<String, Long> gcCountBaseline = new LinkedHashMap<>();
  private final Map<String, Stage> stages = new LinkedHashMap<>();
  private final Map<String, Long> diagnostics = new LinkedHashMap<>();
  private final Map<ResourceValue, Double> resources = new LinkedHashMap<>();
  private double elapsed;
  private double heartbeat = startTime;
  private int state;
  private long nodes = -1;
  private long translatedNodes = -1;

  ExecutionSnapshot(String execution, String trace, Map<String, String> input) {
    attributes = Attributes.builder().put("aquila.execution.id", execution).put("aquila.trace.id", trace)
        .put("aquila.profile", input.getOrDefault("aquila.profile", "unknown")).build();
    files = Long.parseLong(input.getOrDefault("aquila.source.files", "0"));
    for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
      gcTimeBaseline.put(collector.getName(), collector.getCollectionTime());
      gcCountBaseline.put(collector.getName(), collector.getCollectionCount());
    }
  }

  void start(String stage) {
    stages.put(stage, new Stage(System.nanoTime(), 0, 0));
  }

  void diagnostic(String severity) {
    diagnostics.merge(severity, 1L, Long::sum);
  }

  void finish(String stage, boolean root, String outcome, Map<String, String> result) {
    if (SOURCE_TRANSLATION.equals(stage)) {
      translatedNodes = Long.parseLong(result.getOrDefault("aquila.source.nodes", "-1"));
    }
    var previous = stages.get(stage);
    if (previous != null) {
      stages.put(stage, new Stage(previous.started(), secondsSince(previous.started()), outcomeState(outcome)));
    }
    if (root) {
      sample();
      state = outcomeState(outcome);
      nodes = Long.parseLong(result.getOrDefault("aquila.source.nodes", "-1"));
    }
  }

  void record(ValueRecorder recorder) {
    if (state == 0) {
      sample();
    }
    recorder.record("started", startTime, attributes);
    recorder.record("heartbeat", heartbeat, attributes);
    recorder.record("elapsed", elapsed, attributes);
    recorder.record("state", state, attributes);
    recorder.record("source.files", (double) files, attributes);
    if (nodes >= 0) {
      recorder.record("source.nodes", (double) nodes, attributes);
    }
    if (translatedNodes >= 0) {
      recorder.record("source.translated.nodes", (double) translatedNodes, attributes);
    }
    for (var entry : stages.entrySet()) {
      var dimensions = attributes.toBuilder().put("aquila.stage", entry.getKey()).build();
      var stage = entry.getValue();
      recorder.record("stage.elapsed", stage.state() == 0 ? secondsSince(stage.started()) : stage.elapsed(), dimensions);
      recorder.record("stage.state", stage.state(), dimensions);
    }
    diagnostics.forEach((severity, count) -> recorder.record("diagnostics", count.doubleValue(),
        attributes.toBuilder().put("diagnostic.severity", severity).build()));
    resources.forEach((key, value) -> recorder.record(key.name(), value, key.attributes()));
  }

  private void sample() {
    elapsed = secondsSince(started);
    heartbeat = System.currentTimeMillis() / 1000.0;
    var memory = ManagementFactory.getMemoryMXBean();
    memory("heap", memory.getHeapMemoryUsage());
    memory("non_heap", memory.getNonHeapMemoryUsage());
    var threads = ManagementFactory.getThreadMXBean();
    resource("jvm.threads", threads.getThreadCount(), attributes.toBuilder().put("thread.kind", "live").build());
    resource("jvm.threads", threads.getDaemonThreadCount(), attributes.toBuilder().put("thread.kind", "daemon").build());
    resource("jvm.classes", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount(), attributes);
    var cpu = cpuSeconds();
    if (cpu >= 0 && cpuBaseline >= 0) {
      resource("jvm.cpu", Math.max(0, cpu - cpuBaseline), attributes);
    }
    resource("jvm.processors", Runtime.getRuntime().availableProcessors(), attributes);
    for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
      var dimensions = attributes.toBuilder().put("gc.name", collector.getName()).build();
      var time = gcTimeBaseline.getOrDefault(collector.getName(), -1L);
      var count = gcCountBaseline.getOrDefault(collector.getName(), -1L);
      if (time >= 0 && collector.getCollectionTime() >= time) {
        resource("jvm.gc.duration", (collector.getCollectionTime() - time) / 1000.0, dimensions);
      }
      if (count >= 0 && collector.getCollectionCount() >= count) {
        resource("jvm.gc.collections", (double) (collector.getCollectionCount() - count), dimensions);
      }
    }
  }

  private void memory(String type, MemoryUsage usage) {
    var dimensions = attributes.toBuilder().put("memory.type", type).build();
    resource("jvm.memory.used", (double) usage.getUsed(), dimensions);
    resource("jvm.memory.committed", (double) usage.getCommitted(), dimensions);
    if (usage.getMax() >= 0) {
      resource("jvm.memory.limit", (double) usage.getMax(), dimensions);
    }
  }

  private void resource(String name, double value, Attributes dimensions) {
    resources.put(new ResourceValue(name, dimensions), value);
  }

  private static double cpuSeconds() {
    return ProcessHandle.current().info().totalCpuDuration().map(duration -> duration.toNanos() / 1_000_000_000.0).orElse(-1.0);
  }

  private static double secondsSince(long start) {
    return (System.nanoTime() - start) / 1_000_000_000.0;
  }

  private static int outcomeState(String outcome) {
    return switch (outcome) {
      case "COMPLETE" -> 1;
      case "INCOMPLETE" -> 2;
      case "FAILED" -> 3;
      case "CANCELLED" -> 4;
      case "SUCCEEDED" -> 6;
      case "UNAVAILABLE" -> 8;
      default -> 7;
    };
  }

  private record Stage(long started, double elapsed, int state) {
  }

  private record ResourceValue(String name, Attributes attributes) {
  }

  @FunctionalInterface
  interface ValueRecorder {
    void record(String name, double value, Attributes attributes);
  }
}
