package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.aquilagraph.testsupport.IsolatedBuild;
import org.aquilagraph.testsupport.OwnedProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class ProcessInventoryProcessIT {
  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path directory;

  @Test
  void givenLiveUnacknowledgedAndAbsentReservations_whenNativeInventoryRuns_thenItFindsOnlyTheExactLiveChild() throws Exception {
    // Arrange
    var root = directory.toRealPath();
    var request = new BuildRequest(root, Path.of("").toRealPath(), Path.of("").toRealPath().resolve(IsolatedBuild.windows() ? "mvnw.cmd" : "mvnw"),
        BuildOperation.COMPILE, List.of());
    try (var lease = BuildLease.acquire(request)) {
      var live = BuildJournal.reserve(lease.context(), "command-launcher");
      var absent = BuildJournal.reserve(lease.context(), "command-launcher");
      var marker = root.resolve("ready");
      var log = root.resolve("observed-process.log");
      var command = List.of(IsolatedBuild.java().toString(), "-cp", System.getProperty("java.class.path"),
          ProcessObservationClient.class.getName(), marker.toString(), root.resolve("release").toString(), "x".repeat(8 * 1024), lease.context().nonce(), live.reservation());
      try (var process = OwnedProcess.start(new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()))) {
        var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.exists(marker) && process.handle().isAlive() && System.nanoTime() < deadline) {
          TimeUnit.MILLISECONDS.sleep(10);
        }
        assertTrue(Files.exists(marker), Files.readString(log));

        // Act
        var found = ProcessInventory.reservation(live.directory());
        var missing = ProcessInventory.reservation(absent.directory());

        // Assert
        assertEquals(Set.of(process.handle().pid()), found.stream().map(ProcessHandle::pid).collect(Collectors.toSet()));
        assertTrue(missing.isEmpty());
        assertTrue(process.handle().isAlive());
        live.observe(process.handle());
        Files.writeString(root.resolve("release"), "release");
        assertTrue(process.await(Duration.ofSeconds(15)), Files.readString(log));
      } finally {
        live.closeObserved();
        absent.closeWithoutLaunch();
      }
    }
  }
}
