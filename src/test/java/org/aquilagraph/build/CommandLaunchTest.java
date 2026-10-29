package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.time.Duration;
import org.aquilagraph.build.BuildCancellation.Requested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandLaunchTest {
  @TempDir Path directory;

  @Test
  void givenFailedPlatformRelease_whenTheLaunchCloses_thenOwnerAndCancellationCompletionRemainPending() throws Exception {
    // Arrange
    var platform = new Platform(Optional.empty(), new ProcessFailureStub(false));
    platform.closureFailure = Optional.of(new IOException("platform handle release failed"));
    try (var fixture = new Fixture(directory, platform)) {
      var cancellation = BuildCancellation.request(directory);
      assertThrows(Requested.class, () -> BuildCancellation.check(fixture.lease.context()));

      // Act
      var failure = assertThrows(IOException.class, fixture.launch::close);

      // Assert
      assertSame(platform.closureFailure.orElseThrow(), failure);
      assertFalse(Files.exists(fixture.journal.directory().resolve("closed.properties")));
      assertFalse(Files.exists(fixture.command.directory().resolve("closed.properties")));
      assertThrows(IOException.class, fixture.lease::close);
      assertThrows(IOException.class, () -> cancellation.awaitClosure(Duration.ZERO));
      assertFalse(Files.exists(fixture.lease.context().directory().resolve("owner-closed.properties")));
    }
  }

  @Test
  void givenFailedProcessCreationAndJournalClosure_whenTheOwnerCloses_thenTheStartupFailureRetainsItsCleanupFailure() throws Exception {
    // Arrange
    var primary = new IOException("process creation failed");
    var platform = new Platform(Optional.of(primary), new ProcessFailureStub(false));
    try (var fixture = new Fixture(directory, platform)) {
      fixture.blockClosure();

      // Act
      var failure = assertThrows(IOException.class, () -> {
        try (var launch = fixture.launch) {
          launch.start();
        }
      });

      // Assert
      assertSame(primary, failure);
      assertEquals(1, failure.getSuppressed().length);
      assertInstanceOf(IOException.class, failure.getSuppressed()[0]);
      assertEquals(1, platform.creations);
      fixture.assertUnclosed();
    }
  }

  @Test
  void givenCancellationAndFailedJournalClosure_whenStartupIsRefused_thenCancellationCannotLoseItsCleanupFailure() throws Exception {
    // Arrange
    var platform = new Platform(Optional.empty(), new ProcessFailureStub(false));
    try (var fixture = new Fixture(directory, platform)) {
      fixture.blockClosure();
      var context = fixture.lease.context();
      BuildFiles.write(context.directory().resolve("cancellation-request.properties"), context.properties());

      // Act
      var failure = assertThrows(Requested.class, () -> {
        try (var launch = fixture.launch) {
          launch.start();
        }
      });

      // Assert
      assertEquals(1, failure.getSuppressed().length);
      assertInstanceOf(IOException.class, failure.getSuppressed()[0]);
      assertEquals(0, platform.creations);
      context.requireIdentity(BuildFiles.read(context.directory().resolve("cancellation-observed.properties")));
      fixture.assertUnclosed();
    }
  }

  @Test
  void givenFailedStopAndThreeFailedEndpoints_whenTheLaunchCloses_thenEveryFailureRetainsItsEncounterOrder() throws Exception {
    // Arrange
    var process = new ProcessFailureStub(true);
    var platform = new Platform(Optional.empty(), process);
    try (var fixture = new Fixture(directory, platform)) {
      fixture.launch.start();

      // Act
      var failure = assertThrows(UnsupportedOperationException.class, fixture.launch::close);

      // Assert
      assertSame(process.stopFailure, failure);
      assertEquals(1, failure.getSuppressed().length);
      assertSame(process.outputFailure, failure.getSuppressed()[0]);
      assertEquals(List.of(process.errorFailure, process.inputFailure), List.of(failure.getSuppressed()[0].getSuppressed()));
      assertTrue(process.inputClosed);
      assertTrue(process.errorClosed);
      assertTrue(process.outputClosed);
      assertFalse(Files.exists(fixture.journal.directory().resolve("closed.properties")));
      assertFalse(Files.exists(fixture.command.directory().resolve("closed.properties")));
    }
  }

  @Test
  void givenFailedStopAndCleanEndpoints_whenTheLaunchCloses_thenTheOriginalFailureSurvivesWithoutInventedSuppressedFailures()
      throws Exception {
    // Arrange
    var process = new ProcessFailureStub(false);
    var platform = new Platform(Optional.empty(), process);
    try (var fixture = new Fixture(directory, platform)) {
      fixture.launch.start();

      // Act
      var failure = assertThrows(UnsupportedOperationException.class, fixture.launch::close);

      // Assert
      assertSame(process.stopFailure, failure);
      assertEquals(0, failure.getSuppressed().length);
      assertTrue(process.inputClosed);
      assertTrue(process.errorClosed);
      assertTrue(process.outputClosed);
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final BuildLease lease;
    private final BuildJournal journal;
    private final BuildJournal command;
    private final CommandLaunch launch;

    private Fixture(Path directory, Platform platform) throws IOException {
      var root = directory.toRealPath();
      lease = BuildLease.acquire(new BuildRequest(root, Path.of("").toRealPath(), root.resolve("mvnw"), BuildOperation.TEST, List.of()));
      journal = BuildJournal.reserve(lease.context(), "command-launcher");
      command = BuildJournal.reserve(lease.context(), "maven-wrapper");
      journal.recordCommand(command.reservation());
      launch = new CommandLaunch(lease, Optional.empty(), journal, command, platform);
    }

    private void blockClosure() throws IOException {
      var obstruction = Files.createDirectory(journal.directory().resolve("closed.properties"));
      Files.writeString(obstruction.resolve("blocker"), "keep the failed write observable");
    }

    private void assertUnclosed() throws IOException {
      assertTrue(Files.isDirectory(journal.directory().resolve("closed.properties")));
      assertEquals("keep the failed write observable", Files.readString(journal.directory().resolve("closed.properties/blocker")));
      assertFalse(Files.exists(command.directory().resolve("closed.properties")));
      assertFalse(Files.exists(lease.context().directory().resolve("owner-closed.properties")));
    }

    @Override
    public void close() throws IOException {
      var obstruction = journal.directory().resolve("closed.properties");
      if (Files.isDirectory(obstruction)) {
        Files.delete(obstruction.resolve("blocker"));
        Files.delete(obstruction);
      }
      // The platform double creates no operating-system process; only fixture records remain.
      journal.closeWithoutLaunch();
      command.closeWithoutLaunch();
      lease.close();
    }
  }

  private static final class Platform implements CommandPlatform {
    private final Optional<IOException> startupFailure;
    private final Process process;
    private int creations;
    private Optional<IOException> closureFailure = Optional.empty();

    private Platform(Optional<IOException> startupFailure, Process process) {
      this.startupFailure = startupFailure;
      this.process = process;
    }

    @Override
    public Process start(ProcessBuilder builder) throws IOException {
      creations++;
      if (startupFailure.isPresent()) {
        throw startupFailure.orElseThrow();
      }
      return process;
    }

    @Override
    public void requireAvailable() {
    }

    @Override
    public void close() throws IOException {
      if (closureFailure.isPresent()) {
        throw closureFailure.orElseThrow();
      }
    }
  }

}
