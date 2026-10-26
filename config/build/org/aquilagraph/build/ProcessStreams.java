package org.aquilagraph.build;

import java.io.IOException;

/** Releases the parent-side pipe endpoints after owned process closure. */
final class ProcessStreams {
  private ProcessStreams() {
  }

  static void close(Process process) throws IOException {
    try (var _ = process.getInputStream(); var _ = process.getErrorStream(); var _ = process.getOutputStream()) {
      // Closing every endpoint also releases streams when another endpoint reports a failure.
    }
  }
}
