package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsBootSessionTest {
  private static final Instant ORIGINAL_BOOT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant OWNER_START = Instant.parse("2026-01-01T01:00:00Z");
  private static final Instant NEXT_BOOT = Instant.parse("2026-01-02T00:00:00Z");
  private static final long ORIGINAL_UPTIME = 3_600_000L;
  private static final long RESET_UPTIME = 1_000L;
  @TempDir Path directory;

  @Test
  void givenRecordedHostAndLaterBoot_whenRestartIsReconciled_thenTheEvidenceRetainsItsDistinctOrigin() throws Exception {
    // Arrange
    var journal = journal(true);
    var next = new WindowsBootSession("WORKSTATION", NEXT_BOOT, RESET_UPTIME);

    // Act
    var accepted = WindowsBootSession.reconcile(journal, next).orElseThrow();
    journal.closeAfterRestart(accepted);

    // Assert
    var evidence = BuildFiles.read(journal.directory().resolve("closed.properties"));
    assertEquals("host-restarted", evidence.getProperty("closure.evidence"));
    assertEquals(NEXT_BOOT.toString(), evidence.getProperty("boot.started"));
    assertEquals("WORKSTATION", evidence.getProperty("computer.name"));
    assertFalse(evidence.containsKey("active.members"));
    assertTrue(Files.exists(journal.directory().resolve("closed.properties")));
  }

  @Test
  void givenNondecreasingNativeUptimeDespiteChangedClock_whenReconciled_thenNoRestartIsInferred() throws Exception {
    // Arrange
    var journal = journal(true);
    var observations = new WindowsBootSession[]{new WindowsBootSession("WORKSTATION", ORIGINAL_BOOT, ORIGINAL_UPTIME),
        new WindowsBootSession("WORKSTATION", ORIGINAL_BOOT.minusSeconds(1), ORIGINAL_UPTIME + 1),
        new WindowsBootSession("WORKSTATION", NEXT_BOOT, ORIGINAL_UPTIME + 2)};

    // Act
    for (var observation : observations) {
      var restarted = WindowsBootSession.reconcile(journal, observation);

      // Assert
      assertTrue(restarted.isEmpty());
      assertFalse(Files.exists(journal.directory().resolve("closed.properties")));
      assertTrue(Files.exists(journal.directory().resolve("intent.properties")));
    }
  }

  @Test
  void givenResetUptimeOnAnotherComputer_whenReconciled_thenForeignOwnershipCannotAuthorizeClosure() throws Exception {
    // Arrange
    var journal = journal(true);
    var foreign = new WindowsBootSession("OTHER-HOST", NEXT_BOOT, RESET_UPTIME);

    // Act
    var failure = assertThrows(IOException.class, () -> WindowsBootSession.reconcile(journal, foreign));

    // Assert
    assertTrue(failure.getMessage().contains("another Windows computer"));
    assertFalse(Files.exists(journal.directory().resolve("closed.properties")));
  }

  @Test
  void givenHistoricalLocalAccountWithoutHostRecord_whenALaterBootIsObserved_thenHistoricalOwnershipCanBeReconciled() throws Exception {
    // Arrange
    var journal = journal(false);
    assertTrue(WindowsBootSession.reconcile(journal,
        new WindowsBootSession("WORKSTATION", ORIGINAL_BOOT, ORIGINAL_UPTIME)).isEmpty());
    var next = new WindowsBootSession("WORKSTATION", NEXT_BOOT, RESET_UPTIME);

    // Act
    var accepted = WindowsBootSession.reconcile(journal, next).orElseThrow();
    journal.closeAfterRestart(accepted);

    // Assert
    assertEquals("host-restarted", BuildFiles.read(journal.directory().resolve("closed.properties")).getProperty("closure.evidence"));
    assertFalse(Files.exists(journal.invocationContext().directory().resolve("host.properties")));
    assertFalse(Files.exists(journal.directory().resolve("contained-closed.properties")));
  }

  @Test
  void givenHistoricalOwnerFromTheCurrentBoot_whenHandlesAreLost_thenTheOriginalRefusalRemainsNecessary() throws Exception {
    // Arrange
    var journal = journal(false);
    var current = new WindowsBootSession("WORKSTATION", ORIGINAL_BOOT, ORIGINAL_UPTIME);

    // Act
    var restarted = WindowsBootSession.reconcile(journal, current);

    // Assert
    assertTrue(restarted.isEmpty());
    assertEquals("unresolved-resource-recovery", BuildFiles.read(journal.invocationContext().directory()
        .resolve("recovery-host.properties")).getProperty("observation.purpose"));
    assertFalse(Files.exists(journal.directory().resolve("contained-closed.properties")));
  }

  @Test
  void givenHistoricalDomainAccountWithoutHostIdentity_whenALaterBootIsSupplied_thenForeignOwnershipCannotBeAssumedLocal() throws Exception {
    // Arrange
    var journal = journal(false);
    var file = journal.directory().resolve("intent.properties");
    var intent = BuildFiles.read(file);
    intent.setProperty("account", "DOMAIN\\user");
    BuildFiles.write(file, intent);

    // Act
    var failure = assertThrows(IOException.class,
        () -> WindowsBootSession.reconcile(journal, new WindowsBootSession("WORKSTATION", NEXT_BOOT, RESET_UPTIME)));

    // Assert
    assertTrue(failure.getMessage().contains("cannot be associated with this computer"));
    assertEquals("DOMAIN\\user", BuildFiles.read(file).getProperty("account"));
  }

  @Test
  void givenAHostRecordForAnotherInvocation_whenRestartIsReconciled_thenTheMismatchCannotAuthorizeClosure() throws Exception {
    // Arrange
    var journal = journal(true);
    var file = journal.invocationContext().directory().resolve("host.properties");
    var foreign = BuildFiles.read(file);
    foreign.setProperty("nonce", UUID.randomUUID().toString());
    BuildFiles.write(file, foreign);

    // Act
    var failure = assertThrows(IOException.class,
        () -> WindowsBootSession.reconcile(journal, new WindowsBootSession("WORKSTATION", NEXT_BOOT, RESET_UPTIME)));

    // Assert
    assertTrue(failure.getMessage().contains("exact invocation owner: nonce"));
    assertFalse(Files.exists(journal.directory().resolve("contained-closed.properties")));
  }

  @Test
  void givenRestartEvidenceForOneInvocation_whenAnotherReservationUsesIt_thenNoForeignClosureIsWritten() throws Exception {
    // Arrange
    var first = journal(true);
    var other = journal(true);
    var restarted = WindowsBootSession.reconcile(first, new WindowsBootSession("WORKSTATION", NEXT_BOOT, RESET_UPTIME)).orElseThrow();

    // Act
    var failure = assertThrows(IOException.class, () -> other.closeAfterRestart(restarted));

    // Assert
    assertTrue(failure.getMessage().contains("exact invocation owner: nonce"));
    assertFalse(Files.exists(other.directory().resolve("closed.properties")));
  }

  @Test
  void givenUnknownResourceKind_whenRestartClosureIsRequested_thenOnlyKnownNativeProcessResourcesCanClose() throws Exception {
    // Arrange
    var journal = journal(true);
    var restarted = WindowsBootSession.reconcile(journal, new WindowsBootSession("WORKSTATION", NEXT_BOOT, RESET_UPTIME)).orElseThrow();
    var file = journal.directory().resolve("intent.properties");
    var intent = BuildFiles.read(file);
    intent.setProperty("kind", "external-resource");
    BuildFiles.write(file, intent);

    // Act
    var failure = assertThrows(IOException.class, () -> journal.closeAfterRestart(restarted));

    // Assert
    assertTrue(failure.getMessage().contains("unrecognized resource"));
    assertFalse(Files.exists(journal.directory().resolve("closed.properties")));
    assertEquals("external-resource", BuildFiles.read(file).getProperty("kind"));
  }

  @Test
  void givenKnownWindowsJobAndResetUptime_whenRecovered_thenNativeClosureIsRecordedWithoutInventingAMemberCount() throws Exception {
    // Arrange
    var journal = journal(true);
    var file = journal.directory().resolve("intent.properties");
    var intent = BuildFiles.read(file);
    intent.setProperty("kind", "maven-wrapper");
    intent.setProperty("containment.kind", "windows-job");
    BuildFiles.write(file, intent);
    journal.containedStart();
    var restarted = WindowsBootSession.reconcile(journal,
        new WindowsBootSession("WORKSTATION", NEXT_BOOT, RESET_UPTIME)).orElseThrow();

    // Act
    journal.closeAfterRestart(restarted);

    // Assert
    var nativeClosure = BuildFiles.read(journal.directory().resolve("contained-closed.properties"));
    assertEquals("host-restarted", nativeClosure.getProperty("closure.evidence"));
    assertFalse(nativeClosure.containsKey("active.members"));
    assertEquals(Long.toString(RESET_UPTIME), nativeClosure.getProperty("uptime.millis"));
    assertTrue(Files.isRegularFile(journal.directory().resolve("contained-start.properties")));
    assertEquals("host-restarted", BuildFiles.read(journal.directory().resolve("closed.properties")).getProperty("closure.evidence"));
  }

  @Test
  void givenMalformedRecordedUptime_whenReconciled_thenTheRecordIsPreservedAndNoFallbackProofIsCreated() throws Exception {
    // Arrange
    var journal = journal(true);
    var file = journal.invocationContext().directory().resolve("host.properties");
    var host = BuildFiles.read(file);
    host.setProperty("uptime.millis", "invalid");
    BuildFiles.write(file, host);

    // Act
    var failure = assertThrows(IOException.class,
        () -> WindowsBootSession.reconcile(journal, new WindowsBootSession("WORKSTATION", NEXT_BOOT, RESET_UPTIME)));

    // Assert
    assertTrue(failure.getMessage().contains("native uptime is invalid"));
    assertEquals("invalid", BuildFiles.read(file).getProperty("uptime.millis"));
    assertFalse(Files.exists(journal.invocationContext().directory().resolve("recovery-host.properties")));
    assertFalse(Files.exists(journal.directory().resolve("closed.properties")));
  }

  @Test
  void givenMissingMalformedOrMultipleBootRecords_whenParsed_thenTheyCannotBecomeRestartEvidence() {
    // Arrange
    var outputs = new String[]{"", "WORKSTATION", "\t2026-01-01T00:00:00Z", "WORKSTATION\tinvalid",
        "WORKSTATION\t2026-01-01T00:00:00Z\nOTHER\t2026-01-02T00:00:00Z"};

    // Act
    for (var output : outputs) {
      var failure = assertThrows(IOException.class, () -> WindowsBootSession.parse(output, ORIGINAL_UPTIME));

      // Assert
      assertTrue(failure.getMessage().contains("Windows boot observation"));
    }
  }

  private BuildJournal journal(boolean recordedHost) throws Exception {
    var root = directory.toRealPath();
    var nonce = UUID.randomUUID().toString();
    var context = new InvocationContext(root, root.resolve(".build-state/invocations").resolve(nonce), root, nonce,
        new ProcessIdentity(Long.MAX_VALUE, OWNER_START), "Windows 11");
    BuildFiles.write(context.directory().resolve("invocation.properties"), context.properties());
    var journal = BuildJournal.reserve(context, "command-launcher");
    var file = journal.directory().resolve("intent.properties");
    var intent = BuildFiles.read(file);
    intent.setProperty("created", OWNER_START.plusSeconds(1).toString());
    intent.setProperty("account", "WORKSTATION\\user");
    BuildFiles.write(file, intent);
    if (recordedHost) {
      var host = context.properties();
      new WindowsBootSession("WORKSTATION", ORIGINAL_BOOT, ORIGINAL_UPTIME).record(host);
      BuildFiles.write(context.directory().resolve("host.properties"), host);
    }
    return journal;
  }
}
