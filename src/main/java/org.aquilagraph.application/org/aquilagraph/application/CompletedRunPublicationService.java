package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.CompletedRunPublication;

/** Owns eligibility, lazy acquisition, and closure for native completed-run publication. */
public final class CompletedRunPublicationService implements CompletedRunPublication {
  private final AnalysisRunRepositoryFactory repositories;

  /**
   * Creates publication without acquiring a repository.
   *
   * @param repositories
   *          lazy completed-run storage factory
   */
  public CompletedRunPublicationService(AnalysisRunRepositoryFactory repositories) {
    this.repositories = Objects.requireNonNull(repositories, "repositories");
  }

  @Override
  public PublicationOutcome publish(AnalysisRun run) {
    var subject = new Coordinated(Objects.requireNonNull(run, "run"));
    return PublicationCoordinator.publish(subject, PublicationIntent.SAVE_COMPLETED, repositories::open, repository -> repository.save(run),
        AnalysisRunRepository::close);
  }
}
