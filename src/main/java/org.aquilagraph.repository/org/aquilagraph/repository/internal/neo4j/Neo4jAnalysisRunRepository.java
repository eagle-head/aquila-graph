package org.aquilagraph.repository.internal.neo4j;

import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationKind;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

/** Neo4j repository for storing completed analysis runs atomically. */
public final class Neo4jAnalysisRunRepository implements AnalysisRunRepository {
  private final Driver driver;
  private final SessionConfig sessionConfig;
  private final Function<AnalysisRun, Neo4jGraphProjection> mapper;

  private static final String SAVE_FAILURE = "Neo4j repository save failed";
  private Neo4jRepositoryOperation operation;

  /**
   * Composes lazy completed-run acquisition from deferred Neo4j settings.
   *
   * @param settings
   *          deferred settings
   * @return contextual factory for reusable completed-run storage
   */
  public static AnalysisRunRepositoryFactory factory(Supplier<Neo4jConnectionSettings> settings) {
    return factory(settings, GraphDatabase::driver);
  }

  static AnalysisRunRepositoryFactory factory(Supplier<Neo4jConnectionSettings> settings, BiFunction<URI, AuthToken, Driver> createDriver) {
    return factory(new Neo4jRepositoryAcquisition(settings, createDriver));
  }

  static AnalysisRunRepositoryFactory factory(Supplier<Neo4jConnectionSettings> settings, Function<Neo4jConnectionSettings, Driver> drivers,
      Function<Neo4jConnectionSettings, SessionConfig> configurations) {
    return factory(new Neo4jRepositoryAcquisition(settings, drivers, configurations));
  }

  private static AnalysisRunRepositoryFactory factory(Neo4jRepositoryAcquisition acquisition) {
    var mapper = new Neo4jAnalysisRunMapper();
    return context -> {
      if (Objects.requireNonNull(context, "context").kind() != RepositoryOperationKind.SAVE_COMPLETED_RUN) {
        throw new IllegalArgumentException("Completed-run repository requires a completed-run acquisition context");
      }
      return acquisition.open(context, (driver, config) -> new Neo4jAnalysisRunRepository(driver, config, context, mapper::map));
    };
  }

  Neo4jAnalysisRunRepository(Driver driver, SessionConfig sessionConfig, RepositoryOperationContext context,
      Function<AnalysisRun, Neo4jGraphProjection> mapper) {
    this.driver = Objects.requireNonNull(driver, "driver");
    this.sessionConfig = Objects.requireNonNull(sessionConfig, "sessionConfig");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    operation = new Neo4jRepositoryOperation(context, SAVE_FAILURE);
  }

  /**
   * Replaces the projection for this run's exact identifier and acknowledges managed commit.
   *
   * @param run
   *          completed analysis run
   */
  @Override
  public void save(AnalysisRun run) {
    var context = RepositoryOperationContext.saveCompletedRun(Objects.requireNonNull(run, "run").identity().key());
    var current = new Neo4jRepositoryOperation(context, SAVE_FAILURE);
    operation = current;
    var projection = current.work(Neo4jRepositoryPhase.MAPPING, () -> {
      var mapped = Objects.requireNonNull(mapper.apply(run), "run projection");
      if (!run.identity().key().equals(mapped.runId())) {
        throw new IllegalArgumentException("Run projection differs from the operation's exact analysis subject");
      }
      return mapped;
    });
    current.withSession(driver, sessionConfig, Neo4jRepositoryPhase.SCHEMA, Neo4jRepositoryPhase.SCHEMA_SESSION_CLOSE,
        session -> current.driverWork(Neo4jRepositoryPhase.SCHEMA, () -> {
          Neo4jIdentityConstraints.ANALYSIS_RUN.ensure(session);
          return null;
        }));
    current.replace(driver, sessionConfig, session -> replace(session, projection));
  }

  private static void replace(Session session, Neo4jGraphProjection projection) {
    session.executeWrite(transaction -> {
      transaction.run("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity", Values.parameters("runId", projection.runId()))
          .consume();
      for (var node : projection.nodes()) {
        Neo4jSchema.requireNodeLabel(node.label());
        transaction.run("CREATE (entity:AnalysisEntity:`" + node.label() + "`) SET entity = $properties",
            Values.parameters("properties", node.properties())).consume();
      }
      for (var relationship : projection.relationships()) {
        Neo4jSchema.requireRelationshipType(relationship.type());
        transaction.run(
            "MATCH (source:AnalysisEntity {runId: $runId, key: $sourceKey}), " + "(target:AnalysisEntity {runId: $runId, key: $targetKey}) "
                + "CREATE (source)-[relation:`" + relationship.type() + "`]->(target) SET relation = $properties",
            Map.of("runId", projection.runId(), "sourceKey", relationship.sourceKey(), "targetKey", relationship.targetKey(), "properties",
                relationship.properties()))
            .consume();
      }
      return null;
    });
  }

  /** Closes the driver with the last attempted save observation or acquisition context. */
  @Override
  public void close() {
    operation.closeDriver(driver, "Neo4j driver shutdown failed");
  }
}
