package org.aquilagraph.cli;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.AnalysisPublicationException;
import org.aquilagraph.cli.CommandPresentationException.Phase;

/** Nonfatal diagnostic presentation failure retaining the original abrupt publication separately from the presentation cause. */
public final class PublicationDiagnosticException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;
  private final transient DiagnosticContext context;

  PublicationDiagnosticException(AnalysisPublicationException publication, Phase phase, Throwable failure) {
    this(publication, phase, Optional.of(failure), Optional.empty());
  }

  PublicationDiagnosticException(AnalysisPublicationException publication, Phase phase, Optional<Throwable> failure,
      CommandEmission progress) {
    this(publication, phase, failure, Optional.of(progress));
  }

  private PublicationDiagnosticException(AnalysisPublicationException publication, Phase phase, Optional<Throwable> failure,
      Optional<CommandEmission> progress) {
    super("Publication diagnostic presentation failed", failure.orElse(null));
    context = new DiagnosticContext(Objects.requireNonNull(publication, "publication"), Objects.requireNonNull(phase, "phase"), progress);
  }

  /**
   * Returns the original publication exception with its retained operation evidence.
   *
   * @return exact abrupt publication exception
   */
  public AnalysisPublicationException publicationFailure() {
    return context.publication();
  }

  /**
   * Returns the actual preparation or diagnostic destination operation that failed.
   *
   * @return failed presentation phase
   */
  public Phase phase() {
    return context.phase();
  }

  /**
   * Returns the failed destination when a diagnostic emission was attempted.
   *
   * @return standard error during emission, empty during preparation
   */
  public Optional<OutputChannel> channel() {
    return context.progress().map(ignored -> OutputChannel.STDERR);
  }

  /**
   * Returns the available channel observations without claiming delivery.
   *
   * @return emission progress with unused standard output, empty during preparation
   */
  public Optional<CommandEmission> progress() {
    return context.progress();
  }

  /**
   * Returns the original secondary throwable when the failure supplied one.
   *
   * @return exact presentation throwable, empty for an observed stream flag
   */
  public Optional<Throwable> presentationFailure() {
    return Optional.ofNullable(getCause());
  }

  /**
   * Rejects restoration without the original operational context.
   *
   * @param input
   *          serialization stream
   * @throws InvalidObjectException
   *           always
   */
  @Serial
  private void readObject(ObjectInputStream input) throws InvalidObjectException {
    Objects.requireNonNull(input, "input");
    throw new InvalidObjectException("Publication diagnostic failure requires its original publication context");
  }

  private record DiagnosticContext(AnalysisPublicationException publication, Phase phase, Optional<CommandEmission> progress) {
  }
}
