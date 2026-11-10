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

  CommandEmission emitDiagnostics(PreparedCommandOutput prepared, CommandDestination error) {
    return new CommandEmission(Status.UNUSED, observe(prepared.error(), error));
  }

  private static ChannelEmission initial(String text) {
    return text.isEmpty() ? Status.UNUSED : Status.UNATTEMPTED;
  }

  private static ChannelEmission emit(AnalyzeCommandOutcome outcome, OutputChannel channel, String text, CommandDestination destination,
      CommandEmission progress) {
    var observation = observe(text, destination);
    if (observation instanceof AbruptFailure failure) {
      var retained = channel == OutputChannel.STDOUT
          ? new CommandEmission(failure, progress.error())
          : new CommandEmission(progress.output(), failure);
      throw new CommandPresentationException(outcome, failure.phase(), failure.cause(), Optional.of(channel), Optional.of(retained));
    }
    return observation;
  }

  private static ChannelEmission observe(String text, CommandDestination destination) {
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
      return new AbruptFailure(attempt.phase(), failure);
    }
    return observation.resultNow();
  }
}
