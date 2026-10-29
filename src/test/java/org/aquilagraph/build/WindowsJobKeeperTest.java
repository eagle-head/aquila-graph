package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsJobKeeperTest {
  @TempDir Path directory;

  @Test
  void givenIdentityCorrectButIncompleteOrContradictoryClosure_whenTheKeeperRuns_thenEachRecordIsRefused() throws Exception {
    // Arrange
    var command = command(true);
    for (var field : List.of("active.members", "closure.evidence", "observer.pid", "reservation")) {
      command.containedClosed();
      var record = BuildFiles.read(command.directory().resolve("contained-closed.properties"));
      record.setProperty(field, field.equals("active.members") ? "1" : "invalid");
      BuildFiles.write(command.directory().resolve("contained-closed.properties"), record);
      var runtime = new JobRuntime(false, 0);

      // Act
      try (runtime) {
        assertThrows(IOException.class, () -> WindowsJobKeeper.hold(command.invocationContext(), command, runtime.job));
      }

      // Assert
      assertFalse(runtime.terminated);
      assertTrue(runtime.handleClosed);
      assertEquals(record, BuildFiles.read(command.directory().resolve("contained-closed.properties")));
    }
    command.containedClosed();
    var record = BuildFiles.read(command.directory().resolve("contained-closed.properties"));
    record.remove("active.members");
    BuildFiles.write(command.directory().resolve("contained-closed.properties"), record);
    try (var runtime = new JobRuntime(false, 0)) {
      assertThrows(IOException.class, () -> WindowsJobKeeper.hold(command.invocationContext(), command, runtime.job));
    }
  }

  @Test
  void givenDeadBuildOwnerAndRemainingNativeMembers_whenTheKeeperRuns_thenClosureIsObservedWithoutARebootOrCancelRequest()
      throws Exception {
    // Arrange
    var command = command(false);
    var runtime = new JobRuntime(false, 2, 0);

    // Act
    try (runtime) {
      WindowsJobKeeper.hold(command.invocationContext(), command, runtime.job);
    }

    // Assert
    var closure = BuildFiles.read(command.directory().resolve("contained-closed.properties"));
    command.invocationContext().requireIdentity(closure);
    assertEquals("0", closure.getProperty("active.members"));
    assertEquals("native-group-closed", closure.getProperty("closure.evidence"));
    assertTrue(runtime.terminated);
    assertEquals(0, runtime.lastMembers);
    assertTrue(runtime.handleClosed);
    assertFalse(Files.exists(command.invocationContext().directory().resolve("cancellation-request.properties")));
    assertFalse(Files.exists(command.invocationContext().directory().resolve("recovery-host.properties")));
    assertFalse(Files.exists(command.directory().resolve("closed.properties")));
  }

  @Test
  void givenAlreadyClosedGroupAndLiveOwner_whenTheKeeperRuns_thenTheExistingClosureIsPreservedAndTheHandleIsReleased()
      throws Exception {
    // Arrange
    var command = command(true);
    command.containedClosed();
    var original = Files.readString(command.directory().resolve("contained-closed.properties"));
    var runtime = new JobRuntime(false, 0);

    // Act
    try (runtime) {
      WindowsJobKeeper.hold(command.invocationContext(), command, runtime.job);
    }

    // Assert
    assertEquals(original, Files.readString(command.directory().resolve("contained-closed.properties")));
    assertFalse(runtime.terminated);
    assertTrue(runtime.handleClosed);
  }

  @Test
  void givenFailedNativeObservationAfterOwnerDeath_whenTheKeeperRuns_thenTheFailureCannotPublishClosure() throws Exception {
    // Arrange
    var command = command(false);
    var runtime = new JobRuntime(true, 1);
    IOException failure;

    // Act
    try (runtime) {
      failure = assertThrows(IOException.class, () -> WindowsJobKeeper.hold(command.invocationContext(), command, runtime.job));
    }

    // Assert
    assertTrue(failure.getMessage().contains("QueryInformationJobObject failed with Windows error 5"));
    assertTrue(runtime.terminated);
    assertTrue(runtime.handleClosed);
    assertFalse(Files.exists(command.directory().resolve("contained-closed.properties")));
    assertTrue(Files.isRegularFile(command.directory().resolve("intent.properties")));
  }

  @Test
  void givenForeignClosureRecord_whenTheKeeperReadsIt_thenItCannotBeAcceptedAsTheOwnedGroupCompletion() throws Exception {
    // Arrange
    var command = command(true);
    var foreign = command.invocationContext().properties();
    foreign.setProperty("nonce", UUID.randomUUID().toString());
    BuildFiles.write(command.directory().resolve("contained-closed.properties"), foreign);
    var runtime = new JobRuntime(false, 0);
    IOException failure;

    // Act
    try (runtime) {
      failure = assertThrows(IOException.class, () -> WindowsJobKeeper.hold(command.invocationContext(), command, runtime.job));
    }

    // Assert
    assertTrue(failure.getMessage().contains("exact invocation owner: nonce"));
    assertEquals(foreign, BuildFiles.read(command.directory().resolve("contained-closed.properties")));
    assertFalse(runtime.terminated);
    assertTrue(runtime.handleClosed);
  }

  @Test
  void givenOwnedKeeperAcknowledgement_whenValidated_thenTheHolderIdentityAndCompleteReservationArePreserved() throws Exception {
    // Arrange
    var command = command(true);
    var context = command.invocationContext();
    var keeper = BuildJournal.reserve(context, "job-keeper");
    var launcher = new ProcessIdentity(321, Instant.parse("2026-01-01T00:00:00Z"));
    var acknowledgement = acknowledgement(keeper, launcher);

    // Act
    var holder = WindowsJobKeeper.acknowledgement(context, keeper, acknowledgement, launcher);

    // Assert
    assertEquals(ProcessIdentity.observe(ProcessHandle.current()), holder);
    var intent = BuildFiles.read(keeper.directory().resolve("intent.properties"));
    assertEquals("job-keeper", intent.getProperty("kind"));
    assertEquals("java,java.exe,cmd,cmd.exe", intent.getProperty("intended.images"));
    assertEquals(context.entry("WindowsJobKeeper").toString(), intent.getProperty("intended.entry"));
    assertFalse(Files.exists(keeper.directory().resolve("closed.properties")));
  }

  @Test
  void givenOneChangedAcknowledgementField_whenValidated_thenEachOwnershipContradictionIsRejectedIndependently() throws Exception {
    // Arrange
    var command = command(true);
    var context = command.invocationContext();
    var keeper = BuildJournal.reserve(context, "job-keeper");
    var launcher = new ProcessIdentity(321, Instant.parse("2026-01-01T00:00:00Z"));
    var original = acknowledgement(keeper, launcher);

    // Act
    for (var field : List.of("nonce", "reservation", "launcher.pid", "launcher.started", "account")) {
      var changed = new Properties();
      changed.putAll(original);
      changed.setProperty(field, "foreign");
      var failure = assertThrows(IOException.class, () -> WindowsJobKeeper.acknowledgement(context, keeper, changed, launcher));

      // Assert
      assertTrue(failure.getMessage().contains("differs"));
      assertFalse(Files.exists(keeper.directory().resolve("acknowledged.properties")));
      assertFalse(Files.exists(keeper.directory().resolve("closed.properties")));
    }
    assertEquals(ProcessIdentity.observe(ProcessHandle.current()),
        WindowsJobKeeper.acknowledgement(context, keeper, original, launcher));
  }

  @Test
  void givenMalformedBrokerCreationRecords_whenDecoded_thenNoPartialOrOutOfRangeIdentityCanBeAdmitted() {
    // Arrange
    var records = List.of("", "12", "0\t2026-01-01T00:00:00Z", "-1\t2026-01-01T00:00:00Z",
        "4294967296\t2026-01-01T00:00:00Z", "12\tinvalid", "12\t2026-01-01T00:00:00Z\n13\t2026-01-01T00:00:00Z");

    // Act
    for (var record : records) {
      var failure = assertThrows(IOException.class, () -> WindowsProcessBroker.creation(record));

      // Assert
      assertTrue(failure.getMessage().contains("Recovery holder"));
    }
  }

  @Test
  void givenNativeUnsignedPidAndSubmillisecondStart_whenDecoded_thenTheJavaProcessIdentityRemainsExact() throws Exception {
    // Arrange
    var record = "4294967295\t2026-01-01T00:00:00.1234567Z";

    // Act
    var identity = WindowsProcessBroker.creation(record);

    // Assert
    assertEquals(new ProcessIdentity(0xffff_ffffL, Instant.parse("2026-01-01T00:00:00.123Z")), identity);
  }

  private BuildJournal command(boolean liveOwner) throws Exception {
    var root = directory.toRealPath();
    var nonce = UUID.randomUUID().toString();
    var owner = liveOwner ? ProcessIdentity.observe(ProcessHandle.current())
        : new ProcessIdentity(Long.MAX_VALUE, Instant.parse("2000-01-01T00:00:00Z"));
    var context = new InvocationContext(root, root.resolve(".build-state/invocations").resolve(nonce), root, nonce, owner,
        ProcessIdentity.namespace());
    var record = context.properties();
    record.setProperty("maven", root.resolve("mvnw").toString());
    BuildFiles.write(context.directory().resolve("invocation.properties"), record);
    return BuildJournal.reserve(context, "maven-wrapper");
  }

  private static Properties acknowledgement(BuildJournal journal, ProcessIdentity launcher) throws Exception {
    var record = journal.acknowledgement();
    record.setProperty("launcher.pid", Long.toString(launcher.pid()));
    record.setProperty("launcher.started", launcher.started().toString());
    record.setProperty("account", BuildFiles.read(journal.directory().resolve("intent.properties")).getProperty("account"));
    return record;
  }

  private static final class JobRuntime implements AutoCloseable {
    private final Arena arena = Arena.ofConfined();
    private final WindowsJobObject job;
    private boolean terminated;
    private boolean handleClosed;
    private int lastMembers = -1;

    private JobRuntime(boolean observationFailure, Integer... counts) {
      var members = new ArrayDeque<>(List.of(counts));
      var state = MemoryLayout.structLayout(ValueLayout.JAVA_INT.withName("GetLastError"));
      var functions = new HashMap<String, NativeCall>();
      functions.put("TerminateJobObject", arguments -> {
        terminated = true;
        return Optional.of(1);
      });
      functions.put("QueryInformationJobObject", arguments -> {
        if (observationFailure) {
          ((MemorySegment) arguments[0]).set(ValueLayout.JAVA_INT, 0, 5);
          return Optional.of(0);
        }
        lastMembers = members.removeFirst();
        ((MemorySegment) arguments[3]).set(ValueLayout.JAVA_INT, 40, lastMembers);
        return Optional.of(1);
      });
      functions.put("CloseHandle", arguments -> {
        handleClosed = true;
        return Optional.of(1);
      });
      job = new WindowsJobObject(MemorySegment.ofAddress(1), new WindowsKernel(arena, state, Map.copyOf(functions)));
    }

    @Override
    public void close() throws IOException {
      try (arena) {
        job.close();
      }
    }
  }
}
