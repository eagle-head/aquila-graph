package org.aquilagraph.repository.neo4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.aquilagraph.application.AstLookup;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationKind;
import org.aquilagraph.application.RepositoryPhase;
import org.aquilagraph.application.port.AstResultRepository;
import org.aquilagraph.application.port.RepositoryOperationBoundary;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.Neo4jException;

/** One owned AST save or lookup with exact phase-bearing failures and conservative commit knowledge. */
public final class Neo4jAstResultRepository implements AstResultRepository {
  private static final int REQUIRED_ROOT_COUNT = 1;
  private static final String PROPERTIES_FIELD = "properties";
  private final Driver driver;
  private final SessionConfig sessionConfiguration;
  private final RepositoryOperationContext context;
  private final Neo4jAstResultCodec codec;
  private CommitKnowledge knowledge = CommitKnowledge.NOT_STARTED;
  private boolean operationStarted;

  Neo4jAstResultRepository(Driver driver, SessionConfig configuration, RepositoryOperationContext context) {
    this.driver = Objects.requireNonNull(driver, "driver");
    sessionConfiguration = Objects.requireNonNull(configuration, "configuration");
    this.context = Objects.requireNonNull(context, "context");
    codec = new Neo4jAstResultCodec();
  }

  /**
   * Stores only the exact complete source subject after immutable mapping and schema-session closure.
   *
   * @param outcome
   *          complete admitted source outcome
   */
  @Override
  public void saveComplete(AstOutcome outcome) {
    observed(RepositoryPhase.VALIDATION, false, () -> {
      begin(RepositoryOperationKind.SAVE_AST);
      if (Objects.requireNonNull(outcome, "outcome").completion() != AstCompletion.COMPLETE
          || !context.astIdentity().equals(outcome.result().orElseThrow().identity())) {
        throw new IllegalArgumentException("AST save requires the operation's exact complete source subject");
      }
      return null;
    });
    var projection = observed(RepositoryPhase.MAPPING, false, () -> codec.map(outcome));
    try (var owned = session(RepositoryPhase.SCHEMA, RepositoryPhase.SCHEMA_SESSION_CLOSE)) {
      observed(RepositoryPhase.SCHEMA, true, () -> {
        ensureSchema(owned.session);
        return null;
      });
    }
    try (var owned = session(RepositoryPhase.REPLACEMENT, RepositoryPhase.PUBLICATION_SESSION_CLOSE)) {
      knowledge = CommitKnowledge.UNKNOWN;
      observed(RepositoryPhase.REPLACEMENT, true, () -> owned.session.executeWrite(transaction -> {
        replace(transaction, projection);
        return null;
      }));
      knowledge = CommitKnowledge.KNOWN_COMMIT;
    }
  }

  /**
   * Looks up one exact identity; normal results are returned only after read-session closure.
   *
   * @param identity
   *          exact requested source identity
   * @return admitted found outcome, absence, or explicit unsupported format
   */
  @Override
  public AstLookup find(AstIdentity identity) {
    observed(RepositoryPhase.VALIDATION, false, () -> {
      begin(RepositoryOperationKind.QUERY_AST);
      if (!context.astIdentity().equals(identity)) {
        throw new IllegalArgumentException("AST query differs from the operation's exact requested identity");
      }
      return null;
    });
    try (var owned = session(RepositoryPhase.READ, RepositoryPhase.READ_SESSION_CLOSE)) {
      var stored = observed(RepositoryPhase.READ, true, () -> owned.session.executeRead(transaction -> read(transaction, identity)));
      return observed(RepositoryPhase.DECODE, false, () -> lookup(identity, stored));
    }
  }

  /** Closes the owned driver while retaining prior replacement knowledge. */
  @Override
  public void close() {
    observed(RepositoryPhase.REPOSITORY_CLOSE, true, () -> {
      driver.close();
      return null;
    });
  }

  private void begin(RepositoryOperationKind kind) {
    if (operationStarted || context.kind() != kind) {
      throw new IllegalArgumentException("An AST repository has one exact operation lifetime");
    }
    operationStarted = true;
  }

  private PhasedSession session(RepositoryPhase opening, RepositoryPhase closing) {
    return observed(opening, true, () -> new PhasedSession(driver.session(sessionConfiguration), closing));
  }

  private <T> T observed(RepositoryPhase phase, boolean driverRegion, Supplier<T> action) {
    return RepositoryOperationBoundary.observe(context, phase, () -> knowledge, action,
        primary -> driverRegion && primary instanceof Neo4jException
            ? new RepositoryException("Neo4j AST repository operation failed", primary)
            : primary);
  }

  private static void ensureSchema(Session session) {
    session.run("CREATE CONSTRAINT ast_entity_identity IF NOT EXISTS FOR (entity:AstEntity) "
        + "REQUIRE (entity.astResultKey, entity.key) IS UNIQUE").consume();
    session.run("CREATE CONSTRAINT ast_result_identity IF NOT EXISTS FOR (result:AstResult) REQUIRE result.astResultKey IS UNIQUE")
        .consume();
    var definitions = session.run(
        "SHOW CONSTRAINTS YIELD name, type, entityType, labelsOrTypes, properties "
            + "WHERE name IN $names RETURN name, type, entityType, labelsOrTypes, properties",
        Values.parameters("names", List.of("ast_entity_identity", "ast_result_identity"))).list();
    Neo4jAstSchema.requireConstraints(definitions);
  }

  private static void replace(TransactionContext transaction, Neo4jAstProjection projection) {
    var scope = projection.identity().astResultKey();
    transaction.run("MATCH (entity:AstEntity {astResultKey: $scope}) DETACH DELETE entity", Values.parameters("scope", scope)).consume();
    var nodeGroups = new LinkedHashMap<String, List<Map<String, Object>>>();
    projection.nodes().stream().map(Neo4jNode::label).distinct().forEach(label -> nodeGroups.put(label,
        projection.nodes().stream().filter(node -> node.label().equals(label)).map(Neo4jNode::properties).toList()));
    nodeGroups.forEach((label, nodes) -> transaction
        .run("UNWIND $nodes AS properties CREATE (entity:AstEntity:`" + label + "`) SET entity = properties", Map.of("nodes", nodes))
        .consume());
    var relationshipGroups = new LinkedHashMap<String, List<Neo4jRelationship>>();
    projection.relationships().stream().map(Neo4jRelationship::type).distinct().forEach(type -> relationshipGroups.put(type,
        projection.relationships().stream().filter(relationship -> relationship.type().equals(type)).toList()));
    relationshipGroups.forEach((type, relationships) -> transaction
        .run(
            "UNWIND $relationships AS item MATCH (source:AstEntity {astResultKey: $scope, key: item.sourceKey}), "
                + "(target:AstEntity {astResultKey: $scope, key: item.targetKey}) CREATE (source)-[relation:`" + type
                + "`]->(target) SET relation = item.properties",
            Map.of("scope", scope, "relationships", relationships.stream().map(Neo4jAstResultRepository::relationshipProperties).toList()))
        .consume());
  }

  private static Map<String, Object> relationshipProperties(Neo4jRelationship relationship) {
    return Map.of("sourceKey", relationship.sourceKey(), "targetKey", relationship.targetKey(), PROPERTIES_FIELD,
        relationship.properties());
  }

  private static StoredAst read(TransactionContext transaction, AstIdentity identity) {
    var parameters = Values.parameters("scope", identity.astResultKey());
    var roots = transaction
        .run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) RETURN properties(result) AS properties", parameters).list();
    if (roots.isEmpty()) {
      var remaining = transaction.run("MATCH (entity:AstEntity {astResultKey: $scope}) RETURN count(entity) AS count", parameters).single()
          .get("count").asLong();
      return new StoredAst(Optional.empty(), remaining > 0, List.of(), List.of());
    }
    if (roots.size() != REQUIRED_ROOT_COUNT) {
      throw new IllegalArgumentException("AST lookup requires one exact stored result root");
    }
    var root = roots.getFirst().get(PROPERTIES_FIELD).asMap();
    if (!(root.get("formatVersion") instanceof String format) || !AstResult.supportsSourceModelVersion(format)) {
      return new StoredAst(Optional.of(root), true, List.of(), List.of());
    }
    var nodes = transaction.run("MATCH (entity:AstEntity {astResultKey: $scope}) "
        + "RETURN entity.key AS key, labels(entity) AS labels, properties(entity) AS properties", parameters).list();
    var relationships = transaction.run(
        "MATCH (source)-[relation]->(target) "
            + "WHERE (source:AstEntity AND source.astResultKey = $scope) OR relation.astResultKey = $scope "
            + "OR (target:AstEntity AND target.astResultKey = $scope) "
            + "RETURN relation.key AS key, type(relation) AS type, source.key AS sourceKey, target.key AS targetKey, "
            + "labels(source) AS sourceLabels, source.astResultKey AS sourceAstResultKey, "
            + "labels(target) AS targetLabels, target.astResultKey AS targetAstResultKey, " + "properties(relation) AS properties",
        parameters).list();
    return new StoredAst(Optional.of(root), true, nodes, relationships);
  }

  private static Neo4jNode storedNode(Record record) {
    var labels = record.get("labels").asList(value -> value.asString());
    if (labels.size() != 2 || !labels.contains("AstEntity")) {
      throw new IllegalArgumentException("AST storage requires its exact namespace and one closed domain label");
    }
    return new Neo4jNode(record.get("key").asString(),
        labels.stream().filter(label -> !"AstEntity".equals(label)).findFirst().orElseThrow(), record.get(PROPERTIES_FIELD).asMap());
  }

  private AstLookup lookup(AstIdentity identity, StoredAst stored) {
    if (stored.root().isEmpty()) {
      if (stored.hasScopedEntities()) {
        throw new IllegalArgumentException("Stored AST entities have no exact result root");
      }
      return new AstLookup(identity, AstLookupStatus.NOT_FOUND, Optional.empty(), Optional.empty());
    }
    var version = stored.root().orElseThrow().get("formatVersion");
    if (!(version instanceof String format) || format.isBlank()) {
      throw new IllegalArgumentException("Stored AST result requires an explicit format version");
    }
    if (!AstResult.supportsSourceModelVersion(format)) {
      return new AstLookup(identity, AstLookupStatus.INCOMPATIBLE, Optional.empty(), Optional.of(format));
    }
    var sourceProfile = stored.root().orElseThrow().get("astProfileVersion");
    if (sourceProfile instanceof String profile) {
      if (!profile.equals(identity.astProfileVersion())) {
        throw new IllegalArgumentException("Stored AST source profile differs from the exact requested identity");
      }
      if (!new AstProfile(profile).isSupported()) {
        return new AstLookup(identity, AstLookupStatus.INCOMPATIBLE, Optional.empty(), Optional.of(profile));
      }
    }
    var nodes = stored.nodes().stream().map(Neo4jAstResultRepository::storedNode).toList();
    var relationships = stored.relationships().stream().map(record -> storedRelationship(identity, record)).toList();
    var outcome = codec.decode(new Neo4jAstProjection(identity, nodes, relationships), identity);
    return new AstLookup(identity, AstLookupStatus.FOUND, Optional.of(outcome), Optional.empty());
  }

  private static Neo4jRelationship storedRelationship(AstIdentity identity, Record record) {
    requireOwnedEndpoint(identity, record, "source");
    requireOwnedEndpoint(identity, record, "target");
    return new Neo4jRelationship(record.get("key").asString(), record.get("type").asString(), record.get("sourceKey").asString(),
        record.get("targetKey").asString(), record.get(PROPERTIES_FIELD).asMap());
  }

  private static void requireOwnedEndpoint(AstIdentity identity, Record record, String endpoint) {
    var labels = record.get(endpoint + "Labels").asList(value -> value.asString());
    if (!labels.contains("AstEntity") || !identity.astResultKey().equals(record.get(endpoint + "AstResultKey").asObject())) {
      throw new IllegalArgumentException("AST storage relationships require endpoints in their exact owned namespace");
    }
  }

  private record StoredAst(Optional<Map<String, Object>> root, boolean hasScopedEntities, List<Record> nodes, List<Record> relationships) {
  }

  private final class PhasedSession implements AutoCloseable {
    private final Session session;
    private final RepositoryPhase closingPhase;

    private PhasedSession(Session session, RepositoryPhase closingPhase) {
      this.session = Objects.requireNonNull(session, "session");
      this.closingPhase = closingPhase;
    }

    @Override
    public void close() {
      observed(closingPhase, true, () -> {
        session.close();
        return null;
      });
    }
  }
}
