package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.aquilagraph.testsupport.NestedBuildOwnership;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.LINUX)
class LinuxProcessGroupProcessIT {
  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path directory;

  @Test
  void givenNestedBuildRoots_whenTheOuterOwnerDies_thenNestedSlicesCloseBeforeEitherRootCanMutateAgain() throws Exception {
    // Arrange
    var project = Path.of("").toRealPath();

    // Act
    var result = NestedBuildOwnership.run(directory, project);

    // Assert
    assertEquals("true", result.getProperty("hierarchy.verified"));
    assertEquals("true", result.getProperty("invocation.units.closed"));
    assertEquals("false", result.getProperty("worker.alive.before.recovery"));
    assertEquals("false", result.getProperty("worker.alive.after.recovery"));
    assertEquals("0", result.getProperty("outer.recovery.exit"));
    assertEquals("0", result.getProperty("inner.recovery.exit"));
  }
}
