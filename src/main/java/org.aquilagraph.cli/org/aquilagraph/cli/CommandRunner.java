package org.aquilagraph.cli;

import java.util.Objects;

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
   * closing or retrying them. Unmatched computation failures and fatal errors retain their abrupt family. Nonfatal presentation failures
   * retain the outcome, operation and available progress.
   * 
   * @param arguments
   *          unchanged command-line tokens
   * @param output
   *          borrowed result destination
   * @param error
   *          borrowed diagnostic destination
   * @return original outcome, independent observations and explicit exit classification
   * @throws CommandPresentationException
   *           if preparation or a destination fails abruptly
   */
  public CommandInvocation run(String[] arguments, CommandDestination output, CommandDestination error) {
    Objects.requireNonNull(arguments, "arguments");
    for (var argument : arguments) {
      Objects.requireNonNull(argument, "argument");
    }
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(error, "error");
    var outcome = command.execute(arguments);
    var prepared = command.prepare(outcome);
    return new CommandInvocation(outcome, emitter.emit(outcome, prepared, output, error));
  }
}
