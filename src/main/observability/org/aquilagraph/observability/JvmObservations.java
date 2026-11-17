package org.aquilagraph.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.BatchCallback;
import java.lang.management.ManagementFactory;

/** Bounded JVM instruments without file paths, execution IDs or per-thread series. */
final class JvmObservations implements AutoCloseable {
  private final BatchCallback callback;

  JvmObservations(Meter meter) {
    var heap = meter.gaugeBuilder("aquila.jvm.memory.used").setUnit("By").ofLongs().buildObserver();
    var threads = meter.gaugeBuilder("aquila.jvm.threads").ofLongs().buildObserver();
    var collections = meter.counterBuilder("aquila.jvm.gc.collections").buildObserver();
    var gcTime = meter.counterBuilder("aquila.jvm.gc.duration").setUnit("s").ofDoubles().buildObserver();
    callback = meter.batchCallback(() -> record(heap, threads, collections, gcTime), heap, threads, collections, gcTime);
  }

  private static void record(ObservableLongMeasurement heap, ObservableLongMeasurement threads, ObservableLongMeasurement collections,
      ObservableDoubleMeasurement gcTime) {
    var memory = ManagementFactory.getMemoryMXBean();
    heap.record(memory.getHeapMemoryUsage().getUsed(), Attributes.builder().put("memory.type", "heap").build());
    heap.record(memory.getNonHeapMemoryUsage().getUsed(), Attributes.builder().put("memory.type", "non_heap").build());
    threads.record(ManagementFactory.getThreadMXBean().getThreadCount());
    for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
      var dimensions = Attributes.builder().put("gc.name", collector.getName()).build();
      if (collector.getCollectionCount() >= 0) {
        collections.record(collector.getCollectionCount(), dimensions);
      }
      if (collector.getCollectionTime() >= 0) {
        gcTime.record(collector.getCollectionTime() / 1000.0, dimensions);
      }
    }
  }

  @Override
  public void close() {
    callback.close();
  }
}
