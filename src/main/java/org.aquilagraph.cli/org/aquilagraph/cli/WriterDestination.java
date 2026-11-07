package org.aquilagraph.cli;

import java.io.IOException;
import java.io.Writer;
import org.aquilagraph.cli.ChannelEmission.Status;
import org.aquilagraph.cli.CommandPresentationException.Phase;

/** Writes once and flushes only after the throwing writer accepted the write. */
final class WriterDestination extends CommandDestination {
  private final Writer writer;

  WriterDestination(Writer writer) {
    this.writer = writer;
  }

  @Override
  ChannelEmission emit(String text, EmissionAttempt attempt) throws IOException {
    attempt.at(Phase.WRITE);
    writer.write(text);
    attempt.at(Phase.FLUSH);
    writer.flush();
    return Status.COMPLETED;
  }
}
