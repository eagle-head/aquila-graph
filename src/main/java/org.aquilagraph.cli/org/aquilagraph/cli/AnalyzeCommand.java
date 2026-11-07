package org.aquilagraph.cli;

import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future.State;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;
import org.aquilagraph.application.AnalysisDerivationException;
import org.aquilagraph.application.AnalysisPublicationException;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.cli.CommandPreparationException.Phase;

/** Coordinates one command-line analysis request from parsing through presentation. */
public final class AnalyzeCommand {
  /** Exit code for a completed analysis and any requested repository save. */
  public static final int EXIT_COMPLETED = 0;
  /** Exit code for an analysis that produced an incomplete result. */
  public static final int EXIT_INCOMPLETE = 1;
  /** Exit code for rejected input or arguments. */
  public static final int EXIT_REJECTED = 2;
  /** Exit code for a completed analysis whose repository save failed. */
  public static final int EXIT_REPOSITORY_FAILED = 3;

  private final AnalyzeCommandParser parser;
  private final AnalyzeCommandExecutor executor;
  private final CommandReportProjector projector;
  private final CommandDocumentComposer composer;
  private final CommandTextRenderer renderer;

  /**
   * Creates a command that uses Java 25 when no release is supplied.
   *
   * @param analysisUseCase
   *          analysis input port
   */
  public AnalyzeCommand(AnalysisUseCase analysisUseCase) {
    this(analysisUseCase, JavaRelease.JAVA_25);
  }

  /**
   * Creates a command with an explicitly selected default Java release.
   *
   * @param analysisUseCase
   *          analysis input port
   * @param defaultRelease
   *          release selected when the command does not override it
   */
  public AnalyzeCommand(AnalysisUseCase analysisUseCase, JavaRelease defaultRelease) {
    this(analysisUseCase, defaultRelease, failure -> Optional.empty());
  }

  /**
   * Creates the command with bootstrap-supplied public adapter observation projection.
   *
   * @param analysisUseCase
   *          analysis input port
   * @param defaultRelease
   *          release selected when no override is supplied
   * @param failureDetails
   *          detached adapter-detail boundary
   */
  public AnalyzeCommand(AnalysisUseCase analysisUseCase, JavaRelease defaultRelease, FailureDetailProjector failureDetails) {
    this(new AnalyzeCommandParser(defaultRelease), new AnalyzeCommandExecutor(analysisUseCase), new CommandReportProjector(failureDetails),
        new CommandDocumentComposer(), new CommandTextRenderer(System.lineSeparator()));
  }

  AnalyzeCommand(AnalyzeCommandParser parser, AnalyzeCommandExecutor executor, CommandReportProjector projector,
      CommandDocumentComposer composer, CommandTextRenderer renderer) {
    this.parser = Objects.requireNonNull(parser, "parser");
    this.executor = Objects.requireNonNull(executor, "executor");
    this.projector = Objects.requireNonNull(projector, "projector");
    this.composer = Objects.requireNonNull(composer, "composer");
    this.renderer = Objects.requireNonNull(renderer, "renderer");
  }

  /**
   * Establishes an operational outcome without requiring or touching report destinations. Named analysis failures retain their original
   * exception identity and relationships.
   *
   * @param arguments
   *          unchanged command-line arguments
   * @return structured usage rejection, named failure, or returned execution
   * @throws NullPointerException
   *           if the array or a token is null
   * @throws AnalysisDerivationException
   *           if derivation fails abruptly
   * @throws AnalysisPublicationException
   *           if publication fails abruptly
   */
  public AnalyzeCommandOutcome execute(String... arguments) {
    return switch (parser.parse(Objects.requireNonNull(arguments, "arguments"))) {
      case AnalyzeCommandParser.Parsed parsed -> executor.execute(parsed.arguments());
      case AnalyzeCommandParser.Rejected rejected -> new AnalyzeCommandOutcome.UsageRejected(rejected.reason());
    };
  }

  PreparedCommandOutput prepare(AnalyzeCommandOutcome outcome) {
    var report = prepare(outcome, Phase.PROJECTION, () -> projector.project(outcome));
    var document = prepare(outcome, Phase.COMPOSITION, () -> composer.compose(report));
    return prepare(outcome, Phase.FORMATTING, () -> renderer.render(document));
  }

  private static <T> T prepare(AnalyzeCommandOutcome outcome, Phase phase, Supplier<T> action) {
    var observation = new FutureTask<T>(action::get);
    observation.run();
    if (observation.state() == State.FAILED) {
      var failure = observation.exceptionNow();
      if (failure instanceof Error fatal) {
        throw fatal;
      }
      throw new CommandPreparationException(outcome, phase, failure);
    }
    return observation.resultNow();
  }

  /**
   * Executes one command invocation.
   *
   * <p>
   * The caller retains ownership of both streams and controls their closure. Output ordering is defined within each stream. The command
   * translates named input and environment failures and presents returned publication failures with their analysis summary. Abrupt failures
   * propagate with their causes and suppressed exceptions. Both payloads are fully prepared before any CLI-owned channel operation.
   * Preparation failure retains the outcome and phase without report emission. Destination runtime failures remain abrupt and may leave
   * partial output. External delivery depends on the supplied streams.
   *
   * @param arguments
   *          command-line arguments
   * @param output
   *          standard result stream
   * @param error
   *          diagnostic stream
   * @return one of the declared exit codes
   * @throws NullPointerException
   *           if the argument array, an argument, or either stream is null
   * @throws AnalysisDerivationException
   *           if control-flow derivation fails abruptly, retaining source and derivation progress
   * @throws AnalysisPublicationException
   *           if publication fails abruptly, retaining the computed result and operation evidence
   */
  public int run(String[] arguments, PrintStream output, PrintStream error) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(error, "error");

    var outcome = execute(arguments);
    var prepared = prepare(outcome);
    if (!prepared.output().isEmpty()) {
      output.print(prepared.output());
    }
    if (!prepared.error().isEmpty()) {
      error.print(prepared.error());
    }
    return semanticExitCode(outcome);
  }

  static int semanticExitCode(AnalyzeCommandOutcome outcome) {
    return switch (outcome) {
      case AnalyzeCommandOutcome.UsageRejected _ -> EXIT_REJECTED;
      case AnalyzeCommandOutcome.InputRejected _ -> EXIT_REJECTED;
      case AnalyzeCommandOutcome.EnvironmentFailed _ -> EXIT_INCOMPLETE;
      case AnalyzeCommandOutcome.Executed executed -> {
        if (executed.execution().publication().failure().isPresent()) {
          yield EXIT_REPOSITORY_FAILED;
        }
        yield switch (executed.execution().run().completion()) {
          case COMPLETE -> EXIT_COMPLETED;
          case PARTIAL, FAILED -> EXIT_INCOMPLETE;
          case REJECTED -> EXIT_REJECTED;
        };
      }
    };
  }
}
