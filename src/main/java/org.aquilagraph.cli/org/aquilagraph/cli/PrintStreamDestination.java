package org.aquilagraph.cli;

import java.io.PrintStream;
import org.aquilagraph.cli.ChannelEmission.Status;
import org.aquilagraph.cli.ChannelEmission.StreamError;
import org.aquilagraph.cli.CommandPresentationException.Phase;

/** Observes stream flags without inventing the suppressed underlying failure. */
final class PrintStreamDestination extends CommandDestination {
  private final PrintStream stream;

  PrintStreamDestination(PrintStream stream) {
    this.stream = stream;
  }

  @Override
  ChannelEmission emit(String text, EmissionAttempt attempt) {
    attempt.at(Phase.PREFLIGHT);
    if (stream.checkError()) {
      return new StreamError(Phase.PREFLIGHT);
    }
    attempt.at(Phase.WRITE);
    stream.print(text);
    attempt.at(Phase.POST_WRITE_INSPECTION);
    return stream.checkError() ? new StreamError(Phase.POST_WRITE_INSPECTION) : Status.COMPLETED;
  }
}
