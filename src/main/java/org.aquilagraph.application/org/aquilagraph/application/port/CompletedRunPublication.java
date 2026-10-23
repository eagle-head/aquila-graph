package org.aquilagraph.application.port;

import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.PublicationOutcome;

/** Publishes an exact native run only after complete source and body coverage. */
@FunctionalInterface
public interface CompletedRunPublication {
  /**
   * Applies complete body coverage and error qualification before repository acquisition.
   *
   * @param run
   *          computed coordinated request
   * @return independent completed-run publication decision
   */
  PublicationOutcome publish(AnalysisRun run);
}
