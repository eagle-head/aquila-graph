package org.aquilagraph.cli;

import java.io.PrintStream;
import java.util.Objects;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.model.JavaRelease;

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
  private final AnalyzeCommandPresenter presenter;

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
    var useCase = Objects.requireNonNull(analysisUseCase, "analysisUseCase");
    parser = new AnalyzeCommandParser(defaultRelease);
    executor = new AnalyzeCommandExecutor(useCase);
    presenter = new AnalyzeCommandPresenter();
  }

  /**
   * Executes one command invocation.
   *
   * @param arguments
   *          command-line arguments
   * @param output
   *          standard result stream
   * @param error
   *          diagnostic stream
   * @return one of the declared exit codes
   */
  public int run(String[] arguments, PrintStream output, PrintStream error) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(error, "error");

    return switch (parser.parse(arguments)) {
      case AnalyzeCommandParser.Parsed parsed -> presenter.present(executor.execute(parsed.arguments()), output, error);
      case AnalyzeCommandParser.Rejected rejected -> presenter.present(rejected, error);
    };
  }
}
