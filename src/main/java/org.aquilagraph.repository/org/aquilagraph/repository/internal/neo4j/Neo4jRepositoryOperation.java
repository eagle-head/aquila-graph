package org.aquilagraph.repository.internal.neo4j;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.internal.repository.RepositoryOperationBoundary;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.jspecify.annotations.Nullable;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;

/** Tracks one exact Neo4j operation and observes work in its creating region. */
final class Neo4jRepositoryOperation {
  private final RepositoryOperationContext context;
  private final String driverFailureDescription;
  private CommitKnowledge knowledge = CommitKnowledge.NOT_STARTED;

  Neo4jRepositoryOperation(RepositoryOperationContext context, String driverFailureDescription) {
    this.context = Objects.requireNonNull(context, "context");
    this.driverFailureDescription = driverFailureDescription;
  }

  <T> T acquire(Supplier<T> action) {
    return work(Neo4jRepositoryPhase.ACQUISITION, () -> {
      try {
        return Objects.requireNonNull(action.get(), "acquired resource");
      } catch (IllegalArgumentException failure) {
        throw new RepositoryException("Invalid Neo4j repository configuration", failure);
      } catch (Neo4jException failure) {
        throw new RepositoryException("Neo4j driver initialization failed", failure);
      }
    });
  }

  <T extends @Nullable Object> T work(Neo4jRepositoryPhase phase, Supplier<T> action) {
    return RepositoryOperationBoundary.observe(context, action,
        primary -> new Neo4jRepositoryOperationException(context, phase, knowledge, primary));
  }

  <T extends @Nullable Object> T driverWork(Neo4jRepositoryPhase phase, Supplier<T> action) {
    return work(phase, () -> inDriverRegion(action, driverFailureDescription));
  }

  <T extends @Nullable Object> T withSession(Driver driver, SessionConfig config, Neo4jRepositoryPhase opening,
      Neo4jRepositoryPhase closing, Function<Session, T> work) {
    return RepositoryOperationBoundary.withResource(driverWork(opening, () -> Objects.requireNonNull(driver.session(config), "session")),
        work, session -> driverWork(closing, () -> {
          session.close();
          return null;
        }));
  }

  void replace(Driver driver, SessionConfig config, Consumer<Session> write) {
    withSession(driver, config, Neo4jRepositoryPhase.REPLACEMENT, Neo4jRepositoryPhase.PUBLICATION_SESSION_CLOSE, session -> {
      knowledge = CommitKnowledge.UNKNOWN;
      driverWork(Neo4jRepositoryPhase.REPLACEMENT, () -> {
        write.accept(session);
        return null;
      });
      knowledge = CommitKnowledge.KNOWN_COMMIT;
      return null;
    });
  }

  void closeDriver(Driver driver, String description) {
    work(Neo4jRepositoryPhase.REPOSITORY_CLOSE, () -> inDriverRegion(() -> {
      driver.close();
      return null;
    }, description));
  }

  private static <T extends @Nullable Object> T inDriverRegion(Supplier<T> action, String description) {
    try {
      return action.get();
    } catch (Neo4jException failure) {
      throw new RepositoryException(description, failure);
    }
  }
}
