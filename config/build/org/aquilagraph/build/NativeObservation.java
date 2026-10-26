package org.aquilagraph.build;

import java.io.IOException;
import java.util.Optional;

/** Owns a bounded native observation and its write-ahead recovery record. */
final class NativeObservation implements AutoCloseable {
  private final InvocationContext context;
  private final BuildJournal journal;
  private Optional<Process> process = Optional.empty();

  NativeObservation(InvocationContext context, BuildJournal journal) {
    this.context = context;
    this.journal = journal;
  }

  Process start(ProcessBuilder builder) throws IOException {
    Process child;
    try {
      child = builder.start();
    } catch (IOException exception) {
      journal.closeWithoutLaunch();
      throw exception;
    }
    process = Optional.of(child);
    journal.observe(child.toHandle());
    return child;
  }

  @Override
  public void close() throws IOException {
    try {
      journal.closeObserved();
      BuildFiles.write(context.directory().resolve("owner-closed.properties"), context.properties());
    } finally {
      if (process.isPresent()) {
        ProcessStreams.close(process.orElseThrow());
      }
    }
  }
}
