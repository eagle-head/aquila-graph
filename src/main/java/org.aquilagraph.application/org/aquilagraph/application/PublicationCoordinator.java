package org.aquilagraph.application;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.application.port.RepositoryOperationException;
import org.aquilagraph.source.api.AstCompletion;

/** Owns common eligibility, repository lifetime, and publication decisions. */
final class PublicationCoordinator {
  private PublicationCoordinator() {
  }

  static PublicationOutcome notRequested(ComputedPublicationSubject subject) {
    return new PublicationOutcome(PublicationIntent.DO_NOT_SAVE, astIdentity(subject), PublicationStatus.NOT_REQUESTED,
        CommitKnowledge.NOT_STARTED, Optional.empty());
  }

  static <R> PublicationOutcome publish(ComputedPublicationSubject subject, PublicationIntent intent,
      Function<RepositoryOperationContext, R> acquire, Consumer<R> save, Consumer<R> close) {
    var identity = intent == PublicationIntent.SAVE_COMPLETED
        ? Optional.<PublicationSubjectIdentity>of(new CompletedRun(((Coordinated) subject).run().identity().key()))
        : astIdentity(subject);
    var eligible = intent == PublicationIntent.SAVE_COMPLETED
        ? ((Coordinated) subject).run().isPublishable()
        : subject.ast().completion() == AstCompletion.COMPLETE;
    if (!eligible) {
      return new PublicationOutcome(intent, identity, PublicationStatus.NOT_ELIGIBLE, CommitKnowledge.NOT_STARTED, Optional.empty());
    }
    var kind = intent == PublicationIntent.SAVE_COMPLETED ? RepositoryOperationKind.SAVE_COMPLETED_RUN : RepositoryOperationKind.SAVE_AST;
    var context = new RepositoryOperationContext(kind, identity.orElseThrow());
    var owned = new OwnedRepository(context);
    try {
      owned.use(acquire, repository -> {
        owned.save(() -> save.accept(repository));
        return null;
      }, close);
    } catch (RepositoryOperationException failure) {
      if (failure.knowledge().isPresent() && failure.effectivePrimary() instanceof RepositoryException) {
        return new PublicationOutcome(intent, identity, PublicationStatus.FAILED, failure.knowledge().orElseThrow(),
            Optional.of(new PublicationFailure(failure)));
      }
      throw new AnalysisPublicationException(subject, failure);
    }
    return new PublicationOutcome(intent, identity, PublicationStatus.SAVED, CommitKnowledge.KNOWN_COMMIT, Optional.empty());
  }

  static RepositoryOperationContext contextFor(ComputedPublicationSubject subject, RepositoryOperationKind kind) {
    return switch (kind) {
      case SAVE_AST -> new RepositoryOperationContext(kind, astIdentity(subject).orElseThrow());
      case SAVE_COMPLETED_RUN -> {
        if (!(subject instanceof Coordinated coordinated)) {
          throw new IllegalArgumentException("Completed-run publication requires a coordinated subject");
        }
        yield RepositoryOperationContext.saveCompletedRun(coordinated.run().identity().key());
      }
      case QUERY_AST -> throw new IllegalArgumentException("Publication requires a save operation");
    };
  }

  private static Optional<PublicationSubjectIdentity> astIdentity(ComputedPublicationSubject subject) {
    return subject.ast().result().map(result -> new Ast(result.identity()));
  }
}
