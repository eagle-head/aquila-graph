package org.aquilagraph.repository.internal.neo4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.aquilagraph.application.AstLookup;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.RepositoryOperationKind;
import org.aquilagraph.application.port.AstQueryRepository;
import org.aquilagraph.application.port.AstRetentionRepository;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.repository.internal.neo4j.Neo4jStoredAstAdmission.MaterializedRoot;
import org.aquilagraph.repository.internal.neo4j.Neo4jStoredAstAdmission.MaterializedRows;
import org.aquilagraph.repository.internal.neo4j.Neo4jStoredAstAdmission.MissingRoot;
import org.aquilagraph.repository.internal.neo4j.Neo4jStoredAstAdmission.StoredRead;
import org.aquilagraph.repository.internal.neo4j.Neo4jStoredAstAdmission.UnmaterializedRoot;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.value.ValueException;

/** One owned AST save or lookup with exact phase-bearing failures and conservative commit knowledge. */
final class Neo4jAstResultRepository implements AstRetentionRepository, AstQueryRepository {
  private static final int REQUIRED_ROOT_COUNT = 1;
  private static final String PROPERTIES_FIELD = "properties";
  private static final String SCOPE_PARAMETER = "scope";
  private final Driver driver;
  private final SessionConfig sessionConfiguration;
  private final RepositoryOperationContext context;
  private final Neo4jStoredAstAdmission admission;
  private final Function<AstOutcome, Neo4jAstProjection> projectionMapper;
  private final AstIdentity subjectIdentity;
  private final Neo4jRepositoryOperation operation;
  private boolean operationStarted;

  Neo4jAstResultRepository(Driver driver, SessionConfig configuration, RepositoryOperationContext context) {
    this(driver, configuration, context, new Neo4jAstResultCodec());
  }

  private Neo4jAstResultRepository(Driver driver, SessionConfig configuration, RepositoryOperationContext context,
      Neo4jAstResultCodec codec) {
    this(driver, configuration, context, codec, codec::map);
  }

  Neo4jAstResultRepository(Driver driver, SessionConfig configuration, RepositoryOperationContext context, Neo4jAstResultCodec codec,
      Function<AstOutcome, Neo4jAstProjection> projectionMapper) {
    this.driver = Objects.requireNonNull(driver, "driver");
    sessionConfiguration = Objects.requireNonNull(configuration, "configuration");
    this.context = Objects.requireNonNull(context, "context");
    if (!(context.subject() instanceof Ast ast)) {
      throw new IllegalArgumentException("AST repository requires an AST operation");
    }
    subjectIdentity = ast.identity();
    operation = new Neo4jRepositoryOperation(context, "Neo4j AST repository operation failed");
    admission = new Neo4jStoredAstAdmission(codec);
    this.projectionMapper = Objects.requireNonNull(projectionMapper, "projectionMapper");
  }

  /**
   * Stores only the exact complete source subject after immutable mapping and schema-session closure.
   *
   * @param outcome
   *          complete admitted source outcome
   */
  @Override
  public void saveComplete(AstOutcome outcome) {
    operation.work(Neo4jRepositoryPhase.VALIDATION, () -> {
      begin(RepositoryOperationKind.SAVE_AST);
      if (Objects.requireNonNull(outcome, "outcome").completion() != AstCompletion.COMPLETE
          || !subjectIdentity.equals(outcome.result().orElseThrow().identity())) {
        throw new IllegalArgumentException("AST save requires the operation's exact complete source subject");
      }
      return null;
    });
    var projection = operation.work(Neo4jRepositoryPhase.MAPPING, () -> {
      var mapped = Objects.requireNonNull(projectionMapper.apply(outcome), "AST projection");
      if (!subjectIdentity.equals(mapped.identity())) {
        throw new IllegalArgumentException("AST projection differs from the operation's exact source subject");
      }
      return mapped;
    });
    operation.withSession(driver, sessionConfiguration, Neo4jRepositoryPhase.SCHEMA, Neo4jRepositoryPhase.SCHEMA_SESSION_CLOSE,
        session -> operation.driverWork(Neo4jRepositoryPhase.SCHEMA, () -> {
          Neo4jIdentityConstraints.AST_RESULT.ensure(session);
          return null;
        }));
    operation.replace(driver, sessionConfiguration, session -> session.executeWrite(transaction -> {
      Neo4jAstMaterialization.replace(transaction, projection);
      return null;
    }));
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
    operation.work(Neo4jRepositoryPhase.VALIDATION, () -> {
      begin(RepositoryOperationKind.QUERY_AST);
      if (!subjectIdentity.equals(identity)) {
        throw new IllegalArgumentException("AST query differs from the operation's exact requested identity");
      }
      return null;
    });
    return operation.withSession(driver, sessionConfiguration, Neo4jRepositoryPhase.READ, Neo4jRepositoryPhase.READ_SESSION_CLOSE,
        session -> {
          var stored = operation.driverWork(Neo4jRepositoryPhase.READ,
              () -> session.executeRead(transaction -> read(transaction, identity)));
          return operation.work(Neo4jRepositoryPhase.DECODE, () -> admission.admit(identity, stored));
        });
  }

  /** Closes the owned driver while retaining prior replacement knowledge. */
  @Override
  public void close() {
    operation.closeDriver(driver, "Neo4j AST repository operation failed");
  }

  private void begin(RepositoryOperationKind kind) {
    if (operationStarted || context.kind() != kind) {
      throw new IllegalArgumentException("An AST repository has one exact operation lifetime");
    }
    operationStarted = true;
  }

  private static StoredRead read(TransactionContext transaction, AstIdentity identity) {
    var parameters = Values.parameters(SCOPE_PARAMETER, identity.astResultKey());
    var roots = transaction
        .run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) RETURN properties(result) AS properties LIMIT 2", parameters)
        .list();
    if (roots.isEmpty()) {
      var remaining = transaction.run("MATCH (entity:AstEntity {astResultKey: $scope}) RETURN count(entity) AS count", parameters).single()
          .get("count").asLong();
      return new MissingRoot(remaining > 0);
    }
    if (roots.size() != REQUIRED_ROOT_COUNT) {
      throw new IllegalArgumentException("AST lookup requires one exact stored result root");
    }
    var root = roots.getFirst().get(PROPERTIES_FIELD).asMap();
    var format = Neo4jStoredAstAdmission.formatVersion(root);
    if (!Neo4jStoredAstAdmission.requiresMaterialization(format)) {
      return new UnmaterializedRoot(format);
    }
    var nodes = readRows(
        transaction.run("MATCH (entity:AstEntity {astResultKey: $scope}) "
            + "RETURN entity.key AS key, labels(entity) AS labels, properties(entity) AS properties", parameters),
        Neo4jAstResultRepository::storedNode);
    var relationships = readRows(transaction.run(
        "MATCH (source)-[relation]->(target) "
            + "WHERE (source:AstEntity AND source.astResultKey = $scope) OR relation.astResultKey = $scope "
            + "OR (target:AstEntity AND target.astResultKey = $scope) "
            + "RETURN relation.key AS key, type(relation) AS type, source.key AS sourceKey, target.key AS targetKey, "
            + "labels(source) AS sourceLabels, source.astResultKey AS sourceAstResultKey, "
            + "labels(target) AS targetLabels, target.astResultKey AS targetAstResultKey, " + "properties(relation) AS properties",
        parameters), record -> storedRelationship(identity, record));
    var decodingFailure = nodes.decodingFailure().or(relationships::decodingFailure);
    var rows = decodingFailure.isEmpty()
        ? Optional.of(new MaterializedRows(nodes.values(), relationships.values()))
        : Optional.<MaterializedRows>empty();
    return new MaterializedRoot(Neo4jStoredAstAdmission.sourceProfile(root), rows, decodingFailure);
  }

  private static <T> ReadRows<T> readRows(Result result, Function<Record, T> decoder) {
    var values = new ArrayList<T>();
    Optional<RuntimeException> decodingFailure = Optional.empty();
    try (var records = result.stream()) {
      var iterator = records.iterator();
      while (iterator.hasNext()) {
        var record = iterator.next();
        if (decodingFailure.isEmpty()) {
          try {
            values.add(decoder.apply(record));
          } catch (IllegalArgumentException | ValueException invalid) {
            decodingFailure = Optional.of(invalid);
            values.clear();
          }
        }
      }
    }
    return new ReadRows<>(values, decodingFailure);
  }

  private static Neo4jNode storedNode(Record record) {
    var label = Neo4jStoredAstAdmission.nodeLabel(record.get("labels").asList(value -> value.asString()));
    return new Neo4jNode(record.get("key").asString(), label, record.get(PROPERTIES_FIELD).asMap());
  }

  private static Neo4jRelationship storedRelationship(AstIdentity identity, Record record) {
    requireOwnedEndpoint(identity, record, "source");
    requireOwnedEndpoint(identity, record, "target");
    return new Neo4jRelationship(record.get("key").asString(), record.get("type").asString(), record.get("sourceKey").asString(),
        record.get("targetKey").asString(), record.get(PROPERTIES_FIELD).asMap());
  }

  private static void requireOwnedEndpoint(AstIdentity identity, Record record, String endpoint) {
    Neo4jStoredAstAdmission.requireEndpointNamespace(record.get(endpoint + "Labels").asList(value -> value.asString()));
    Neo4jStoredAstAdmission.requireEndpointScope(identity, record.get(endpoint + "AstResultKey").asObject());
  }

  private record ReadRows<T>(List<T> values, Optional<RuntimeException> decodingFailure) {
  }
}
