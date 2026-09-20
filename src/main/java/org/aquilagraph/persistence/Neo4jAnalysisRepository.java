package org.aquilagraph.persistence;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aquilagraph.application.AnalysisRepository;
import org.aquilagraph.application.AnalysisRepositoryException;
import org.aquilagraph.application.PersistenceReceipt;
import org.aquilagraph.model.AnalysisRun;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.Neo4jException;

/** Neo4j-backed adapter for publishing completed analysis runs atomically. */
public final class Neo4jAnalysisRepository implements AnalysisRepository {
  private static final List<Logger> NEO4J_LOGGERS = List.of(Logger.getLogger("org.neo4j.driver"),
      Logger.getLogger("org.neo4j.bolt.connection"));
  private static final Set<String> NODE_LABELS = Set.of("AnalysisRun", "InputSnapshot", "AnalysisEnvironmentSnapshot", "DependencySnapshot",
      "CompilerConfiguration", "SourceFile", "Program", "JavaType", "JavaMethod", "JavaParameter", "MethodBody", "SyntaxNode",
      "SemanticReference", "MethodAnalysis", "ControlFlowGraph", "FlowValue", "EntryNode", "OperationNode", "ExitNode", "Diagnostic",
      "SourceOrigin");
  private static final Set<String> RELATIONSHIP_TYPES = Set.of("HAS_INPUT", "HAS_ENVIRONMENT", "HAS_CONFIGURATION", "HAS_CLASS_PATH_ENTRY",
      "HAS_MODULE_PATH_ENTRY", "HAS_SOURCE", "HAS_PROGRAM", "HAS_DIAGNOSTIC", "DECLARES_TYPE", "DECLARES_METHOD", "DECLARES_PARAMETER",
      "HAS_BODY", "HAS_SYNTAX_ROOT", "CHILD", "HAS_REFERENCE", "HAS_ORIGIN", "IN_SOURCE", "HAS_METHOD_ANALYSIS", "ANALYZES_METHOD",
      "HAS_VALUE", "USES_VALUE", "DEFINES_VALUE", "HAS_CFG", "HAS_NODE", "NORMAL_FLOW", "TRUE_FLOW", "FALSE_FLOW", "SWITCH_FLOW",
      "RETURN_FLOW", "EXCEPTIONAL_FLOW");

  private final Driver driver;
  private final SessionConfig sessionConfig;
  private final Function<AnalysisRun, Neo4jGraphProjection> projector;

  /**
   * Opens a repository from validated connection settings.
   *
   * @param settings
   *          Neo4j connection settings
   * @throws AnalysisRepositoryException
   *           if driver initialization fails
   */
  public Neo4jAnalysisRepository(Neo4jConnectionSettings settings) {
    this(createDriver(Objects.requireNonNull(settings, "settings")), SessionConfig.forDatabase(settings.database()),
        new AnalysisRunProjector()::project);
  }

  Neo4jAnalysisRepository(Driver driver, SessionConfig sessionConfig, Function<AnalysisRun, Neo4jGraphProjection> projector) {
    this.driver = Objects.requireNonNull(driver, "driver");
    this.sessionConfig = Objects.requireNonNull(sessionConfig, "sessionConfig");
    this.projector = Objects.requireNonNull(projector, "projector");
  }

  /**
   * Replaces any existing projection for the run identifier in one write transaction.
   *
   * @param run
   *          completed analysis run
   * @return persisted node and relationship counts
   * @throws AnalysisRepositoryException
   *           if Neo4j rejects the operation
   */
  @Override
  public PersistenceReceipt persist(AnalysisRun run) {
    var projection = projector.apply(run);
    try {
      ensureSchema();
      persistProjection(projection);
      return new PersistenceReceipt(projection.runId(), projection.nodes().size(), projection.relationships().size());
    } catch (Neo4jException exception) {
      throw new AnalysisRepositoryException("Neo4j persistence failed", exception);
    }
  }

  private void persistProjection(Neo4jGraphProjection projection) {
    try (var session = driver.session(sessionConfig)) {
      session.executeWrite(transaction -> {
        transaction
            .run("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity", Values.parameters("runId", projection.runId()))
            .consume();
        for (var node : projection.nodes()) {
          requireAllowed(node.label(), NODE_LABELS, "node label");
          transaction.run("CREATE (entity:AnalysisEntity:`" + node.label() + "`) SET entity = $properties",
              Values.parameters("properties", node.properties())).consume();
        }
        for (var relationship : projection.relationships()) {
          requireAllowed(relationship.type(), RELATIONSHIP_TYPES, "relationship type");
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
    }
  }

  private static Driver createDriver(Neo4jConnectionSettings settings) {
    NEO4J_LOGGERS.forEach(logger -> logger.setLevel(Level.WARNING));
    try {
      return GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
    } catch (Neo4jException exception) {
      throw new AnalysisRepositoryException("Neo4j driver initialization failed", exception);
    }
  }

  private static void requireAllowed(String value, Set<String> allowed, String kind) {
    if (!allowed.contains(value)) {
      throw new IllegalArgumentException("Unsupported " + kind + ": " + value);
    }
  }

  /**
   * Closes the Neo4j driver.
   *
   * @throws AnalysisRepositoryException
   *           if driver shutdown fails
   */
  @Override
  public void close() {
    try {
      driver.close();
    } catch (Neo4jException exception) {
      throw new AnalysisRepositoryException("Neo4j driver shutdown failed", exception);
    }
  }
}
