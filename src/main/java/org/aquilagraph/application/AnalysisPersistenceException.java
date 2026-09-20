package org.aquilagraph.application;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;
import org.aquilagraph.model.AnalysisRun;

/** Associates a persistence failure with the completed analysis run that could not be stored. */
public final class AnalysisPersistenceException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  private final transient AnalysisRun failedRun;

  /**
   * Creates a persistence failure for a completed analysis run.
   *
   * @param analysisRun
   *          run that could not be persisted
   * @param cause
   *          repository failure
   */
  public AnalysisPersistenceException(AnalysisRun analysisRun, RuntimeException cause) {
    super("Persistence failed for analysis run " + Objects.requireNonNull(analysisRun, "analysisRun").runId(),
        Objects.requireNonNull(cause, "cause"));
    failedRun = analysisRun;
  }

  /**
   * Returns the completed run retained for presentation or recovery.
   *
   * @return failed persistence input
   */
  public AnalysisRun analysisRun() {
    return failedRun;
  }

  /**
   * Rejects deserialization because the transient analysis run cannot be reconstructed.
   *
   * @param input
   *          serialized input stream
   * @throws IOException
   *           always, after reading default serial fields
   * @throws ClassNotFoundException
   *           if a serialized field class cannot be resolved
   */
  @Serial
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    throw new InvalidObjectException("AnalysisPersistenceException cannot be deserialized without its analysis run");
  }
}
