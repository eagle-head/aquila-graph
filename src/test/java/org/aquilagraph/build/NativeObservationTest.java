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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeObservationTest {
  @TempDir Path directory;

  @Test
  void givenFailedCreationAndFailedJournalClosure_whenTheObservationCloses_thenTheCreationFailureRemainsPrimary() throws Exception {
    // Arrange
    var context = context();
    var journal = BuildJournal.reserve(context, "process-observation");
    var primary = new IOException("observation process creation failed");
    var observation = new NativeObservation(context, journal, builder -> { throw primary; });
    var obstruction = Files.createDirectory(journal.directory().resolve("closed.properties"));
    Files.writeString(obstruction.resolve("blocker"), "preserve the failed write");

    // Act
    var failure = assertThrows(IOException.class, () -> {
      try (observation) {
        observation.start(new ProcessBuilder("unused"));
      }
    });

    // Assert
    assertSame(primary, failure);
    assertEquals(1, failure.getSuppressed().length);
    assertInstanceOf(IOException.class, failure.getSuppressed()[0]);
    assertEquals("preserve the failed write", Files.readString(obstruction.resolve("blocker")));
    assertFalse(Files.exists(context.directory().resolve("owner-closed.properties")));
  }

  @Test
  void givenFailedIdentityPublicationAndFailedEndpoints_whenTheObservationCloses_thenTheProcessStopsAndClosureIsWithheld() throws Exception {
    // Arrange
    var context = context();
    var journal = BuildJournal.reserve(context, "process-observation");
    var process = new ProcessFailureStub(true);
    process.alive = true;
    var observation = new NativeObservation(context, journal, builder -> process);
    assertSame(process.stopFailure, assertThrows(UnsupportedOperationException.class,
        () -> observation.start(new ProcessBuilder("unused"))));

    // Act
    var failure = assertThrows(IOException.class, observation::close);

    // Assert
    assertSame(process.outputFailure, failure);
    assertEquals(List.of(process.errorFailure, process.inputFailure), List.of(failure.getSuppressed()));
    assertTrue(process.inputClosed);
    assertTrue(process.errorClosed);
    assertTrue(process.outputClosed);
    assertFalse(process.isAlive());
    assertEquals(1, process.destructions);
    assertFalse(Files.exists(journal.directory().resolve("closed.properties")));
    assertFalse(Files.exists(context.directory().resolve("owner-closed.properties")));
  }

  @Test
  void givenReturnedProcessWithoutPublishedIdentity_whenCleanupSucceeds_thenPhysicalTerminationPrecedesOwnerClosure() throws Exception {
    // Arrange
    var context = context();
    var journal = BuildJournal.reserve(context, "process-observation");
    var process = new ProcessFailureStub(false);
    process.alive = true;
    var observation = new NativeObservation(context, journal, builder -> process);

    // Act
    var failure = assertThrows(UnsupportedOperationException.class, () -> {
      try (observation) {
        observation.start(new ProcessBuilder("unused"));
      }
    });

    // Assert
    assertSame(process.stopFailure, failure);
    assertEquals(0, failure.getSuppressed().length);
    assertFalse(process.isAlive());
    assertEquals(1, process.destructions);
    assertTrue(journal.closed());
    context.requireIdentity(BuildFiles.read(context.directory().resolve("owner-closed.properties")));
  }

  @Test
  void givenUnpublishedProcessThatRefusesTermination_whenCleanupRuns_thenAnEmptyJournalCannotCertifyClosure() throws Exception {
    // Arrange
    var context = context();
    var journal = BuildJournal.reserve(context, "process-observation");
    var process = new ProcessFailureStub(false);
    process.alive = true;
    process.refuseStop = true;
    var observation = new NativeObservation(context, journal, builder -> process);
    assertThrows(UnsupportedOperationException.class, () -> observation.start(new ProcessBuilder("unused")));

    // Act
    var failure = assertThrows(IOException.class, observation::close);

    // Assert
    assertTrue(failure.getMessage().contains("remains alive"));
    assertTrue(process.isAlive());
    assertTrue(process.inputClosed && process.outputClosed && process.errorClosed);
    assertFalse(Files.exists(journal.directory().resolve("closed.properties")));
    assertFalse(Files.exists(context.directory().resolve("owner-closed.properties")));
  }

  @Test
  void givenFailedCreationAndSuccessfulCleanup_whenTheObservationCloses_thenNoCleanupFailureIsInvented() throws Exception {
    // Arrange
    var context = context();
    var journal = BuildJournal.reserve(context, "process-observation");
    var primary = new IOException("observation process creation failed");
    var observation = new NativeObservation(context, journal, builder -> { throw primary; });

    // Act
    var failure = assertThrows(IOException.class, () -> {
      try (observation) {
        observation.start(new ProcessBuilder("unused"));
      }
    });

    // Assert
    assertSame(primary, failure);
    assertEquals(0, failure.getSuppressed().length);
    context.requireIdentity(BuildFiles.read(context.directory().resolve("owner-closed.properties")));
    assertTrue(Files.isRegularFile(journal.directory().resolve("closed.properties")));
  }

  private InvocationContext context() throws IOException {
    var root = directory.toRealPath();
    var nonce = UUID.randomUUID().toString();
    var context = new InvocationContext(root, root.resolve(".build-state/invocations").resolve(nonce), root, nonce,
        ProcessIdentity.observe(ProcessHandle.current()), ProcessIdentity.namespace());
    BuildFiles.write(context.directory().resolve("invocation.properties"), context.properties());
    return context;
  }
}
