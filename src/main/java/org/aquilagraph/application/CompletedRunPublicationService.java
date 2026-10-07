package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.CompletedRunPublication;
import org.aquilagraph.application.port.RepositoryException;

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
    Objects.requireNonNull(run, "run");
    var subject = Optional.<PublicationSubjectIdentity>of(new CompletedRun(run.identity().key()));
    if (!run.isPublishable()) {
      return new PublicationOutcome(PublicationIntent.SAVE_COMPLETED, subject, PublicationStatus.NOT_ELIGIBLE, CommitKnowledge.NOT_STARTED,
          Optional.empty());
    }
    try (var repository = Objects.requireNonNull(repositories.open(), "repository")) {
      repository.save(run);
    } catch (RepositoryException exception) {
      throw new AnalysisRepositoryException(run, exception);
    }
    return new PublicationOutcome(PublicationIntent.SAVE_COMPLETED, subject, PublicationStatus.SAVED, CommitKnowledge.KNOWN_COMMIT,
        Optional.empty());
  }
}
