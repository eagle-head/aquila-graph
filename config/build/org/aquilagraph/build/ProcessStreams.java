package org.aquilagraph.build;

import java.io.IOException;
import java.io.Closeable;
import java.util.Optional;

/** Releases the parent-side pipe endpoints after owned process closure. */
final class ProcessStreams {
  private ProcessStreams() {
  }

  static Closeable closing(Optional<Process> process) {
    return () -> {
      if (process.isPresent()) {
        close(process.orElseThrow());
      }
    };
  }

  static void close(Process process) throws IOException {
    try (var _ = process.getInputStream(); var _ = process.getErrorStream(); var _ = process.getOutputStream()) {
      // Closing every endpoint also releases streams when another endpoint reports a failure.
    }
  }
}
