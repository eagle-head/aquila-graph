package org.aquilagraph.cli;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.AnalysisPublicationException;
import org.aquilagraph.cli.ChannelEmission.AbruptFailure;
import org.aquilagraph.cli.ChannelEmission.IoFailure;
import org.aquilagraph.cli.ChannelEmission.Status;
import org.aquilagraph.cli.ChannelEmission.StreamError;

/** Coordinates execution, complete preparation, independent emission and exit policy once. */
public final class CommandRunner {
  private final AnalyzeCommand command;
  private final CommandOutputEmitter emitter = new CommandOutputEmitter();

  /**
   * Uses the command's existing detached report and complete text preparation.
   * 
   * @param command
   *          output-independent command
   */
  public CommandRunner(AnalyzeCommand command) {
    this.command = Objects.requireNonNull(command, "command");
  }

  /**
   * Executes once after validating caller inputs, preparing both payloads before any destination operation. Borrows destinations without
   * closing or retrying them. An abrupt publication is prepared and presented on the diagnostic destination before propagation. Unmatched
   * computation failures and fatal errors retain their abrupt family.
   * 
   * @param arguments
   *          unchanged command-line tokens
   * @param output
   *          borrowed result destination
   * @param error
   *          borrowed diagnostic destination
   * @return original outcome, independent observations and explicit exit classification
   * @throws CommandPresentationException
   *           if preparation or a destination fails abruptly after a command outcome was established
   * @throws AnalysisPublicationException
   *           if publication fails abruptly and its diagnostic presentation completes
   * @throws PublicationDiagnosticException
   *           if diagnostic presentation for an abrupt publication fails nonfatally
   */
  public CommandInvocation run(String[] arguments, CommandDestination output, CommandDestination error) {
    Objects.requireNonNull(arguments, "arguments");
    for (var argument : arguments) {
      Objects.requireNonNull(argument, "argument");
    }
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(error, "error");
    AnalyzeCommandOutcome outcome;
    try {
      outcome = command.execute(arguments);
    } catch (AnalysisPublicationException failure) {
      presentPublicationFailure(failure, error);
      throw failure;
    }
    var prepared = command.prepare(outcome);
    return new CommandInvocation(outcome, emitter.emit(outcome, prepared, output, error));
  }

  private void presentPublicationFailure(AnalysisPublicationException publication, CommandDestination error) {
    var prepared = command.preparePublicationDiagnostics(publication);
    var progress = emitter.emitDiagnostics(prepared, error);
    switch (progress.error()) {
      case Status _ -> {
        return;
      }
      case IoFailure failure ->
        throw new PublicationDiagnosticException(publication, failure.phase(), Optional.of(failure.cause()), progress);
      case StreamError failure -> throw new PublicationDiagnosticException(publication, failure.phase(), Optional.empty(), progress);
      case AbruptFailure failure ->
        throw new PublicationDiagnosticException(publication, failure.phase(), Optional.of(failure.cause()), progress);
    }
  }
}
