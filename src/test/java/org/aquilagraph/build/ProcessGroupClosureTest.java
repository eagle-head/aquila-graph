package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessGroupClosureTest {
  @TempDir Path directory;

  @Test
  void givenInvalidGroupEvidenceAndFailedHandleRelease_whenClosing_thenTheEvidenceFailureRetainsTheNativeCleanupFailure()
      throws Exception {
    // Arrange
    var root = directory.toRealPath();
    var nonce = UUID.randomUUID().toString();
    var context = new InvocationContext(root, root.resolve(".build-state/invocations").resolve(nonce), root, nonce,
        ProcessIdentity.observe(ProcessHandle.current()), ProcessIdentity.namespace());
    BuildFiles.write(context.directory().resolve("invocation.properties"), context.properties());
    var journal = NativeJournalFixtures.windowsGroup(context, "git-inspection");
    var intent = BuildFiles.read(journal.directory().resolve("intent.properties"));
    intent.setProperty("containment.kind", "unrecognized-group");
    BuildFiles.write(journal.directory().resolve("intent.properties"), intent);
    var releases = new ArrayList<Long>();
    try (var arena = Arena.ofConfined()) {
      var state = MemoryLayout.structLayout(ValueLayout.JAVA_INT.withName("GetLastError"));
      var functions = Map.<String, NativeCall>of("CloseHandle", arguments -> {
        releases.add(((MemorySegment) arguments[1]).address());
        ((MemorySegment) arguments[0]).set(ValueLayout.JAVA_INT, 0, 7);
        return Optional.of(0);
      });
      var job = new WindowsJobObject(MemorySegment.ofAddress(1), new WindowsKernel(arena, state, functions));
      var group = new ProcessGroup(context, journal, new ProcessBuilder("unused"), root.resolve("unused.args"), Optional.of(job));

      // Act
      var failure = assertThrows(IOException.class, group::close);

      // Assert
      assertTrue(failure.getMessage().contains("unsupported native containment kind"));
      assertEquals(1, failure.getSuppressed().length);
      assertEquals("CloseHandle failed with Windows error 7", assertInstanceOf(IOException.class, failure.getSuppressed()[0]).getMessage());
      assertEquals(List.of(1L), releases);
      assertFalse(Files.exists(journal.directory().resolve("closed.properties")));
      assertFalse(Files.exists(journal.directory().resolve("contained-closed.properties")));
      assertEquals("unrecognized-group", BuildFiles.read(journal.directory().resolve("intent.properties")).getProperty("containment.kind"));
    }
  }
}
