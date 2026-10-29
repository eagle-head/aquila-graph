package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.aquilagraph.build.BuildCancellation.Requested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildCancellationTest {
  @TempDir Path directory;

  @Test
  void givenDirectoryAtAcknowledgementPathAfterOwnerClosure_whenCancellationIsRead_thenItCannotBecomeUnobservedSuccess() throws Exception {
    // Arrange
    BuildCancellation cancellation;
    Path acknowledgement;
    try (var lease = BuildLease.acquire(request())) {
      cancellation = BuildCancellation.request(directory);
      acknowledgement = Files.createDirectory(lease.context().directory().resolve("cancellation-observed.properties"));
    }

    // Act
    assertThrows(IOException.class, () -> cancellation.awaitClosure(Duration.ZERO));

    // Assert
    assertTrue(Files.isDirectory(acknowledgement));
    assertTrue(Files.isRegularFile(cancellation.context().directory().resolve("owner-closed.properties")));
    assertFalse(Files.exists(directory.resolve(".build-state/active-owner.properties")));
  }

  @Test
  void givenFailedNativeObservationEndpoints_whenTheOwnerCloses_thenCancellationCannotClaimCompleteResourceRelease() throws Exception {
    // Arrange
    var lease = BuildLease.acquire(request());
    var context = InvocationContext.observation(lease.context().properties());
    var journal = BuildJournal.reserve(context, "process-observation");
    var process = new ProcessFailureStub(true);
    var observation = new NativeObservation(context, journal, builder -> process);
    var cancellation = BuildCancellation.request(directory);
    assertThrows(Requested.class, () -> BuildCancellation.check(lease.context()));
    assertThrows(UnsupportedOperationException.class, () -> observation.start(new ProcessBuilder("unused")));

    // Act
    try {
      assertThrows(IOException.class, observation::close);
      assertThrows(IOException.class, lease::close);

      // Assert
      assertThrows(IOException.class, () -> cancellation.awaitClosure(Duration.ZERO));
      assertFalse(Files.exists(lease.context().directory().resolve("owner-closed.properties")));
      assertFalse(Files.exists(context.directory().resolve("owner-closed.properties")));
    } finally {
      process.failEndpoints = false;
      observation.close();
      lease.close();
    }
    assertTrue(cancellation.awaitClosure(Duration.ZERO));
  }

  @Test
  void givenLiveOwner_whenCancellationIsRequested_thenTheOwnerObservesItAndClosureReleasesTheLease() throws Exception {
    // Arrange
    var lease = BuildLease.acquire(request());
    var context = lease.context();

    // Act
    BuildCancellation cancellation;
    try (lease) {
      cancellation = BuildCancellation.request(directory);
      var pending = assertThrows(IOException.class, () -> cancellation.awaitClosure(Duration.ZERO));
      assertTrue(pending.getMessage().contains("Cancellation remains pending"));
      assertThrows(Requested.class, () -> BuildCancellation.check(context));
    }
    var observed = cancellation.awaitClosure(Duration.ZERO);

    // Assert
    assertTrue(observed);
    context.requireIdentity(BuildFiles.read(context.directory().resolve("cancellation-request.properties")));
    context.requireIdentity(BuildFiles.read(context.directory().resolve("cancellation-observed.properties")));
    assertFalse(Files.exists(directory.resolve(".build-state/active-owner.properties")));
    try (var successor = BuildLease.acquire(request())) {
      assertFalse(context.nonce().equals(successor.context().nonce()));
    }
  }

  @Test
  void givenRequestedCancellation_whenTheOwnerCompletesBeforeObservingIt_thenTheResultDoesNotClaimCancellation() throws Exception {
    // Arrange
    BuildCancellation cancellation;
    Path ownerDirectory;
    try (var lease = BuildLease.acquire(request())) {
      ownerDirectory = lease.context().directory();
      cancellation = BuildCancellation.request(directory);
    }

    // Act
    var observed = cancellation.awaitClosure(Duration.ZERO);

    // Assert
    assertFalse(observed);
    assertTrue(Files.isRegularFile(ownerDirectory.resolve("owner-closed.properties")));
    assertFalse(Files.exists(ownerDirectory.resolve("cancellation-observed.properties")));
  }

  @Test
  void givenUnclosedReservation_whenCancellationIsObserved_thenAcknowledgementCannotSupplySuccessfulClosure() throws Exception {
    // Arrange
    var lease = BuildLease.acquire(request());
    var reservation = BuildJournal.reserve(lease.context(), "command-launcher");
    var cancellation = BuildCancellation.request(directory);

    // Act
    try {
      assertThrows(Requested.class, () -> BuildCancellation.check(lease.context()));
      var closing = assertThrows(IOException.class, lease::close);
      var pending = assertThrows(IOException.class, () -> cancellation.awaitClosure(Duration.ZERO));

      // Assert
      assertTrue(closing.getMessage().contains("unresolved resources"));
      assertTrue(pending.getMessage().contains("Cancellation remains pending"));
      assertFalse(Files.exists(lease.context().directory().resolve("owner-closed.properties")));
      assertTrue(Files.isRegularFile(reservation.directory().resolve("intent.properties")));
    } finally {
      reservation.closeWithoutLaunch();
      lease.close();
    }
    assertTrue(cancellation.awaitClosure(Duration.ZERO));
  }

  @Test
  void givenCancellationForAnotherOwner_whenTheCurrentOwnerChecks_thenTheMismatchRemainsAnError() throws Exception {
    // Arrange
    try (var lease = BuildLease.acquire(request())) {
      var context = lease.context();
      var foreign = context.properties();
      foreign.setProperty("owner.started", "2000-01-01T00:00:00Z");
      BuildFiles.write(context.directory().resolve("cancellation-request.properties"), foreign);

      // Act
      var failure = assertThrows(IOException.class, () -> BuildCancellation.check(context));

      // Assert
      assertTrue(failure.getMessage().contains("exact invocation owner: owner.started"));
      assertFalse(Files.exists(context.directory().resolve("cancellation-observed.properties")));
      assertEquals("2000-01-01T00:00:00Z",
          BuildFiles.read(context.directory().resolve("cancellation-request.properties")).getProperty("owner.started"));
    }
  }

  @Test
  void givenNoActiveBuild_whenCancellationIsRequested_thenNoLeaseOrInvocationIsCreated() throws Exception {
    // Arrange
    var root = directory.toRealPath();

    // Act
    var failure = assertThrows(IOException.class, () -> BuildCancellation.request(root));

    // Assert
    assertTrue(failure.getMessage().contains("no active local build"));
    assertFalse(Files.exists(directory.resolve(".build-state")));
  }

  @Test
  void givenNoCancellationRequest_whenTheOwnerChecks_thenNormalOwnershipIsPreserved() throws Exception {
    // Arrange
    try (var lease = BuildLease.acquire(request())) {
      var context = lease.context();

      // Act
      BuildCancellation.check(context);

      // Assert
      ActiveLease.require(context);
      assertFalse(Files.exists(context.directory().resolve("cancellation-observed.properties")));
      assertFalse(Files.exists(context.directory().resolve("owner-closed.properties")));
    }
  }

  @Test
  void givenReleasedOriginalLeaseAndLiveSuccessor_whenCancellationClosureIsRead_thenTheSuccessorIsPreserved() throws Exception {
    // Arrange
    BuildCancellation cancellation;
    try (var lease = BuildLease.acquire(request())) {
      cancellation = BuildCancellation.request(directory);
      assertThrows(Requested.class, () -> BuildCancellation.check(lease.context()));
    }
    try (var successor = BuildLease.acquire(request())) {
      // Act
      var observed = cancellation.awaitClosure(Duration.ZERO);

      // Assert
      assertTrue(observed);
      ActiveLease.require(successor.context());
      assertFalse(Files.exists(successor.context().directory().resolve("cancellation-request.properties")));
    }
  }

  @Test
  void givenForeignAcknowledgementAfterLeaseRelease_whenClosureIsRead_thenCancellationCannotBeMisreported() throws Exception {
    // Arrange
    BuildCancellation cancellation;
    try (var lease = BuildLease.acquire(request())) {
      cancellation = BuildCancellation.request(directory);
      var foreign = lease.context().properties();
      foreign.setProperty("nonce", UUID.randomUUID().toString());
      BuildFiles.write(lease.context().directory().resolve("cancellation-observed.properties"), foreign);
    }

    // Act
    var failure = assertThrows(IOException.class, () -> cancellation.awaitClosure(Duration.ZERO));

    // Assert
    assertTrue(failure.getMessage().contains("exact invocation owner: nonce"));
    assertFalse(Files.exists(directory.resolve(".build-state/active-owner.properties")));
  }

  @Test
  void givenDeadRecordedOwner_whenCancellationIsRequested_thenItsReservationIsPreservedWithoutInventingAcknowledgement() throws Exception {
    // Arrange
    var root = directory.toRealPath();
    var nonce = UUID.randomUUID().toString();
    var context = new InvocationContext(root, root.resolve(".build-state/invocations").resolve(nonce), root, nonce,
        new ProcessIdentity(Long.MAX_VALUE, Instant.parse("2000-01-01T00:00:00Z")), ProcessIdentity.namespace());
    BuildFiles.write(context.directory().resolve("invocation.properties"), context.properties());
    var active = root.resolve(".build-state/active-owner.properties");
    BuildFiles.write(active, context.properties());

    // Act
    var failure = assertThrows(IOException.class, () -> BuildCancellation.request(root));

    // Assert
    assertTrue(failure.getMessage().contains("no longer alive"));
    assertEquals(nonce, BuildFiles.read(active).getProperty("nonce"));
    assertFalse(Files.exists(context.directory().resolve("cancellation-request.properties")));
    assertFalse(Files.exists(context.directory().resolve("cancellation-observed.properties")));
  }

  private BuildRequest request() throws IOException {
    var root = directory.toRealPath();
    return new BuildRequest(root, root, root.resolve("absent-wrapper"), BuildOperation.COMPILE, List.of());
  }
}
