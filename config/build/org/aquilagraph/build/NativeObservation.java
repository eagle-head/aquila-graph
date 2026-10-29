package org.aquilagraph.build;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/** Owns a bounded native observation and its write-ahead recovery record. */
final class NativeObservation implements AutoCloseable {
  private final InvocationContext context;
  private final BuildJournal journal;
  private final Creation creation;
  private final boolean ownsInvocation;
  private Optional<Process> process = Optional.empty();

  NativeObservation(InvocationContext context, BuildJournal journal) {
    this(context, journal, ProcessBuilder::start);
  }

  NativeObservation(InvocationContext context, BuildJournal journal, Creation creation) {
    this(context, journal, creation, true);
  }

  NativeObservation(InvocationContext context, BuildJournal journal, Creation creation, boolean ownsInvocation) {
    this.context = context;
    this.journal = journal;
    this.creation = creation;
    this.ownsInvocation = ownsInvocation;
  }

  Process start(ProcessBuilder builder) throws IOException {
    var child = creation.start(builder);
    process = Optional.of(child);
    journal.observe(child.toHandle());
    return child;
  }

  @Override
  public void close() throws IOException {
    try (var _ = ProcessStreams.closing(process)) {
      if (process.isPresent()) {
        ProcessClosure.close(process.orElseThrow(), Duration.ofSeconds(10));
        journal.stopObserved();
      }
    }
    if (process.isEmpty()) {
      journal.closeWithoutLaunch();
    } else {
      journal.completeObserved();
    }
    if (ownsInvocation) {
      BuildFiles.write(context.directory().resolve("owner-closed.properties"), context.properties());
    }
  }

  /** External process creation is separate from observation identity and closure. */
  @FunctionalInterface
  interface Creation {
    Process start(ProcessBuilder builder) throws IOException;
  }
}
