package org.aquilagraph.cli;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Future.State;
import java.util.concurrent.FutureTask;
import org.aquilagraph.cli.ChannelEmission.AbruptFailure;
import org.aquilagraph.cli.ChannelEmission.IoFailure;
import org.aquilagraph.cli.ChannelEmission.Status;

/** Attempts independent complete payloads once, retaining checked and abrupt progress. */
final class CommandOutputEmitter {
  CommandEmission emit(AnalyzeCommandOutcome outcome, PreparedCommandOutput prepared, CommandDestination output, CommandDestination error) {
    ChannelEmission stdout = initial(prepared.output());
    ChannelEmission stderr = initial(prepared.error());
    if (stdout == Status.UNATTEMPTED) {
      stdout = emit(outcome, OutputChannel.STDOUT, prepared.output(), output, new CommandEmission(stdout, stderr));
    }
    if (stderr == Status.UNATTEMPTED) {
      stderr = emit(outcome, OutputChannel.STDERR, prepared.error(), error, new CommandEmission(stdout, stderr));
    }
    return new CommandEmission(stdout, stderr);
  }

  private static ChannelEmission initial(String text) {
    return text.isEmpty() ? Status.UNUSED : Status.UNATTEMPTED;
  }

  private static ChannelEmission emit(AnalyzeCommandOutcome outcome, OutputChannel channel, String text, CommandDestination destination,
      CommandEmission progress) {
    var attempt = new EmissionAttempt();
    var observation = new FutureTask<ChannelEmission>(() -> {
      try {
        return destination.emit(text, attempt);
      } catch (IOException failure) {
        return new IoFailure(attempt.phase(), failure);
      }
    });
    observation.run();
    if (observation.state() == State.FAILED) {
      var failure = observation.exceptionNow();
      if (failure instanceof Error fatal) {
        throw fatal;
      }
      var interrupted = new AbruptFailure(attempt.phase(), failure);
      var retained = channel == OutputChannel.STDOUT
          ? new CommandEmission(interrupted, progress.error())
          : new CommandEmission(progress.output(), interrupted);
      throw new CommandPresentationException(outcome, attempt.phase(), failure, Optional.of(channel), Optional.of(retained));
    }
    return observation.resultNow();
  }
}
