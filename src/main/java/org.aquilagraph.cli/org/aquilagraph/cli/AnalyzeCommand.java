package org.aquilagraph.cli;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future.State;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;
import org.aquilagraph.application.AnalysisDerivationException;
import org.aquilagraph.application.AnalysisPublicationException;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.cli.CommandPresentationException.Phase;

/** Establishes output-independent command outcomes and prepares complete reports or abrupt diagnostics. */
public final class AnalyzeCommand {
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
    return prepare(new EstablishedOutcome(outcome));
  }

  PreparedCommandOutput preparePublicationDiagnostics(AnalysisPublicationException publication) {
    return prepare(new AbruptPublication(publication));
  }

  private PreparedCommandOutput prepare(PresentationSubject subject) {
    var report = prepare(subject, Phase.PROJECTION, () -> switch (subject) {
      case EstablishedOutcome established -> projector.project(established.outcome());
      case AbruptPublication abrupt -> projector.project(abrupt.publication());
    });
    var document = prepare(subject, Phase.COMPOSITION, () -> composer.compose(report));
    return prepare(subject, Phase.FORMATTING, () -> renderer.render(document));
  }

  private static <T> T prepare(PresentationSubject subject, Phase phase, Supplier<T> action) {
    var observation = new FutureTask<T>(action::get);
    observation.run();
    if (observation.state() == State.FAILED) {
      var failure = observation.exceptionNow();
      if (failure instanceof Error fatal) {
        throw fatal;
      }
      switch (subject) {
        case EstablishedOutcome established -> throw new CommandPresentationException(established.outcome(), phase, failure);
        case AbruptPublication abrupt -> throw new PublicationDiagnosticException(abrupt.publication(), phase, failure);
      }
    }
    return observation.resultNow();
  }

  private sealed interface PresentationSubject permits EstablishedOutcome, AbruptPublication {
  }

  private record EstablishedOutcome(AnalyzeCommandOutcome outcome) implements PresentationSubject {
  }

  private record AbruptPublication(AnalysisPublicationException publication) implements PresentationSubject {
  }
}
