package org.aquilagraph.application;

import java.io.Serial;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.util.Objects;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.model.AnalysisRun;

/** Associates a repository failure with the completed analysis run that could not be stored. */
public final class AnalysisRepositoryException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  private final transient AnalysisRun failedRun;

  /**
   * Creates a repository failure for a completed analysis run.
   *
   * @param analysisRun
   *          run that could not be stored
   * @param cause
   *          repository failure
   */
  public AnalysisRepositoryException(AnalysisRun analysisRun, RepositoryException cause) {
    super("Repository save failed for analysis run " + Objects.requireNonNull(analysisRun, "analysisRun").runId(),
        Objects.requireNonNull(cause, "cause"));
    failedRun = analysisRun;
  }

  /**
   * Returns the completed run retained for presentation or recovery.
   *
   * @return failed repository input
   */
  public AnalysisRun analysisRun() {
    return failedRun;
  }

  /**
   * Returns the repository failure that prevented storage.
   *
   * @return repository boundary failure
   */
  public RepositoryException repositoryFailure() {
    return (RepositoryException) Objects.requireNonNull(getCause(), "repository failure");
  }

  /**
   * Rejects deserialization because the transient run cannot be reconstructed.
   *
   * @param input
   *          serialized input
   * @throws IOException
   *           always, because the analysis run cannot be reconstructed
   * @throws ClassNotFoundException
   *           if default deserialization cannot resolve a serialized class
   */
  @Serial
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    throw new InvalidObjectException("AnalysisRepositoryException cannot be deserialized without its analysis run");
  }
}
