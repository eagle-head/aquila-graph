package org.aquilagraph.repository.internal.neo4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
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
          callback.execute(transaction());
          events.add("callback.return");
        }
        if (writeFailure != null) { throw writeFailure; }
        events.add("write.acknowledged");
        yield null;
      }
      case "executeRead" -> ((TransactionCallback<?>) arguments[0]).execute(transaction());
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

  private TransactionContext transaction() {
    return proxy(TransactionContext.class, (ignored, method, arguments) -> {
      if (!method.getName().equals("run")) { throw new AssertionError(method.getName()); }
      var query = (String) arguments[0];
      queries.add(query);
      statements.add(new Statement(query, arguments.length > 1 ? Values.value(arguments[1]) : Values.value(Map.of())));
      if (query.contains("RETURN properties(result)") && readFailure != null) { throw readFailure; }
      var rows = query.contains("RETURN properties(result)") && !version.equals("absent")
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
        if (query.contains("RETURN properties(result)")) {
          rows = List.of(record(Map.of("properties", stored.root().properties())));
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
      if (query.contains("RETURN properties(result)") && rootProperties != null) {
        rows = rootProperties.stream().map(properties -> record(Map.of("properties", properties))).toList();
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

  private static Result result(List<Record> records) {
    return result(records, null);
  }

  private static Result result(List<Record> records, RuntimeException streamFailure) {
    return proxy(Result.class, (ignored, method, arguments) -> switch (method.getName()) {
      case "consume" -> null;
      case "stream" -> streamFailure == null ? records.stream() : Stream.concat(records.stream(), Stream.<Record>generate(() -> {
        throw streamFailure;
      }).limit(1));
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
