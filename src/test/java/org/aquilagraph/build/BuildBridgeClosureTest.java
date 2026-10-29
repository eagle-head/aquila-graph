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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.DirectoryNotEmptyException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildBridgeClosureTest {
  @TempDir Path directory;

  @Test
  void givenConcurrentClaimRefusalAndFailedUnpublishedCleanup_whenAcquiring_thenTheRefusalRetainsItsCleanupFailure() throws Exception {
    // Arrange
    var root = directory.toRealPath();
    var request = new BuildRequest(root, root, root.resolve("mvnw"), BuildOperation.TEST, List.of());
    var refusal = new FileAlreadyExistsException("active-owner.properties");

    // Act
    var failure = assertThrows(IOException.class, () -> ActiveLease.acquire(request, (active, claim) -> {
      Files.delete(claim);
      Files.createDirectory(claim);
      Files.writeString(claim.resolve("blocker"), "preserve failed unpublished-claim cleanup");
      throw refusal;
    }));

    // Assert
    assertSame(refusal, failure.getCause());
    assertEquals(1, failure.getSuppressed().length);
    assertInstanceOf(DirectoryNotEmptyException.class, failure.getSuppressed()[0]);
    assertFalse(Files.exists(root.resolve(".build-state/active-owner.properties")));
  }

  @Test
  void givenMavenIdentityFailureAndFailedEndpoints_whenTheBridgeRuns_thenTheOriginalFailureRetainsAllCleanupFailures() throws Exception {
    // Arrange
    var context = context();
    var journal = BuildJournal.reserve(context, "maven-wrapper");
    var process = new ProcessFailureStub(true);
    process.alive = true;

    // Act
    var failure = assertThrows(UnsupportedOperationException.class,
        () -> MavenWrapper.run(journal, new ProcessBuilder("unused"), Optional.empty(), builder -> process));

    // Assert
    assertSame(process.stopFailure, failure);
    assertEquals(List.of(process.outputFailure), List.of(failure.getSuppressed()));
    assertEquals(List.of(process.errorFailure, process.inputFailure), List.of(process.outputFailure.getSuppressed()));
    assertFalse(process.isAlive());
    assertEquals(1, process.destructions);
    assertFalse(Files.exists(journal.directory().resolve("closed.properties")));
  }

  @Test
  void givenGitCreationAndJournalCleanupFailures_whenTheBridgeRuns_thenTheCreationFailureRemainsPrimary() throws Exception {
    // Arrange
    var context = context();
    var container = BuildJournal.reserve(context, "git-inspection");
    var primary = new IOException("Git creation failed");
    var inspection = new GitInspection(context, container, builder -> {
      var reservation = builder.command().get(4).substring("aquila.build.reservation=".length());
      var journal = BuildJournal.open(context, reservation);
      var obstruction = Files.createDirectory(journal.directory().resolve("closed.properties"));
      Files.writeString(obstruction.resolve("blocker"), "preserve the cleanup failure");
      throw primary;
    });

    // Act
    var failure = assertThrows(IOException.class, () -> inspection.git(List.of("status"), 0));

    // Assert
    assertSame(primary, failure);
    assertEquals(1, failure.getSuppressed().length);
    assertInstanceOf(IOException.class, failure.getSuppressed()[0]);
    assertFalse(Files.exists(context.directory().resolve("owner-closed.properties")));
  }

  @Test
  void givenGitIdentityFailureAndFailedEndpoints_whenTheBridgeRuns_thenPhysicalCleanupAndPrimaryFailureArePreserved() throws Exception {
    // Arrange
    var context = context();
    var container = BuildJournal.reserve(context, "git-inspection");
    var process = new ProcessFailureStub(true);
    process.alive = true;
    var inspection = new GitInspection(context, container, builder -> process);

    // Act
    var failure = assertThrows(UnsupportedOperationException.class, () -> inspection.git(List.of("status"), 0));

    // Assert
    assertSame(process.stopFailure, failure);
    assertEquals(List.of(process.outputFailure), List.of(failure.getSuppressed()));
    assertEquals(List.of(process.errorFailure, process.inputFailure), List.of(process.outputFailure.getSuppressed()));
    assertFalse(process.isAlive());
    assertTrue(process.inputClosed && process.outputClosed && process.errorClosed);
    assertFalse(Files.exists(context.directory().resolve("owner-closed.properties")));
  }

  private InvocationContext context() throws IOException {
    var root = directory.toRealPath();
    var nonce = UUID.randomUUID().toString();
    var context = new InvocationContext(root, root.resolve(".build-state/invocations").resolve(nonce), root, nonce,
        ProcessIdentity.observe(ProcessHandle.current()), ProcessIdentity.namespace());
    var record = context.properties();
    record.setProperty("maven", root.resolve("mvnw").toString());
    BuildFiles.write(context.directory().resolve("invocation.properties"), record);
    return context;
  }
}
