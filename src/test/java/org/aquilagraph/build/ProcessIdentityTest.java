package org.aquilagraph.build;

import static org.aquilagraph.build.BuildResourceAssertions.assertClosed;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.ProcessHandle.Info;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.aquilagraph.build.WindowsJobObject.Completion;
import org.aquilagraph.build.WindowsKernel.ProcessAccessDeniedException;

class ProcessIdentityTest {
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

  @Test
  void givenAProcessThatExitsWhileMetadataIsRead_whenTheJournalObservesIt_thenTheCompletedProcessDoesNotBecomeAnUnknownLiveIdentity() throws Exception {
    // Arrange
    var request = request();
    var process = metadataTransition(true);
    try (var lease = BuildLease.acquire(request)) {
      var journal = BuildJournal.reserve(lease.context(), "process-observation");
      try {
        // Act
        journal.observe(process);

        // Assert
        assertTrue(journal.identities().isEmpty());
        assertTrue(ProcessIdentity.observeAvailable(process).isEmpty());
      } finally {
        journal.closeWithoutLaunch();
      }
    }
  }

  @Test
  void givenALiveProcessWithoutStartMetadata_whenTheJournalObservesIt_thenTheMissingIdentityStillFailsClosed() throws Exception {
    // Arrange
    var request = request();
    var process = metadataTransition(false);
    try (var lease = BuildLease.acquire(request)) {
      var journal = BuildJournal.reserve(lease.context(), "process-observation");
      try {
        // Act
        var failure = assertThrows(IOException.class, () -> journal.observe(process));

        // Assert
        assertTrue(failure.getMessage().contains("Process start time is unavailable"));
        assertTrue(journal.identities().isEmpty());
      } finally {
        journal.closeWithoutLaunch();
      }
    }
  }

  private BuildRequest request() throws IOException {
    var root = directory.toRealPath();
    return new BuildRequest(root, Path.of("").toRealPath(), root.resolve("mvnw"), BuildOperation.COMPILE, List.of());
  }

  private static ProcessHandle metadataTransition(boolean exitsDuringRead) {
    var live = new AtomicBoolean(true);
    var info = (Info) Proxy.newProxyInstance(Info.class.getClassLoader(), new Class<?>[] {Info.class}, (proxy, method, arguments) -> {
      if ("startInstant".equals(method.getName())) {
        if (exitsDuringRead) {
          live.set(false);
        }
        return Optional.empty();
      }
      throw new UnsupportedOperationException(method.getName());
    });
    return (ProcessHandle) Proxy.newProxyInstance(ProcessHandle.class.getClassLoader(), new Class<?>[] {ProcessHandle.class},
        (proxy, method, arguments) -> switch (method.getName()) {
          case "info" -> info;
          case "isAlive" -> live.get();
          case "pid" -> Long.MAX_VALUE;
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }
}
