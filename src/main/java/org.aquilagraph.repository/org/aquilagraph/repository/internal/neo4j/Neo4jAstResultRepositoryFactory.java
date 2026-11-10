package org.aquilagraph.repository.internal.neo4j;

import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationKind;
import org.aquilagraph.application.port.AstQueryRepository;
import org.aquilagraph.application.port.AstRetentionRepository;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;

/** Lazy acquisition that retains ownership of partial resources until repository transfer. */
public final class Neo4jAstResultRepositoryFactory {
  private final Neo4jRepositoryAcquisition acquisition;

  /**
   * Retains lazy connection settings without acquiring a driver.
   *
   * @param settings
   *          deferred validated settings
   */
  public Neo4jAstResultRepositoryFactory(Supplier<Neo4jConnectionSettings> settings) {
    this(settings, Neo4jRepositoryAcquisition::connect, value -> SessionConfig.forDatabase(value.database()));
  }

  Neo4jAstResultRepositoryFactory(Supplier<Neo4jConnectionSettings> settings, Function<Neo4jConnectionSettings, Driver> drivers,
      Function<Neo4jConnectionSettings, SessionConfig> configurations) {
    acquisition = new Neo4jRepositoryAcquisition(settings, drivers, configurations);
  }

  /**
   * Acquires and transfers a repository for one complete AST save.
   *
   * @param context
   *          SAVE_AST and its exact source subject
   * @return transferred owned repository
   */
  public AstRetentionRepository openRetention(RepositoryOperationContext context) {
    return acquire(context, RepositoryOperationKind.SAVE_AST);
  }

  /**
   * Acquires and transfers a repository for one exact AST lookup.
   *
   * @param context
   *          QUERY_AST and its exact source subject
   * @return transferred owned repository
   */
  public AstQueryRepository openQuery(RepositoryOperationContext context) {
    return acquire(context, RepositoryOperationKind.QUERY_AST);
  }

  private Neo4jAstResultRepository acquire(RepositoryOperationContext context, RepositoryOperationKind kind) {
    Objects.requireNonNull(context, "context");
    if (context.kind() != kind) {
      throw new IllegalArgumentException("AST repository requires " + kind);
    }
    return acquisition.open(context, (driver, config) -> new Neo4jAstResultRepository(driver, config, context));
  }
}
