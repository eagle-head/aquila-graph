package org.aquilagraph.build;

import static org.aquilagraph.build.BuildResourceAssertions.assertClosed;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.aquilagraph.build.WindowsJobObject.Completion;
import org.aquilagraph.build.WindowsKernel.ProcessAccessDeniedException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@Tag("process")
class WindowsProcessIdentityProcessIT {
  private static final long SYSTEM_PROCESS_ID = 4;
  @TempDir Path directory;

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void givenAProtectedProcessWithADifferentCreationTime_whenCompletionChecksAStaleIdentity_thenOnlyTheOldIdentityIsDiscarded() throws Exception {
    // Arrange
    var request = request();
    try (var lease = BuildLease.acquire(request)) {
      var journal = BuildJournal.reserve(lease.context(), "process-observation");
      try {
        var current = WindowsProcessInventory.identity(lease.context().properties(), SYSTEM_PROCESS_ID).orElseThrow();
        var stale = new ProcessIdentity(current.pid(), current.started().minusSeconds(1));
        assertThrows(ProcessAccessDeniedException.class, () -> WindowsKernel.instance().openProcess(stale));

        // Act
        try (var completion = new Completion()) {
          completion.observe(journal, stale);
          completion.await(System.nanoTime() + Duration.ofSeconds(1).toNanos());
        }

        // Assert
        assertTrue(WindowsProcessInventory.identity(lease.context().properties(), SYSTEM_PROCESS_ID).filter(current::equals).isPresent());
        assertTrue(journal.identities().isEmpty());
      } finally {
        journal.closeWithoutLaunch();
      }
    }
    assertClosed(request.root());
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void givenAMatchingProtectedProcessIdentity_whenNativeAccessIsDenied_thenCompletionStillRefusesToCertifyItsExit() throws Exception {
    // Arrange
    var request = request();
    try (var lease = BuildLease.acquire(request)) {
      var journal = BuildJournal.reserve(lease.context(), "process-observation");
      try (var completion = new Completion()) {
        var current = WindowsProcessInventory.identity(lease.context().properties(), SYSTEM_PROCESS_ID).orElseThrow();

        // Act
        var failure = assertThrows(ProcessAccessDeniedException.class, () -> completion.observe(journal, current));

        // Assert
        assertTrue(failure.getMessage().contains("Windows error 5"));
        assertTrue(WindowsProcessInventory.identity(lease.context().properties(), SYSTEM_PROCESS_ID).filter(current::equals).isPresent());
      } finally {
        journal.closeWithoutLaunch();
      }
    }
    assertClosed(request.root());
  }

  private BuildRequest request() throws IOException {
    var root = directory.toRealPath();
    return new BuildRequest(root, Path.of("").toRealPath(), root.resolve("mvnw"), BuildOperation.COMPILE, List.of());
  }
}
