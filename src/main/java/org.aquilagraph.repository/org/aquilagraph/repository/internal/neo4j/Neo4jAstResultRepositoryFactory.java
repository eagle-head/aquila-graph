package org.aquilagraph.repository.internal.neo4j;

import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationKind;
import org.aquilagraph.application.port.AstResultRepository;
import org.aquilagraph.application.port.AstResultRepositoryFactory;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;

/** Lazy acquisition that retains ownership of partial resources until repository transfer. */
public final class Neo4jAstResultRepositoryFactory implements AstResultRepositoryFactory {
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
   * Acquires and transfers a repository for one exact operation.
   *
   * @param context
   *          exact subject and operation
   * @return transferred owned repository
   */
  @Override
  public AstResultRepository open(RepositoryOperationContext context) {
    Objects.requireNonNull(context, "context");
    if (context.kind() == RepositoryOperationKind.SAVE_COMPLETED_RUN) {
      throw new IllegalArgumentException("AST repository requires an AST operation");
    }
    return acquisition.open(context, (driver, config) -> new Neo4jAstResultRepository(driver, config, context));
  }
}
