package org.aquilagraph.application;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;

/**
 * Storage outcome independent of the computed source and graph qualifications.
 *
 * @param intent
 *          explicit retained publication intent
 * @param subjectIdentity
 *          exact completed-run request key for SAVE_COMPLETED, otherwise the AST identity when available
 * @param status
 *          independent publication decision
 * @param knowledge
 *          conservative subject-replacement observation
 * @param failure
 *          full declared operation failure only for FAILED
 */
public record PublicationOutcome(PublicationIntent intent, Optional<PublicationSubjectIdentity> subjectIdentity, PublicationStatus status,
    CommitKnowledge knowledge, Optional<PublicationFailure> failure) {
  /** Enforces no-attempt, acknowledged-success, and failed-operation shapes. */
  public PublicationOutcome {
    Objects.requireNonNull(intent, "intent");
    Objects.requireNonNull(subjectIdentity, "subjectIdentity");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(knowledge, "knowledge");
    Objects.requireNonNull(failure, "failure");
    if ((status == PublicationStatus.FAILED) != failure.isPresent()
        || ((status == PublicationStatus.SAVED || status == PublicationStatus.FAILED) && subjectIdentity.isEmpty())
        || (status == PublicationStatus.SAVED && knowledge != CommitKnowledge.KNOWN_COMMIT)
        || ((status == PublicationStatus.NOT_REQUESTED || status == PublicationStatus.NOT_ELIGIBLE)
            && knowledge != CommitKnowledge.NOT_STARTED)
        || ((intent == PublicationIntent.DO_NOT_SAVE) != (status == PublicationStatus.NOT_REQUESTED))
        || (intent != PublicationIntent.SAVE_COMPLETED && subjectIdentity.filter(value -> !(value instanceof Ast)).isPresent())
        || (intent == PublicationIntent.SAVE_COMPLETED && subjectIdentity.filter(CompletedRun.class::isInstance).isEmpty())) {
      throw new IllegalArgumentException("Publication fields contradict its intent or observed operation state");
    }
    failure.ifPresent(value -> {
      var context = value.carrier().context();
      if (value.knowledge() != knowledge || !subjectIdentity.equals(Optional.of(context.subject()))) {
        throw new IllegalArgumentException("Publication failure must retain its exact attempted subject and knowledge");
      }
    });
  }
}
