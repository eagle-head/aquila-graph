package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.AstIdentity;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

/** Supplies driver protocol outcomes without executing queries or database commits. */
final class AstRepositoryDriverStub {
  private static final Pattern AST_NODE_INSERT = Pattern.compile(
      "UNWIND \\$nodes AS expected CREATE \\(entity:AstEntity:`([^`]+)`\\) SET entity = expected "
          + "RETURN entity.key AS key, elementId\\(entity\\) AS address");
  private static final Pattern AST_RELATIONSHIP_INSERT = Pattern.compile(
      "UNWIND \\$relationships AS item MATCH \\(source\\), \\(target\\) "
          + "WHERE elementId\\(source\\) = item.source AND elementId\\(target\\) = item.target "
          + "CREATE \\(source\\)-\\[relation:`([^`]+)`\\]->\\(target\\) SET relation = item.properties");
  private static final String AST_ROOT_UPDATE = "UNWIND $nodes AS item MATCH (entity) WHERE elementId(entity) = item.address "
      + "SET entity:`AstResult`, entity = item.properties";
  final List<String> events = new ArrayList<>();
  final List<String> queries = new ArrayList<>();
  final List<Statement> statements = new ArrayList<>();
  final List<Statement> schemaStatements = new ArrayList<>();
  final List<String> completedSchemaStatements = new ArrayList<>();
  final List<SessionConfig> sessionConfigurations = new ArrayList<>();
  private final List<Record> constraints;
  private int sessions;
  int attempts = 1;
  int failingClose = -1;
  RuntimeException schemaFailure;
  Consumer<String> onSchemaResult = ignored -> {};
  RuntimeException readFailure;
  RuntimeException writeFailure;
  RuntimeException closeFailure;
  RuntimeException driverCloseFailure;
  RuntimeException nodeStreamFailure;
  RuntimeException relationshipStreamFailure;
  BiFunction<Integer, Statement, Result> replacementResult;
  boolean malformedNodeRecords;
  String version = "absent";
  String lockedAstSubject;
  long scopedNodeCount;
  Neo4jAstProjection stored;
  List<Map<String, Object>> rootProperties;
  UnaryOperator<List<Map<String, Object>>> nodeRows = UnaryOperator.identity();
  UnaryOperator<List<Map<String, Object>>> relationshipRows = UnaryOperator.identity();

  AstRepositoryDriverStub() {
    this(List.of(constraint("ast_entity_identity", "AstEntity", List.of("astResultKey", "key")),
        constraint("ast_result_identity", "AstResult", List.of("astResultKey"))));
  }

  AstRepositoryDriverStub(List<Record> constraints) {
    this.constraints = List.copyOf(constraints);
  }

  static AstRepositoryDriverStub completedRuns() {
    return new AstRepositoryDriverStub(List.of(constraint("analysis_entity_identity", "AnalysisEntity", List.of("runId", "key")),
        constraint("analysis_run_identity", "AnalysisRun", List.of("runId"))));
  }

  record Statement(String query, Value parameters) {
  }

  // Replay the root update and insert batches on the controlled read boundary.
  Neo4jAstProjection writtenAst(AstIdentity identity) {
    var rootUpdates = statements.stream().filter(statement -> AST_ROOT_UPDATE.equals(statement.query())).toList();
    assertEquals(1, rootUpdates.size(), "Expected the locked subject to receive its AST label and payload");
    var updates = rootUpdates.getFirst().parameters().get("nodes").asList(value -> value);
    assertEquals(1, updates.size());
    var update = updates.getFirst();
    var rootKey = identity.astResultKey();
    var properties = update.get("properties").asMap();
    assertEquals(Map.of("address", "node/" + rootKey, "properties", properties), update.asMap());
    assertEquals(rootKey, properties.get("key"));
    var root = new Neo4jNode(rootKey, "AstResult", properties);

    var nodeBatches = statements.stream().filter(statement -> AST_NODE_INSERT.matcher(statement.query()).matches()).toList();
    var relationshipBatches = statements.stream().filter(statement -> AST_RELATIONSHIP_INSERT.matcher(statement.query()).matches()).toList();
    // A first save adds one lock, two reconciliation reads, and one root update to the insert batches.
    assertEquals(4 + nodeBatches.size() + relationshipBatches.size(), statements.size());
    var insertedNodes = nodeBatches.stream().flatMap(statement -> AST_NODE_INSERT.matcher(statement.query()).results()
        .flatMap(match -> statement.parameters().get("nodes").asList(value -> value.asMap()).stream()
            .map(row -> new Neo4jNode((String) row.get("key"), match.group(1), row))));
    var nodes = Stream.concat(Stream.of(root), insertedNodes).toList();
    var relationships = relationshipBatches.stream()
        .flatMap(statement -> AST_RELATIONSHIP_INSERT.matcher(statement.query()).results()
            .flatMap(match -> statement.parameters().get("relationships").asList(value -> value).stream().map(row -> {
              var values = row.get("properties").asMap();
              return new Neo4jRelationship((String) values.get("key"), match.group(1), nodeKey(row.get("source").asString()),
                  nodeKey(row.get("target").asString()), values);
            }))).toList();
    return new Neo4jAstProjection(identity, nodes, relationships);
  }

  private static String nodeKey(String address) {
    assertTrue(address.startsWith("node/"), address);
    return address.substring("node/".length());
  }

  Driver driver() {
    return proxy(Driver.class, (ignored, method, arguments) -> switch (method.getName()) {
      case "session" -> {
        sessionConfigurations.add((SessionConfig) arguments[0]);
        yield session(sessions++);
      }
      case "close" -> {
        events.add("driver.close");
        if (driverCloseFailure != null) { throw driverCloseFailure; }
        yield null;
      }
      default -> throw new AssertionError("Unexpected driver operation: " + method.getName());
    });
  }

  private Session session(int ordinal) {
    events.add("session.open:" + ordinal);
    return proxy(Session.class, (ignored, method, arguments) -> switch (method.getName()) {
      case "run" -> {
        var query = (String) arguments[0];
        queries.add(query);
        schemaStatements.add(new Statement(query, arguments.length > 1 ? Values.value(arguments[1]) : Values.value(Map.of())));
        if (schemaFailure != null) { throw schemaFailure; }
        yield schemaResult(query);
      }
      case "executeWrite" -> {
        events.add("write.begin");
        var callback = (TransactionCallback<?>) arguments[0];
        for (var attempt = 0; attempt < attempts; attempt++) {
          callback.execute(transaction(attempt));
          events.add("callback.return");
        }
        if (writeFailure != null) { throw writeFailure; }
        events.add("write.acknowledged");
        yield null;
      }
      case "executeRead" -> ((TransactionCallback<?>) arguments[0]).execute(transaction(0));
      case "close" -> {
        events.add("session.close:" + ordinal);
        if (ordinal == failingClose) { throw closeFailure; }
        yield null;
      }
      default -> throw new AssertionError("Unexpected session operation: " + method.getName());
    });
  }

  private Result schemaResult(String query) {
    return proxy(Result.class, (ignored, method, arguments) -> {
      if (query.startsWith("SHOW CONSTRAINTS") && method.getName().equals("list")) {
        onSchemaResult.accept(query);
        completedSchemaStatements.add(query);
        return constraints;
      }
      if (query.startsWith("CREATE CONSTRAINT") && method.getName().equals("consume")) {
        onSchemaResult.accept(query);
        completedSchemaStatements.add(query);
        return null;
      }
      throw new AssertionError("Unexpected schema result operation: " + method.getName() + " for " + query);
    });
  }

  private TransactionContext transaction(int attempt) {
    return proxy(TransactionContext.class, (ignored, method, arguments) -> {
      if (!method.getName().equals("run")) { throw new AssertionError(method.getName()); }
      var query = (String) arguments[0];
      queries.add(query);
      var statement = new Statement(query, arguments.length > 1 ? Values.value(arguments[1]) : Values.value(Map.of()));
      statements.add(statement);
      if (replacementResult != null) {
        return replacementResult.apply(attempt, statement);
      }
      var readsRootMetadata = query.contains("RETURN result { .formatVersion, .astProfileVersion } AS properties");
      if (readsRootMetadata && readFailure != null) { throw readFailure; }
      var rows = readsRootMetadata && !version.equals("absent")
          ? List.of(record(Map.of("properties", Map.of("formatVersion", version)))) : List.<Record>of();
      if (query.contains("RETURN entity.key AS key, elementId(entity) AS address")) {
        rows = ((Value) arguments[1]).get("nodes").asList(value -> value.asMap()).stream()
            .map(node -> record(Map.of("key", node.get("key"), "address", "node/" + node.get("key")))).toList();
      }
      if (lockedAstSubject != null && query.contains("RETURN elementId(entity) AS address")) {
        var key = lockedAstSubject;
        rows = List.of(record(Map.of("key", key, "address", "node/" + key, "labels", List.of("AstEntity"),
            "properties", Map.of("key", key, "astResultKey", key))));
      } else if (stored != null) {
        if (readsRootMetadata) {
          rows = List.of(rootMetadata(stored.root().properties()));
        } else if (query.contains("labels(entity)")) {
          rows = nodeRows.apply(stored.nodes().stream().map(node -> Map.<String, Object>of("key", node.key(), "labels",
              malformedNodeRecords ? List.of("AstEntity", node.label(), "Unexpected") : List.of("AstEntity", node.label()),
              "properties", node.properties(), "address", "node/" + node.key())).toList()).stream()
              .map(AstRepositoryDriverStub::record).toList();
        } else if (query.contains("elementId(relation) AS address")) {
          rows = stored.relationships().stream().map(relation -> record(Map.of("address", "relation/" + relation.key(),
              "key", relation.key(), "type", relation.type(), "properties", relation.properties(),
              "source", "node/" + relation.sourceKey(), "target", "node/" + relation.targetKey()))).toList();
        } else if (query.contains("type(relation)")) {
          var labelsByKey = new HashMap<String, List<String>>();
          stored.nodes().forEach(node -> labelsByKey.put(node.key(), List.of("AstEntity", node.label())));
          rows = relationshipRows.apply(stored.relationships().stream().map(relation -> Map.<String, Object>of(
              "key", relation.key(), "type", relation.type(),
              "sourceKey", relation.sourceKey(), "targetKey", relation.targetKey(), "properties", relation.properties(),
              "sourceLabels", labelsByKey.get(relation.sourceKey()), "sourceAstResultKey", stored.identity().astResultKey(),
              "targetLabels", labelsByKey.get(relation.targetKey()), "targetAstResultKey", stored.identity().astResultKey())).toList())
              .stream().map(AstRepositoryDriverStub::record).toList();
        }
      }
      if (readsRootMetadata && rootProperties != null) {
        rows = rootProperties.stream().map(AstRepositoryDriverStub::rootMetadata).toList();
      }
      if (query.contains("RETURN count(entity)")) {
        rows = List.of(record(Map.of("count", scopedNodeCount)));
      }
      if (query.contains("labels(entity)")) {
        return result(rows, nodeStreamFailure);
      }
      return result(rows, query.contains("type(relation)") ? relationshipStreamFailure : null);
    });
  }

  private static Record rootMetadata(Map<String, Object> properties) {
    var selected = new HashMap<String, Object>();
    selected.put("formatVersion", properties.get("formatVersion"));
    selected.put("astProfileVersion", properties.get("astProfileVersion"));
    return record(Map.of("properties", selected));
  }

  private static Result result(List<Record> records) {
    return result(records, null);
  }

  private static Result result(List<Record> records, RuntimeException streamFailure) {
    return observedResult(records, streamFailure, () -> {});
  }

  static Result observedResult(List<Record> records, RuntimeException streamFailure, Runnable onCompletion) {
    return proxy(Result.class, (ignored, method, arguments) -> switch (method.getName()) {
      case "consume" -> {
        onCompletion.run();
        yield null;
      }
      case "stream" -> {
        var stream = streamFailure == null ? records.stream() : Stream.concat(records.stream(), Stream.<Record>generate(() -> {
          throw streamFailure;
        }).limit(1));
        yield stream.onClose(onCompletion);
      }
      case "single" -> records.getFirst();
      case "list" -> {
        if (arguments != null && arguments.length != 0) { throw new AssertionError("Unexpected mapped result"); }
        yield records;
      }
      default -> throw new AssertionError("Unexpected result operation: " + method.getName());
    });
  }

  static Record record(Map<String, Object> properties) {
    return proxy(Record.class, (ignored, method, arguments) -> switch (method.getName()) {
      case "get" -> {
        var value = properties.get((String) arguments[0]);
        yield value instanceof Value supplied ? supplied : Values.value(value);
      }
      default -> throw new AssertionError("Unexpected record operation: " + method.getName());
    });
  }

  static Value failingStringValue(RuntimeException failure) {
    return proxy(Value.class, (ignored, method, arguments) -> {
      if (method.getName().equals("asString")) { throw failure; }
      throw new AssertionError("Unexpected value operation: " + method.getName());
    });
  }

  private static Record constraint(String name, String label, List<String> properties) {
    return record(Map.of("name", name, "type", "NODE_PROPERTY_UNIQUENESS", "entityType", "NODE", "labelsOrTypes", List.of(label),
        "properties", properties));
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
  }
}
