package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aquilagraph.build.WindowsJobObject.Child;
import org.junit.jupiter.api.Test;

class WindowsProcessCreationTest {
  @Test
  void givenAnExistingJobNameAndFailedHandleRelease_whenCreating_thenTheNameRefusalRetainsTheCleanupFailure() throws Exception {
    // Arrange
    var releases = new ArrayList<Long>();
    try (var arena = Arena.ofConfined()) {
      var state = MemoryLayout.structLayout(ValueLayout.JAVA_INT.withName("GetLastError"));
      var functions = Map.<String, NativeCall>of("CreateJobObjectW", arguments -> {
        ((MemorySegment) arguments[0]).set(ValueLayout.JAVA_INT, 0, 183);
        return Optional.of(MemorySegment.ofAddress(1));
      }, "CloseHandle", arguments -> {
        releases.add(((MemorySegment) arguments[1]).address());
        ((MemorySegment) arguments[0]).set(ValueLayout.JAVA_INT, 0, 5);
        return Optional.of(0);
      });
      var kernel = new WindowsKernel(arena, state, functions);

      // Act
      var failure = assertThrows(IOException.class, () -> kernel.create("existing-job"));

      // Assert
      assertEquals("The new Windows job name already exists: existing-job", failure.getMessage());
      assertEquals(1, failure.getSuppressed().length);
      assertEquals("CloseHandle failed with Windows error 5", failure.getSuppressed()[0].getMessage());
      assertEquals(List.of(1L), releases);
    }
  }

  @Test
  void givenFailedCreationAndAttributeCleanup_whenStarting_thenTheNativeCreationErrorRemainsPrimary() throws Exception {
    // Arrange
    try (var fixture = new Kernel(true, false, true)) {
      // Act
      var failure = assertThrows(IOException.class, fixture::start);

      // Assert
      assertEquals("CreateProcessW failed with Windows error 5", failure.getMessage());
      assertEquals(1, failure.getSuppressed().length);
      assertSame(fixture.attributeFailure, failure.getSuppressed()[0]);
      assertTrue(fixture.attributesDeleted);
      assertTrue(fixture.releases.isEmpty());
    }
  }

  @Test
  void givenFailedThreadReleaseAndFurtherCleanupFailures_whenStarting_thenEveryFailureIsPreservedBeforePublication() throws Exception {
    // Arrange
    try (var fixture = new Kernel(false, true, true)) {
      // Act
      var failure = assertThrows(IOException.class, fixture::start);

      // Assert
      assertEquals("CloseHandle failed with Windows error 5", failure.getMessage());
      assertEquals(2, failure.getSuppressed().length);
      assertSame(fixture.attributeFailure, failure.getSuppressed()[0]);
      assertEquals("CloseHandle failed with Windows error 6", failure.getSuppressed()[1].getMessage());
      assertEquals(List.of(2L, 1L), fixture.releases);
      assertTrue(fixture.attributesDeleted);
    }
  }

  @Test
  void givenCreatedProcessAndFailedAttributeCleanup_whenStarting_thenTheUnpublishedProcessHandleIsStillReleased() throws Exception {
    // Arrange
    try (var fixture = new Kernel(false, false, true)) {
      // Act
      var failure = assertThrows(IllegalStateException.class, fixture::start);

      // Assert
      assertSame(fixture.attributeFailure, failure);
      assertEquals(0, failure.getSuppressed().length);
      assertEquals(List.of(2L, 1L), fixture.releases);
      assertTrue(fixture.attributesDeleted);
    }
  }

  @Test
  void givenSuccessfulNativeCreation_whenTheReturnedChildCompletes_thenItsBoundKernelOwnsObservationAndRelease() throws Exception {
    // Arrange
    try (var fixture = new Kernel(false, false, false)) {
      int exit;

      // Act
      try (var child = fixture.start()) {
        assertTrue(child.awaitSlice());
        exit = child.exitValue();
      }

      // Assert
      assertEquals(17, exit);
      assertEquals(List.of(2L, 1L), fixture.releases);
      assertTrue(fixture.attributesDeleted);
    }
  }

  private static final class Kernel implements AutoCloseable {
    private final Arena arena = Arena.ofConfined();
    private final IllegalStateException attributeFailure = new IllegalStateException("attribute cleanup failed");
    private final List<Long> releases = new ArrayList<>();
    private final WindowsKernel kernel;
    private boolean attributesDeleted;

    private Kernel(boolean failCreation, boolean failHandles, boolean failAttributes) {
      var state = MemoryLayout.structLayout(ValueLayout.JAVA_INT.withName("GetLastError"));
      var functions = new HashMap<String, NativeCall>();
      functions.put("InitializeProcThreadAttributeList", arguments -> {
        if (((MemorySegment) arguments[1]).address() == 0) {
          ((MemorySegment) arguments[4]).set(ValueLayout.JAVA_LONG, 0, 64);
          ((MemorySegment) arguments[0]).set(ValueLayout.JAVA_INT, 0, 122);
          return Optional.of(0);
        }
        return Optional.of(1);
      });
      functions.put("UpdateProcThreadAttribute", arguments -> Optional.of(1));
      functions.put("GetStdHandle", arguments -> Optional.of(MemorySegment.ofAddress(100)));
      functions.put("CreateProcessW", arguments -> {
        if (failCreation) {
          ((MemorySegment) arguments[0]).set(ValueLayout.JAVA_INT, 0, 5);
          return Optional.of(0);
        }
        var information = (MemorySegment) arguments[10];
        information.set(ValueLayout.ADDRESS, 0, MemorySegment.ofAddress(1));
        information.set(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize(), MemorySegment.ofAddress(2));
        information.set(ValueLayout.JAVA_INT, 2 * ValueLayout.ADDRESS.byteSize(), 1234);
        return Optional.of(1);
      });
      functions.put("CloseHandle", arguments -> {
        var handle = ((MemorySegment) arguments[1]).address();
        releases.add(handle);
        if (failHandles) {
          ((MemorySegment) arguments[0]).set(ValueLayout.JAVA_INT, 0, handle == 2 ? 5 : 6);
          return Optional.of(0);
        }
        return Optional.of(1);
      });
      functions.put("DeleteProcThreadAttributeList", arguments -> {
        attributesDeleted = true;
        if (failAttributes) {
          throw attributeFailure;
        }
        return Optional.empty();
      });
      functions.put("WaitForSingleObject", arguments -> Optional.of(0));
      functions.put("GetExitCodeProcess", arguments -> {
        ((MemorySegment) arguments[2]).set(ValueLayout.JAVA_INT, 0, 17);
        return Optional.of(1);
      });
      kernel = new WindowsKernel(arena, state, Map.copyOf(functions));
    }

    private Child start() throws IOException {
      return kernel.start(MemorySegment.ofAddress(3), Path.of("java"), Path.of("arguments"), Path.of("directory"));
    }

    @Override
    public void close() {
      arena.close();
    }
  }
}
