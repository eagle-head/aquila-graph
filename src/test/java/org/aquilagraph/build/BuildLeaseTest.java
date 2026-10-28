package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildLeaseTest {
  @TempDir
  Path directory;

  @Test
  void givenHeldLease_whenAContenderRuns_thenMutationIsRefusedAndTheStableLockSurvivesClosure() throws Exception {
    // Arrange
    var request = request();
    var sentinel = directory.resolve("sentinel");
    Files.writeString(sentinel, "unchanged");
    IOException failure;

    // Act
    try (var first = BuildLease.acquire(request)) {
      failure = assertThrows(IOException.class, () -> BuildLease.acquire(request));
      assertTrue(first.context().root().equals(directory.toRealPath()));
    }

    // Assert
    assertTrue(failure.getMessage().contains("Another local build owns"));
    assertEquals("unchanged", Files.readString(sentinel));
    assertTrue(Files.isRegularFile(directory.resolve(".build-state/build.lock")));
    try (var later = BuildLease.acquire(request)) {
      assertTrue(later.context().owner().liveProcess().isPresent());
    }
  }

  @Test
  void givenUnacknowledgedCreation_whenRecoveryRuns_thenTheReservationIsPreservedAndReuseIsRefused() throws Exception {
    // Arrange
    var request = request();
    BuildJournal reservation;
    var lease = BuildLease.acquire(request);
    reservation = BuildJournal.reserve(lease.context(), "command-launcher");
    assertThrows(IOException.class, lease::close);

    // Act
    var failure = assertThrows(IOException.class, () -> BuildLease.acquire(request));

    // Assert
    assertTrue(failure.getMessage().contains("unresolved resources"));
    assertTrue(Files.exists(reservation.directory().resolve("intent.properties")));
    reservation.closeWithoutLaunch();
    lease.close();
    try (var recovered = BuildLease.acquire(request)) {
      assertEquals(directory.toRealPath(), recovered.context().root());
    }
  }

  private BuildRequest request() throws IOException {
    var root = directory.toRealPath();
    return new BuildRequest(root, Path.of("").toRealPath(), root.resolve("mvnw"), BuildOperation.COMPILE, List.of());
  }

  @Test
  void givenAClaimFromAnotherProcessNamespace_whenThePlatformNameMatches_thenLocalRecoveryRemainsRefused() throws Exception {
    // Arrange
    var request = request();
    Properties foreign;
    try (var lease = BuildLease.acquire(request)) {
      foreign = lease.context().properties();
    }
    foreign.setProperty("owner.namespace", "foreign-process-namespace");
    foreign.setProperty("owner.pid", Long.toString(Long.MAX_VALUE));
    var active = directory.resolve(".build-state/active-owner.properties");
    BuildFiles.write(active, foreign);

    // Act
    try {
      var failure = assertThrows(IOException.class, () -> {
        try (var unexpected = BuildLease.acquire(request)) {
          unexpected.requireValid();
        }
      });

      // Assert
      assertTrue(failure.getMessage().contains("Another process namespace"));
      assertEquals("foreign-process-namespace", BuildFiles.read(active).getProperty("owner.namespace"));
    } finally {
      Files.deleteIfExists(active);
    }
    try (var accepted = BuildLease.acquire(request)) {
      assertTrue(accepted.context().owner().liveProcess().isPresent());
    }
  }
}
