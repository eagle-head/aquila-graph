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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.aquilagraph.build.CommandPlatform.Native;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandPlatformTest {
  @TempDir Path directory;

  @Test
  void givenFailedKeeperCleanup_whenThePlatformCloses_thenTheJobIsReleasedAndPairedFailuresRetainTheirOrder() throws Exception {
    // Arrange
    for (var failJob : List.of(false, true)) {
      var context = context();
      var command = BuildJournal.reserve(context, "git-inspection");
      var keeper = WindowsJobKeeper.reserve(context, command);
      var keeperDirectory = keeperDirectory(context);
      var obstruction = Files.createDirectory(keeperDirectory.resolve("closed.properties"));
      var releases = new ArrayList<Long>();
      try (var arena = Arena.ofConfined()) {
        var job = job(arena, releases, failJob);
        var platform = new Native(context, command, Optional.of(job), Optional.of(keeper));

        // Act
        var failure = assertThrows(IOException.class, platform::close);

        // Assert
        assertTrue(failure.getMessage().contains(obstruction.toString()));
        assertEquals(List.of(1L), releases);
        assertEquals(failJob ? 1 : 0, failure.getSuppressed().length);
        if (failJob) {
          assertEquals("CloseHandle failed with Windows error 7", failure.getSuppressed()[0].getMessage());
        }
        assertTrue(Files.isDirectory(obstruction));
        assertFalse(Files.exists(command.directory().resolve("closed.properties")));
        assertFalse(Files.exists(context.directory().resolve("owner-closed.properties")));
      }
    }
  }

  @Test
  void givenCompletedKeeperAndOwnedJob_whenThePlatformCloses_thenBothResourcesCompleteWithoutAProcessLaunch() throws Exception {
    // Arrange
    var context = context();
    var command = BuildJournal.reserve(context, "git-inspection");
    var keeper = WindowsJobKeeper.reserve(context, command);
    BuildJournal.open(context, keeperDirectory(context).getFileName().toString()).closeWithoutLaunch();
    var releases = new ArrayList<Long>();
    try (var arena = Arena.ofConfined()) {
      var platform = new Native(context, command, Optional.of(job(arena, releases, false)), Optional.of(keeper));

      // Act
      platform.close();
      platform.close();

      // Assert
      assertEquals(List.of(1L), releases);
      assertFalse(Files.exists(context.root().resolve("target/build-observation")));
    }
  }

  private InvocationContext context() throws IOException {
    var root = directory.toRealPath();
    var nonce = UUID.randomUUID().toString();
    var context = new InvocationContext(root, root.resolve(".build-state/invocations").resolve(nonce), root, nonce,
        ProcessIdentity.observe(ProcessHandle.current()), ProcessIdentity.namespace());
    BuildFiles.write(context.directory().resolve("invocation.properties"), context.properties());
    return context;
  }

  private static Path keeperDirectory(InvocationContext context) throws IOException {
    try (var resources = Files.newDirectoryStream(context.directory().resolve("resources"))) {
      for (var resource : resources) {
        if ("job-keeper".equals(BuildFiles.read(resource.resolve("intent.properties")).getProperty("kind"))) {
          return resource;
        }
      }
    }
    throw new IOException("The fixture did not reserve its keeper");
  }

  private static WindowsJobObject job(Arena arena, List<Long> releases, boolean fail) {
    var state = MemoryLayout.structLayout(ValueLayout.JAVA_INT.withName("GetLastError"));
    var functions = Map.<String, NativeCall>of("CloseHandle", arguments -> {
      releases.add(((MemorySegment) arguments[1]).address());
      ((MemorySegment) arguments[0]).set(ValueLayout.JAVA_INT, 0, 7);
      return Optional.of(fail ? 0 : 1);
    });
    return new WindowsJobObject(MemorySegment.ofAddress(1), new WindowsKernel(arena, state, functions));
  }
}
