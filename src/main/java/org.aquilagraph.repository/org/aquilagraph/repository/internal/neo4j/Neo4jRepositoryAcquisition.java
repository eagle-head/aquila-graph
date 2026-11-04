package org.aquilagraph.repository.internal.neo4j;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.internal.repository.RepositoryOperationBoundary;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;

/** Acquires connection resources and closes the driver until a repository takes ownership. */
final class Neo4jRepositoryAcquisition {
  private final Supplier<Neo4jConnectionSettings> settings;
  private final Function<Neo4jConnectionSettings, Driver> drivers;
  private final Function<Neo4jConnectionSettings, SessionConfig> configurations;

  Neo4jRepositoryAcquisition(Supplier<Neo4jConnectionSettings> settings, Function<Neo4jConnectionSettings, Driver> drivers,
      Function<Neo4jConnectionSettings, SessionConfig> configurations) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.drivers = Objects.requireNonNull(drivers, "drivers");
    this.configurations = Objects.requireNonNull(configurations, "configurations");
  }

  <T> T open(RepositoryOperationContext context, BiFunction<Driver, SessionConfig, T> owner) {
    var operation = new Neo4jRepositoryOperation(context, "Neo4j driver initialization failed");
    var configuration = operation.acquire(settings);
    return RepositoryOperationBoundary.transfer(operation.acquire(() -> drivers.apply(configuration)), driver -> {
      var sessionConfiguration = operation.acquire(() -> configurations.apply(configuration));
      return operation.work(Neo4jRepositoryPhase.ACQUISITION, () -> owner.apply(driver, sessionConfiguration));
    }, driver -> operation.closeDriver(driver, "Neo4j partial driver shutdown failed"));
  }

  static Driver connect(Neo4jConnectionSettings settings) {
    return GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
  }
}
