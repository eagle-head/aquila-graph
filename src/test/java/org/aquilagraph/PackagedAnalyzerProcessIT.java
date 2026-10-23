package org.aquilagraph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackagedAnalyzerProcessIT {
  private static final List<String> SUSPENDED_JVM = List.of(
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:0,timeout=60000");

  @TempDir
  Path directory;

  @Test
  void givenSuspendedAnalyzer_whenLauncherTimesOut_thenActualJvmAndLauncherClose() throws Exception {
    // Arrange
    var source = Files.createDirectory(directory.resolve("source"));
    var baseline = Set.copyOf(ProcessHandle.current().descendants().toList());
    var result = new AtomicReference<PackagedAnalyzerProcess.Result>();
    var failure = new AtomicReference<Throwable>();
    var worker = start(source, result, failure);
    var owned = List.<ProcessHandle>of();
    try {
      owned = awaitLaunchedTree(worker, baseline);

      // Act
      worker.join(TimeUnit.SECONDS.toMillis(45));

      // Assert
      assertFalse(worker.isAlive(), "Timeout and process closure must finish within their bounds");
      assertNull(failure.get());
      var outcome = result.get();
      assertNotNull(outcome);
      assertFalse(outcome.completed());
      assertTrue(owned.stream().noneMatch(ProcessHandle::isAlive), "Every previously observed launcher/JVM must close");
      assertTrue(newDescendants(baseline).isEmpty(), "No owned descendant may survive timeout");
    } finally {
      closeRemaining(worker, owned, baseline);
    }
  }

  @Test
  void givenRunningAnalyzer_whenWaitingThreadIsInterrupted_thenFailureAndOwnedClosureArePreserved() throws Exception {
    // Arrange
    var source = Files.createDirectory(directory.resolve("source"));
    var baseline = Set.copyOf(ProcessHandle.current().descendants().toList());
    var failure = new AtomicReference<Throwable>();
    var worker = start(source, new AtomicReference<>(), failure);
    var owned = List.<ProcessHandle>of();
    try {
      owned = awaitLaunchedTree(worker, baseline);

      // Act
      worker.interrupt();
      worker.join(TimeUnit.SECONDS.toMillis(15));

      // Assert
      assertFalse(worker.isAlive(), "Interrupted process ownership must finish within its closure bound");
      assertInstanceOf(InterruptedException.class, failure.get());
      assertTrue(owned.stream().noneMatch(ProcessHandle::isAlive), "Every previously observed launcher/JVM must close");
      assertTrue(newDescendants(baseline).isEmpty(), "No owned descendant may survive interruption");
    } finally {
      closeRemaining(worker, owned, baseline);
    }
  }

  private Thread start(Path source, AtomicReference<PackagedAnalyzerProcess.Result> result, AtomicReference<Throwable> failure) {
    return Thread.ofPlatform().start(() -> {
      try {
        result.set(new PackagedAnalyzerProcess(directory).run(SUSPENDED_JVM, Map.of(), source));
      } catch (Throwable problem) {
        failure.set(problem);
      }
    });
  }

  private List<ProcessHandle> awaitLaunchedTree(Thread worker, Set<ProcessHandle> baseline) throws Exception {
    var output = directory.resolve("process-output.txt");
    var expected = System.getProperty("os.name").startsWith("Windows") ? 2 : 1;
    var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (worker.isAlive() && System.nanoTime() < deadline) {
      var actual = newDescendants(baseline);
      if (actual.size() >= expected && Files.exists(output) && Files.readString(output).contains("Listening for transport dt_socket")) {
        System.out.println("Observed live packaged process tree: " + actual.stream().map(ProcessHandle::pid).toList());
        return actual;
      }
      Thread.sleep(25);
    }
    throw new AssertionError("The delivered launcher and suspended JVM must be observed alive before the failure action");
  }

  private static List<ProcessHandle> newDescendants(Set<ProcessHandle> baseline) {
    return ProcessHandle.current().descendants().filter(process -> !baseline.contains(process)).toList();
  }

  private static void closeRemaining(Thread worker, List<ProcessHandle> observed, Set<ProcessHandle> baseline) throws Exception {
    worker.interrupt();
    worker.join(TimeUnit.SECONDS.toMillis(15));
    var remaining = new LinkedHashSet<>(observed);
    remaining.addAll(newDescendants(baseline));
    for (var process : remaining) {
      if (!process.isAlive()) {
        continue;
      }
      process.destroyForcibly();
      process.onExit().get(10, TimeUnit.SECONDS);
    }
    assertFalse(worker.isAlive(), "The observing worker must close");
  }
}
