package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildJournalClosureTest {
  @TempDir Path directory;

  @Test
  void givenEqualPidAndStartAcrossNamespaces_whenCompletingTheParent_thenOnlyItsActualObservationCanBlockClosure() throws Exception {
    // Arrange
    var parent = context();
    var historical = InvocationContext.observation(parent.properties());
    var historicalJournal = BuildJournal.reserve(historical, "process-observation");
    new NativeObservation(historical, historicalJournal).close();
    for (var file : List.of(historical.directory().resolve("invocation.properties"),
        historical.directory().resolve("owner-closed.properties"), historicalJournal.directory().resolve("intent.properties"),
        historicalJournal.directory().resolve("closed.properties"))) {
      var record = BuildFiles.read(file);
      record.setProperty("owner.namespace", "foreign-process-namespace");
      record.setProperty("root", "/historical/project");
      record.setProperty("tool.root", "/historical/tools");
      BuildFiles.write(file, record);
    }

    // Act
    BuildJournal.recover(parent.root());
    BuildJournal.requireClosed(parent);
    var actual = InvocationContext.observation(parent.properties());
    var actualJournal = BuildJournal.reserve(actual, "process-observation");

    // Assert
    assertEquals(parent.owner(), historical.owner());
    assertEquals(parent.owner(), actual.owner());
    assertThrows(IOException.class, () -> BuildJournal.requireClosed(parent));
    assertFalse(Files.exists(actual.directory().resolve("owner-closed.properties")));
    new NativeObservation(actual, actualJournal).close();
    BuildJournal.requireClosed(parent);
    assertTrue(actualJournal.closed());
  }

  @Test
  void givenInvalidNativeInvocationMarkers_whenCompletingCleaningOrRecovering_thenEveryReaderRefusesTheSameInvalidEvidence() throws Exception {
    // Arrange
    var context = context();
    var invocation = context.properties();
    invocation.setProperty("containment.invocation.slice", "aquila-" + context.nonce().replace("-", "") + ".slice");
    BuildFiles.write(context.directory().resolve("invocation.properties"), invocation);
    Files.createDirectories(context.directory().resolve("resources"));
    var file = context.directory().resolve("native-closed.properties");
    for (var variant : List.of("directory", "malformed", "foreign")) {
      BuildFiles.write(file, context.properties());
      BuildJournal.requireClosed(context);
      LinuxScope.closeInvocation(context);
      BuildJournal.recover(context.root());
      if (variant.equals("directory")) {
        Files.delete(file);
        Files.createDirectory(file);
      } else {
        var record = variant.equals("malformed") ? new Properties() : context.properties();
        if (variant.equals("foreign")) {
          record.setProperty("nonce", UUID.randomUUID().toString());
        }
        BuildFiles.write(file, record);
      }

      // Act
      assertThrows(IOException.class, () -> BuildJournal.requireClosed(context));
      assertThrows(IOException.class, () -> LinuxScope.closeInvocation(context));
      assertThrows(IOException.class, () -> BuildJournal.recover(context.root()));

      // Assert
      assertTrue(Files.exists(file));
      assertFalse(Files.exists(context.directory().resolve("owner-closed.properties")));
      Files.delete(file);
    }
    assertFalse(LinuxScope.invocationClosed(context.directory(), invocation));
    assertThrows(IOException.class, () -> BuildJournal.requireClosed(context));
  }

  @Test
  void givenForeignRestartHistory_whenRecoveryReadsIt_thenStoredIdentityAndUptimeRemainAuthoritative() throws Exception {
    // Arrange
    var context = context();
    var ownership = context.properties();
    ownership.setProperty("owner.namespace", "foreign-windows-namespace");
    ownership.setProperty("root", "/historical/project");
    ownership.setProperty("tool.root", "/historical/tools");
    BuildFiles.write(context.directory().resolve("invocation.properties"), ownership);
    var baseline = new Properties();
    baseline.putAll(ownership);
    new WindowsBootSession("historical-computer", Instant.parse("2026-01-01T00:00:00Z"), 10000).record(baseline);
    BuildFiles.write(context.directory().resolve("host.properties"), baseline);
    var reservation = UUID.randomUUID().toString();
    var resource = context.directory().resolve("resources").resolve(reservation);
    var intent = new Properties();
    intent.putAll(ownership);
    intent.setProperty("reservation", reservation);
    intent.setProperty("kind", "maven-wrapper");
    intent.setProperty("containment.kind", "windows-job");
    BuildFiles.write(resource.resolve("intent.properties"), intent);
    var closure = new Properties();
    closure.putAll(ownership);
    closure.setProperty("reservation", reservation);
    closure.setProperty("closure.evidence", "host-restarted");
    closure.setProperty("observer.pid", Long.toString(context.owner().pid()));
    closure.setProperty("observer.started", context.owner().started().toString());
    new WindowsBootSession("historical-computer", Instant.parse("2026-01-02T00:00:00Z"), 100).record(closure);
    BuildFiles.write(resource.resolve("contained-closed.properties"), closure);
    closure.setProperty("closed", Instant.now().toString());
    closure.setProperty("reason", "native processes belong to a verified earlier host boot");
    BuildFiles.write(resource.resolve("closed.properties"), closure);

    // Act
    BuildJournal.recover(context.root());

    // Assert
    assertEquals(ownership, BuildFiles.read(context.directory().resolve("invocation.properties")));
    assertEquals(baseline, BuildFiles.read(context.directory().resolve("host.properties")));
    assertEquals(closure, BuildFiles.read(resource.resolve("closed.properties")));
    closure.setProperty("active.members", "0");
    BuildFiles.write(resource.resolve("closed.properties"), closure);
    assertThrows(IOException.class, () -> BuildJournal.recover(context.root()));
    assertEquals(closure, BuildFiles.read(resource.resolve("closed.properties")));
  }

  @Test
  void givenClosedPreNamespaceRecords_whenRecoveryReadsThem_thenOriginalDeclarationsArePreservedWithoutNewNativeEvidence() throws Exception {
    // Arrange
    var context = context();
    var invocation = context.properties();
    invocation.remove("owner.namespace");
    invocation.remove("owner.platform");
    BuildFiles.write(context.directory().resolve("invocation.properties"), invocation);
    var resource = context.directory().resolve("resources").resolve(UUID.randomUUID().toString());
    var intent = new Properties();
    intent.putAll(invocation);
    intent.setProperty("reservation", resource.getFileName().toString());
    intent.setProperty("kind", "maven-launcher");
    BuildFiles.write(resource.resolve("intent.properties"), intent);
    var closure = new Properties();
    closure.putAll(invocation);
    closure.setProperty("closed", Instant.now().toString());
    closure.setProperty("reason", "native containment and all recorded process identities were observed closed");
    BuildFiles.write(resource.resolve("closed.properties"), closure);

    // Act
    BuildJournal.recover(context.root());

    // Assert
    assertEquals(closure, BuildFiles.read(resource.resolve("closed.properties")));
    assertFalse(Files.exists(resource.resolve("contained-closed.properties")));
    Files.delete(resource.resolve("closed.properties"));
    assertThrows(IOException.class, () -> BuildJournal.recover(context.root()));
    assertFalse(Files.exists(resource.resolve("closed.properties")));
  }

  @Test
  void givenClosedForeignNamespaceHistory_whenRecoveryReadsIt_thenPathsRemainDataAndUnclosedResourcesAreRefused() throws Exception {
    // Arrange
    var context = context();
    var invocation = context.properties();
    invocation.setProperty("owner.namespace", "foreign-process-namespace");
    invocation.setProperty("root", "/historical/project");
    invocation.setProperty("tool.root", "/historical/tools");
    BuildFiles.write(context.directory().resolve("invocation.properties"), invocation);
    var resource = context.directory().resolve("resources").resolve(UUID.randomUUID().toString());
    var intent = new Properties();
    intent.putAll(invocation);
    intent.setProperty("reservation", resource.getFileName().toString());
    intent.setProperty("kind", "process-observation");
    BuildFiles.write(resource.resolve("intent.properties"), intent);
    var closure = new Properties();
    closure.putAll(invocation);
    closure.setProperty("closed", Instant.now().toString());
    closure.setProperty("reason", "all recorded process identities were observed closed");
    BuildFiles.write(resource.resolve("closed.properties"), closure);

    // Act
    BuildJournal.recover(context.root());

    // Assert
    assertEquals(invocation, BuildFiles.read(context.directory().resolve("invocation.properties")));
    Files.delete(resource.resolve("closed.properties"));
    assertThrows(IOException.class, () -> BuildJournal.recover(context.root()));
    assertFalse(Files.exists(resource.resolve("closed.properties")));
  }

  @Test
  void givenMalformedExistingClosure_whenClosingReleasingOrRecovering_thenMarkerExistenceCannotGrantAuthority() throws Exception {
    // Arrange
    var context = context();
    var journal = BuildJournal.reserve(context, "command-launcher");
    var file = journal.directory().resolve("closed.properties");
    BuildFiles.write(file, context.properties());

    // Act
    assertThrows(IOException.class, journal::closeObserved);
    assertThrows(IOException.class, () -> BuildJournal.requireClosed(context));
    assertThrows(IOException.class, () -> BuildJournal.recover(context.root()));

    // Assert
    assertEquals(context.properties(), BuildFiles.read(file));
    assertFalse(Files.exists(context.directory().resolve("owner-closed.properties")));
  }

  @Test
  void givenDirectoryAtClosurePath_whenReleasingOrRecovering_thenItRemainsAnObservableError() throws Exception {
    // Arrange
    var context = context();
    var journal = BuildJournal.reserve(context, "command-launcher");
    var file = Files.createDirectory(journal.directory().resolve("closed.properties"));

    // Act
    assertThrows(IOException.class, journal::closeObserved);
    assertThrows(IOException.class, () -> BuildJournal.requireClosed(context));
    assertThrows(IOException.class, () -> BuildJournal.recover(context.root()));

    // Assert
    assertTrue(Files.isDirectory(file));
  }

  @Test
  void givenUnknownKindOrContainment_whenClosingWithoutRestart_thenNeitherCanBeRelabeledAsSuccessfulClosure() throws Exception {
    // Arrange
    var context = context();
    for (var field : List.of("kind", "containment.kind")) {
      var journal = BuildJournal.reserve(context, "git-inspection");
      var file = journal.directory().resolve("intent.properties");
      var intent = BuildFiles.read(file);
      intent.setProperty(field, "unknown");
      BuildFiles.write(file, intent);

      // Act
      assertThrows(IOException.class, journal::closeObserved);

      // Assert
      assertEquals("unknown", BuildFiles.read(file).getProperty(field));
      assertFalse(Files.exists(journal.directory().resolve("closed.properties")));
    }
  }

  @Test
  void givenNativeFinalClosureWithoutGroupEvidence_whenReleasing_thenTheFinalMarkerCannotReplaceNativeProof() throws Exception {
    // Arrange
    var context = context();
    var journal = BuildJournal.reserve(context, "git-inspection");
    var record = context.properties();
    record.setProperty("closed", Instant.now().toString());
    record.setProperty("reason", "native containment and all recorded process identities were observed closed");
    BuildFiles.write(journal.directory().resolve("closed.properties"), record);

    // Act
    var failure = assertThrows(IOException.class, () -> BuildJournal.requireClosed(context));

    // Assert
    assertTrue(failure.getMessage().contains("lacks contained closure evidence"));
  }

  @Test
  void givenValidLegacyClosureWithoutReservationField_whenValidated_thenItsExactInvocationEvidenceRemainsReadable() throws Exception {
    // Arrange
    var context = context();
    var journal = BuildJournal.reserve(context, "command-launcher");
    journal.closeWithoutLaunch();
    var record = BuildFiles.read(journal.directory().resolve("closed.properties"));
    record.remove("reservation");
    BuildFiles.write(journal.directory().resolve("closed.properties"), record);

    // Act
    BuildJournal.requireClosed(context);

    // Assert
    assertTrue(journal.closed());
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
