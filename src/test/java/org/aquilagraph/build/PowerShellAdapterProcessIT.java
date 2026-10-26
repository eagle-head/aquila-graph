package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.List;
import org.aquilagraph.testsupport.IsolatedBuild;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
class PowerShellAdapterProcessIT {
  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path directory;

  @Test
  void givenHeldOutputs_whenTheNativeAdapterRunsAContenderAndInvalidInput_thenTransportDoesNotMutateOutputs() throws Exception {
    // Arrange
    var fixture = new IsolatedBuild(directory);
    fixture.writePom("", "", "");
    var marker = fixture.root().resolve("target/retained.txt");
    Files.writeString(marker, "retained");
    var before = paths(marker.getParent());
    var request = new BuildRequest(fixture.root(), fixture.tools(), fixture.tools().resolve("mvnw.cmd"), BuildOperation.COMPILE, List.of());
    try (var watcher = fixture.root().getFileSystem().newWatchService()) {
      for (var target : List.of(fixture.root().resolve("target"), fixture.tools().resolve("target"))) {
        target.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
        var bootstrap = target.resolve("build-bootstrap");
        if (Files.isDirectory(bootstrap)) {
          bootstrap.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
        }
      }

      // Act
      IsolatedBuild.Result contender;
      try (var lease = BuildLease.acquire(request)) {
        lease.requireValid();
        contender = fixture.run(fixture.powerShell("compile"));
      }
      var invalid = fixture.run(fixture.powerShell("compile", "clean"));
      var debugBuilder = fixture.powerShell("compile");
      debugBuilder.environment().put("MAVEN_DEBUG_OPTS", "-Dexec.skip=true");
      var debug = fixture.run(debugBuilder);

      // Assert
      assertNotEquals(0, contender.exitCode(), contender.output());
      assertTrue(contender.output().contains("Another local build owns"), contender.output());
      assertNotEquals(0, invalid.exitCode(), invalid.output());
      assertTrue(invalid.output().contains("Unsupported flag"), invalid.output());
      assertNotEquals(0, debug.exitCode(), debug.output());
      assertTrue(debug.output().contains("MAVEN_DEBUG_OPTS"), debug.output());
      assertEquals(before, paths(marker.getParent()));
      assertEquals("retained", Files.readString(marker));
      var key = watcher.poll();
      while (key != null) {
        for (var event : key.pollEvents()) {
          assertFalse(event.kind() == StandardWatchEventKinds.OVERFLOW, "Output observation overflowed");
          if (event.context() instanceof Path relative) {
            var watched = (Path) key.watchable();
            assertFalse(watched.endsWith("build-bootstrap") || relative.startsWith("build-bootstrap"),
                "The adapter created bootstrap output before owning the project lease: " + watched.resolve(relative));
          }
        }
        key = watcher.poll();
      }
    }
  }

  private static List<String> paths(Path root) throws Exception {
    try (var files = Files.walk(root)) {
      return files.map(path -> root.relativize(path).toString()).sorted().toList();
    }
  }
}
