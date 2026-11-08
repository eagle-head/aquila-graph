package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.lang.ProcessHandle.Info;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Properties;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessIdentityTest {
  @TempDir Path directory;

  @Test
  void givenNativeBirthAndChangedWallClockMetadata_whenComparedAndIndexed_thenOnlyExactBirthControlsIdentity() {
    // Arrange
    var epoch = Instant.parse("2026-01-01T00:00:00Z");
    var birth = Optional.of(new LinuxProcessStart("00000000-0000-0000-0000-000000000001", 1200));
    var original = new ProcessIdentity(42, epoch, birth);
    var shifted = new ProcessIdentity(42, epoch.plusSeconds(1), birth);
    var identities = new HashSet<>(List.of(original));

    // Act
    var alreadyOwned = identities.contains(shifted);

    // Assert
    assertTrue(alreadyOwned);
    assertEquals(original, shifted);
    assertEquals(shifted, original);
    assertNotEquals(original, new ProcessIdentity(43, epoch, birth));
    assertNotEquals(original, new ProcessIdentity(42, epoch, Optional.of(new LinuxProcessStart("00000000-0000-0000-0000-000000000002", 1200))));
    assertNotEquals(original, new ProcessIdentity(42, epoch, Optional.of(new LinuxProcessStart("00000000-0000-0000-0000-000000000001", 1201))));
    assertNotEquals(original, new ProcessIdentity(42, epoch));
    assertNotEquals(new ProcessIdentity(42, epoch), original);
    assertNotEquals(new ProcessIdentity(42, epoch), new ProcessIdentity(42, epoch.plusSeconds(1)));
  }

  @Test
  void givenNativeProcessOwnership_whenWrittenAndRead_thenBirthAndOriginalWallClockMetadataRoundTrip() throws Exception {
    // Arrange
    var identity = new ProcessIdentity(42, Instant.parse("2026-01-01T00:00:00Z"), Optional.of(new LinuxProcessStart("00000000-0000-0000-0000-000000000001", 1200)));
    var record = new Properties();
    record.setProperty("unrelated", "retained");

    // Act
    identity.write(record, "owner.");
    var restored = ProcessIdentity.read(record, "owner.");

    // Assert
    assertEquals(identity, restored);
    assertEquals(identity.started(), restored.started());
    assertEquals("00000000-0000-0000-0000-000000000001", record.getProperty("owner.start.boot-id"));
    assertEquals("1200", record.getProperty("owner.start.ticks"));
    assertEquals("retained", record.getProperty("unrelated"));
  }

  @Test
  void givenPartialOrMalformedNativeBirth_whenAProcessRecordIsRead_thenNoEpochFallbackHidesTheMissingEvidence() {
    // Arrange
    var base = new Properties();
    new ProcessIdentity(42, Instant.parse("2026-01-01T00:00:00Z")).write(base, "");
    var invalid = List.of(List.of("start.boot-id", "00000000-0000-0000-0000-000000000001"), List.of("start.ticks", "1200"),
        List.of("start.boot-id", "00000000-0000-0000-0000-000000000001", "start.ticks", "-1"), List.of("start.boot-id", "", "start.ticks", "1200"),
        List.of("start.boot-id", "00000000-0000-0000-0000-000000000001", "start.ticks", "unknown"),
        List.of("start.boot-id", "invalid", "start.ticks", "1200"), List.of("start.boot-id", "1-1-1-1-1", "start.ticks", "1200"));

    // Act
    for (var fields : invalid) {
      var record = new Properties();
      record.putAll(base);
      for (var index = 0; index < fields.size(); index += 2) {
        record.setProperty(fields.get(index), fields.get(index + 1));
      }
      var failure = assertThrows(IOException.class, () -> ProcessIdentity.read(record, ""));

      // Assert
      assertTrue(failure.getMessage().contains("process") || failure.getMessage().contains("start."), failure.getMessage());
    }
  }

  @Test
  void givenNativeInvocationOwnership_whenOneRecordedIdentityFieldChanges_thenExactOwnerAdmissionRejectsIt() throws Exception {
    // Arrange
    var root = directory.toRealPath();
    var owner = new ProcessIdentity(42, Instant.parse("2026-01-01T00:00:00Z"), Optional.of(new LinuxProcessStart("00000000-0000-0000-0000-000000000001", 1200)));
    var context = new InvocationContext(root, root, root, "owned", owner, "Linux:owned");
    var expected = context.properties();
    context.requireIdentity(expected);

    // Act
    for (var field : List.of("owner.start.boot-id", "owner.start.ticks", "owner.pid", "owner.started", "nonce")) {
      var changed = new Properties();
      changed.putAll(expected);
      changed.setProperty(field, "foreign");
      var failure = assertThrows(IOException.class, () -> context.requireIdentity(changed));

      // Assert
      assertTrue(failure.getMessage().contains(field), failure.getMessage());
    }
  }

  @Test
  void givenAnEpochOnlyIdentityAndReusedProperties_whenWritten_thenLegacyIdentityCannotInheritAnotherNativeBirth() throws Exception {
    // Arrange
    var record = new Properties();
    new ProcessIdentity(42, Instant.EPOCH, Optional.of(new LinuxProcessStart("00000000-0000-0000-0000-000000000001", 1200))).write(record, "");
    var legacy = new ProcessIdentity(43, Instant.parse("2026-01-01T00:00:00Z"));

    // Act
    legacy.write(record, "");
    var restored = ProcessIdentity.read(record, "");

    // Assert
    assertEquals(legacy, restored);
    assertTrue(restored.linuxStart().isEmpty());
    assertEquals(2, record.size());
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
