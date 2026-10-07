package org.aquilagraph.repository.neo4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.application.AnalysisRun;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.Neo4jException;

/** Neo4j repository for storing completed analysis runs atomically. */
public final class Neo4jAnalysisRunRepository implements AnalysisRunRepository {
  private static final List<Logger> NEO4J_LOGGERS = List.of(Logger.getLogger("org.neo4j.driver"),
      Logger.getLogger("org.neo4j.bolt.connection"));

  private final Driver driver;
  private final SessionConfig sessionConfig;
  private final Function<AnalysisRun, Neo4jGraphProjection> mapper;

  /**
   * Opens a repository from validated Neo4j connection settings.
   *
   * @param settings
   *          Neo4j connection settings
   * @throws RepositoryException
   *           if driver initialization fails
   */
  public Neo4jAnalysisRunRepository(Neo4jConnectionSettings settings) {
    this(acquire(settings, Neo4jAnalysisRunRepository::createDriver, value -> SessionConfig.forDatabase(value.database())));
  }

  Neo4jAnalysisRunRepository(Neo4jConnectionSettings settings, Function<Neo4jConnectionSettings, Driver> driverFactory,
      Function<Neo4jConnectionSettings, SessionConfig> sessionConfigFactory) {
    this(acquire(settings, driverFactory, sessionConfigFactory));
  }

  private Neo4jAnalysisRunRepository(RepositoryResources resources) {
    this(resources.driver(), resources.sessionConfig(), resources.mapper());
  }

  Neo4jAnalysisRunRepository(Driver driver, SessionConfig sessionConfig, Function<AnalysisRun, Neo4jGraphProjection> mapper) {
    this.driver = Objects.requireNonNull(driver, "driver");
    this.sessionConfig = Objects.requireNonNull(sessionConfig, "sessionConfig");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  /**
   * Replaces the Neo4j projection for the supplied run identifier in one write transaction.
   *
   * @param run
   *          completed analysis run
   * @throws RepositoryException
   *           if Neo4j rejects the operation
   */
  @Override
  public void save(AnalysisRun run) {
    var projection = mapper.apply(run);
    try {
      ensureSchema();
      saveProjection(projection);
    } catch (Neo4jException exception) {
      throw new RepositoryException("Neo4j repository save failed", exception);
    }
  }

  private void saveProjection(Neo4jGraphProjection projection) {
    try (var session = driver.session(sessionConfig)) {
      session.executeWrite(transaction -> {
        transaction
            .run("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity", Values.parameters("runId", projection.runId()))
            .consume();
        for (var node : projection.nodes()) {
          Neo4jSchema.requireNodeLabel(node.label());
          transaction.run("CREATE (entity:AnalysisEntity:`" + node.label() + "`) SET entity = $properties",
              Values.parameters("properties", node.properties())).consume();
        }
        for (var relationship : projection.relationships()) {
          Neo4jSchema.requireRelationshipType(relationship.type());
          transaction.run(
              "MATCH (source:AnalysisEntity {runId: $runId, key: $sourceKey}), "
                  + "(target:AnalysisEntity {runId: $runId, key: $targetKey}) " + "CREATE (source)-[relation:`" + relationship.type()
                  + "`]->(target) SET relation = $properties",
              Map.of("runId", projection.runId(), "sourceKey", relationship.sourceKey(), "targetKey", relationship.targetKey(),
                  "properties", relationship.properties()))
              .consume();
        }
        return null;
      });
    }
  }

  private void ensureSchema() {
    try (var session = driver.session(sessionConfig)) {
      session.run("CREATE CONSTRAINT analysis_entity_identity IF NOT EXISTS "
          + "FOR (entity:AnalysisEntity) REQUIRE (entity.runId, entity.key) IS UNIQUE").consume();
      session.run("CREATE CONSTRAINT analysis_run_identity IF NOT EXISTS " + "FOR (run:AnalysisRun) REQUIRE run.runId IS UNIQUE").consume();
      var definitions = session.run(
          "SHOW CONSTRAINTS YIELD name, type, entityType, labelsOrTypes, properties "
              + "WHERE name IN $names RETURN name, type, entityType, labelsOrTypes, properties",
          Values.parameters("names", List.of("analysis_entity_identity", "analysis_run_identity"))).list();
      Neo4jSchema.requireIdentityConstraints(definitions);
    }
  }

  private static RepositoryResources acquire(Neo4jConnectionSettings settings, Function<Neo4jConnectionSettings, Driver> driverFactory,
      Function<Neo4jConnectionSettings, SessionConfig> sessionConfigFactory) {
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(driverFactory, "driverFactory");
    Objects.requireNonNull(sessionConfigFactory, "sessionConfigFactory");
    try (var acquisition = new DriverAcquisition(driverFactory.apply(settings))) {
      try {
        var config = Objects.requireNonNull(sessionConfigFactory.apply(settings), "sessionConfig");
        var resources = new RepositoryResources(acquisition.driver, config, new Neo4jAnalysisRunMapper()::map);
        acquisition.transferred = true;
        return resources;
      } catch (Neo4jException exception) {
        throw new RepositoryException("Neo4j driver initialization failed", exception);
      }
    }
  }

  private static void closeDriver(Driver acquiredDriver) {
    try {
      acquiredDriver.close();
    } catch (Neo4jException exception) {
      throw new RepositoryException("Neo4j driver shutdown failed", exception);
    }
  }

  private record RepositoryResources(Driver driver, SessionConfig sessionConfig, Function<AnalysisRun, Neo4jGraphProjection> mapper) {
  }

  private static final class DriverAcquisition implements AutoCloseable {
    private final Driver driver;
    private boolean transferred;

    private DriverAcquisition(Driver driver) {
      this.driver = Objects.requireNonNull(driver, "driver");
    }

    @Override
    public void close() {
      if (!transferred) {
        closeDriver(driver);
      }
    }
  }

  private static Driver createDriver(Neo4jConnectionSettings settings) {
    NEO4J_LOGGERS.forEach(logger -> logger.setLevel(Level.WARNING));
    try {
      return GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
    } catch (Neo4jException exception) {
      throw new RepositoryException("Neo4j driver initialization failed", exception);
    }
  }

  /**
   * Closes the Neo4j driver.
   *
   * @throws RepositoryException
   *           if driver shutdown fails
   */
  @Override
  public void close() {
    closeDriver(driver);
  }
}
