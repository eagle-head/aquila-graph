package org.aquilagraph.repository.internal.neo4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

/** Supplies driver protocol outcomes without executing queries or database commits. */
final class AstRepositoryDriverStub {
  final List<String> events = new ArrayList<>();
  final List<String> queries = new ArrayList<>();
  private int sessions;
  int attempts = 1;
  int failingClose = -1;
  RuntimeException schemaFailure;
  RuntimeException readFailure;
  RuntimeException writeFailure;
  RuntimeException closeFailure;
  RuntimeException driverCloseFailure;
  RuntimeException nodeStreamFailure;
  boolean malformedNodeRecords;
  String version = "absent";
  Neo4jAstProjection stored;
  UnaryOperator<List<Map<String, Object>>> nodeRows = UnaryOperator.identity();
  UnaryOperator<List<Map<String, Object>>> relationshipRows = UnaryOperator.identity();

  Driver driver() {
    return proxy(Driver.class, (ignored, method, arguments) -> switch (method.getName()) {
      case "session" -> session(sessions++);
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
        if (schemaFailure != null) { throw schemaFailure; }
        yield result(query.startsWith("SHOW CONSTRAINTS") ? constraints() : List.of());
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

  private TransactionContext transaction() {
    return proxy(TransactionContext.class, (ignored, method, arguments) -> {
      if (!method.getName().equals("run")) { throw new AssertionError(method.getName()); }
      var query = (String) arguments[0];
      queries.add(query);
      if (query.contains("RETURN properties(result)") && readFailure != null) { throw readFailure; }
      var rows = query.contains("RETURN properties(result)") && !version.equals("absent")
          ? List.of(record(Map.of("properties", Map.of("formatVersion", version)))) : List.<Record>of();
      if (query.contains("RETURN entity.key AS key, elementId(entity) AS address")) {
        rows = ((Value) arguments[1]).get("nodes").asList(value -> value.asMap()).stream()
            .map(node -> record(Map.of("key", node.get("key"), "address", "node/" + node.get("key")))).toList();
      }
      if (stored != null) {
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
      if (query.contains("RETURN count(entity)")) {
        rows = List.of(record(Map.of("count", 0L)));
      }
      return result(rows, query.contains("labels(entity)") ? nodeStreamFailure : null);
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

  private static Record record(Map<String, Object> properties) {
    return proxy(Record.class, (ignored, method, arguments) -> switch (method.getName()) {
      case "get" -> Values.value(properties.get((String) arguments[0]));
      default -> throw new AssertionError("Unexpected record operation: " + method.getName());
    });
  }

  private static List<Record> constraints() {
    return List.of(constraint("ast_entity_identity", "AstEntity", List.of("astResultKey", "key")),
        constraint("ast_result_identity", "AstResult", List.of("astResultKey")));
  }

  private static Record constraint(String name, String label, List<String> properties) {
    return record(Map.of("name", name, "type", "NODE_PROPERTY_UNIQUENESS", "entityType", "NODE", "labelsOrTypes", List.of(label),
        "properties", properties));
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
  }
}
