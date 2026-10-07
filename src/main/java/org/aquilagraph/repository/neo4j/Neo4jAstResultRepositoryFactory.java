package org.aquilagraph.repository.neo4j;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryPhase;
import org.aquilagraph.application.port.AstResultRepository;
import org.aquilagraph.application.port.AstResultRepositoryFactory;
import org.aquilagraph.application.port.RepositoryOperationBoundary;
import org.aquilagraph.application.port.RepositoryException;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;

/** Lazy acquisition that retains ownership of partial resources until repository transfer. */
public final class Neo4jAstResultRepositoryFactory implements AstResultRepositoryFactory {
  private final Supplier<Neo4jConnectionSettings> settings;
  private final Function<Neo4jConnectionSettings, Driver> drivers;
  private final Function<Neo4jConnectionSettings, SessionConfig> configurations;

  /**
   * Retains lazy connection settings without acquiring a driver.
   *
   * @param settings
   *          deferred validated settings
   */
  public Neo4jAstResultRepositoryFactory(Supplier<Neo4jConnectionSettings> settings) {
    this(settings, value -> GraphDatabase.driver(value.uri(), AuthTokens.basic(value.username(), value.password())),
        value -> SessionConfig.forDatabase(value.database()));
  }

  Neo4jAstResultRepositoryFactory(Supplier<Neo4jConnectionSettings> settings, Function<Neo4jConnectionSettings, Driver> drivers,
      Function<Neo4jConnectionSettings, SessionConfig> configurations) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.drivers = Objects.requireNonNull(drivers, "drivers");
    this.configurations = Objects.requireNonNull(configurations, "configurations");
  }

  /**
   * Acquires and transfers a repository for one exact operation.
   *
   * @param context
   *          exact subject and operation
   * @return transferred owned repository
   */
  @Override
  public AstResultRepository open(RepositoryOperationContext context) {
    Objects.requireNonNull(context, "context");
    var configuration = acquire(context, settings);
    var driver = acquire(context, () -> drivers.apply(configuration));
    try (var partial = new PartialDriver(driver, context)) {
      var sessionConfiguration = acquire(context, () -> configurations.apply(configuration));
      var repository = acquire(context, () -> new Neo4jAstResultRepository(driver, sessionConfiguration, context));
      partial.transferred = true;
      return repository;
    }
  }

  private static <T> T acquire(RepositoryOperationContext context, Supplier<T> action) {
    return RepositoryOperationBoundary.observe(context, RepositoryPhase.ACQUISITION, () -> CommitKnowledge.NOT_STARTED,
        () -> Objects.requireNonNull(action.get(), "acquired resource"), primary -> primary);
  }

  private static final class PartialDriver implements AutoCloseable {
    private final Driver driver;
    private final RepositoryOperationContext context;
    private boolean transferred;

    private PartialDriver(Driver driver, RepositoryOperationContext context) {
      this.driver = driver;
      this.context = context;
    }

    @Override
    public void close() {
      if (!transferred) {
        RepositoryOperationBoundary.observe(context, RepositoryPhase.REPOSITORY_CLOSE, () -> CommitKnowledge.NOT_STARTED, () -> {
          driver.close();
          return null;
        }, primary -> primary instanceof Neo4jException
            ? new RepositoryException("Neo4j partial driver shutdown failed", primary)
            : primary);
      }
    }
  }
}
