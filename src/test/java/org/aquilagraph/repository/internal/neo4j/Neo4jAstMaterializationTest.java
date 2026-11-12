package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.repository.internal.neo4j.AstRepositoryDriverStub.Statement;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.testsupport.StatementBranchFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.Neo4jException;

class Neo4jAstMaterializationTest {
  private static final String BASE_LABEL = "AstEntity";
  private static final String OCCURRENCE_LABEL = "AstOccurrence";
  private static final String CHILD_TYPE = "AST_CHILD";

  @TempDir Path directory;

  @Test
  void givenPopulatedRows_whenSavingCompleteAst_thenExactEffectsUsePostNodeAddresses() {
    // Arrange
    var ast = completeAst();
    var scope = subjectKey(ast);
    var root = node(scope, scope, "AstResult", "root");
    var exact = node(scope, "exact", OCCURRENCE_LABEL, 1L);
    var scalar = node(scope, "scalar", OCCURRENCE_LABEL, 1L);
    var relabeled = node(scope, "relabeled", OCCURRENCE_LABEL, "new value");
    var extraLabel = node(scope, "extra-label", OCCURRENCE_LABEL, "exact value");
    var missing = node(scope, "missing", OCCURRENCE_LABEL, "inserted value");
    var retained = link(scope, "retained", scope, "exact", 1L);
    var changedScalar = link(scope, "changed-scalar", scope, "scalar", 1L);
    var changedLink = link(scope, "changed-link", "relabeled", "exact", "new value");
    var missingLink = link(scope, "missing-link", "relabeled", "missing", "inserted link");
    var desired = projection(ast, List.of(root, exact, scalar, relabeled, extraLabel, missing),
        List.of(retained, changedScalar, changedLink, missingLink));
    var existingNodes = List.of(storedNode("root-address", scope, List.of(BASE_LABEL, "AstResult"), properties(scope, scope, "root")),
        storedNode("exact-address", "exact", List.of(OCCURRENCE_LABEL, BASE_LABEL), properties(scope, "exact", 1L)),
        storedNode("scalar-address", "scalar", List.of(BASE_LABEL, OCCURRENCE_LABEL), properties(scope, "scalar", 1.0d)),
        storedNode("relabeled-address", "relabeled", List.of(BASE_LABEL, "AstValue", "SpareLabel"),
            Map.of("astResultKey", scope, "key", "relabeled", "value", "old value", "staleProperty", true)),
        storedNode("extra-label-address", "extra-label", List.of(BASE_LABEL, OCCURRENCE_LABEL, "SpareLabel"),
            properties(scope, "extra-label", "exact value")),
        storedNode("obsolete-address", "obsolete", List.of(BASE_LABEL, OCCURRENCE_LABEL), properties(scope, "obsolete", "old")),
        storedNode("missing-key-node-address", Values.NULL, List.of(BASE_LABEL, OCCURRENCE_LABEL), Map.of("astResultKey", scope)),
        storedNode("non-string-key-node-address", Values.NULL, List.of(BASE_LABEL, OCCURRENCE_LABEL), Map.of("astResultKey", scope, "key", 2L)));
    // The obsolete node's incident links are already gone when the link query runs.
    var postNodeLinks = List.of(
        storedLink("bad-retained-address", "retained", "AST_FIELD", "root-address", "exact-address", properties(scope, "retained", 1L)),
        storedLink("retained-address", "retained", CHILD_TYPE, "root-address", "exact-address", properties(scope, "retained", 1L)),
        storedLink("duplicate-address", "retained", CHILD_TYPE, "root-address", "exact-address", properties(scope, "retained", 1L)),
        storedLink("wrong-scalar-address", "changed-scalar", CHILD_TYPE, "root-address", "scalar-address", properties(scope, "changed-scalar", 1.0d)),
        storedLink("wrong-type-address", "changed-link", "AST_FIELD", "relabeled-address", "exact-address",
            properties(scope, "changed-link", "new value")),
        storedLink("wrong-properties-address", "changed-link", CHILD_TYPE, "relabeled-address", "exact-address",
            properties(scope, "changed-link", "old value")),
        storedLink("wrong-source-address", "changed-link", CHILD_TYPE, "foreign-source-address", "exact-address",
            properties(scope, "changed-link", "new value")),
        storedLink("wrong-target-address", "changed-link", CHILD_TYPE, "relabeled-address", "foreign-target-address",
            properties(scope, "changed-link", "new value")),
        storedLink("incoming-incident-address", "incoming", CHILD_TYPE, "foreign-source-address", "scalar-address",
            properties("another-subject", "incoming", "selected by target")),
        storedLink("outgoing-incident-address", "outgoing", CHILD_TYPE, "scalar-address", "foreign-target-address",
            properties("another-subject", "outgoing", "selected by source")),
        storedLink("scope-marked-foreign-address", "scope-marked", CHILD_TYPE, "foreign-source-address", "foreign-target-address",
            properties(scope, "scope-marked", "selected by property")),
        storedLink("missing-key-link-address", Values.NULL, CHILD_TYPE, "root-address", "exact-address", Map.of("astResultKey", scope)),
        storedLink("non-string-key-link-address", Values.NULL, CHILD_TYPE, "root-address", "exact-address", Map.of("astResultKey", scope, "key", 2L)));
    var attempt = new ReplacementAttempt(existingNodes, postNodeLinks, Map.of("missing", "new-node-address"));
    var backend = driver(attempt);

    // Act
    save(ast, desired, backend);

    // Assert
    assertScopeAndQueryShape(attempt, scope);
    assertEquals(List.of(Operation.LOCK, Operation.READ_NODES, Operation.DELETE_NODES, Operation.REMOVE_LABELS, Operation.UPDATE_NODES,
        Operation.INSERT_NODES), attempt.completedBeforeLinkRead);
    assertEquals(List.of("obsolete-address", "missing-key-node-address", "non-string-key-node-address"),
        attempt.payload(Operation.DELETE_NODES, "addresses"));
    assertTrue(attempt.one(Operation.DELETE_NODES).query().contains("WHERE elementId(entity) IN $addresses DETACH DELETE entity"));
    assertEquals(List.of(Map.of("address", "relabeled-address", "labels", List.of("AstValue", "SpareLabel")),
        Map.of("address", "extra-label-address", "labels", List.of("SpareLabel"))),
        attempt.payload(Operation.REMOVE_LABELS, "nodes"));
    assertTrue(attempt.one(Operation.REMOVE_LABELS).query().contains("WHERE elementId(entity) = item.address REMOVE entity:$(item.labels)"));
    assertEquals(List.of(update("scalar-address", properties(scope, "scalar", 1L)),
        update("relabeled-address", properties(scope, "relabeled", "new value")),
        update("extra-label-address", properties(scope, "extra-label", "exact value"))), attempt.payload(Operation.UPDATE_NODES, "nodes"));
    assertTrue(attempt.one(Operation.UPDATE_NODES).query()
        .contains("WHERE elementId(entity) = item.address SET entity:`AstOccurrence`, entity = item.properties"));
    assertEquals(List.of(properties(scope, "missing", "inserted value")), attempt.payload(Operation.INSERT_NODES, "nodes"));
    assertTrue(attempt.one(Operation.INSERT_NODES).query().contains("CREATE (entity:AstEntity:`AstOccurrence`) SET entity = expected"));
    assertEquals(List.of("bad-retained-address", "duplicate-address", "wrong-scalar-address", "wrong-type-address", "wrong-properties-address",
        "wrong-source-address", "wrong-target-address", "incoming-incident-address", "outgoing-incident-address",
        "scope-marked-foreign-address", "missing-key-link-address", "non-string-key-link-address"),
        attempt.payload(Operation.DELETE_LINKS, "addresses"));
    assertTrue(attempt.one(Operation.DELETE_LINKS).query().contains("WHERE elementId(relation) IN $addresses DELETE relation"));
    assertEquals(List.of(linkInsert("root-address", "scalar-address", properties(scope, "changed-scalar", 1L)),
        linkInsert("relabeled-address", "exact-address", properties(scope, "changed-link", "new value")),
        linkInsert("relabeled-address", "new-node-address", properties(scope, "missing-link", "inserted link"))),
        attempt.payload(Operation.INSERT_LINKS, "relationships"));
    assertTrue(attempt.one(Operation.INSERT_LINKS).query().contains("WHERE elementId(source) = item.source AND elementId(target) = item.target "
        + "CREATE (source)-[relation:`AST_CHILD`]->(target) SET relation = item.properties"));
    assertEquals(List.of(Operation.READ_LINKS, Operation.DELETE_LINKS, Operation.INSERT_LINKS),
        attempt.completed.subList(attempt.completedBeforeLinkRead.size(), attempt.completed.size()));
    assertEquals(List.of("session.open:0", "session.close:0", "session.open:1", "write.begin", "callback.return", "write.acknowledged",
        "session.close:1", "driver.close"), backend.events);
  }

  @Test
  void givenExactPopulatedProjection_whenSavingCompleteAst_thenOnlyTheSubjectLockWrites() {
    // Arrange
    var ast = completeAst();
    var scope = subjectKey(ast);
    var root = node(scope, scope, "AstResult", "root");
    var child = node(scope, "child", OCCURRENCE_LABEL, 1L);
    var desired = projection(ast, List.of(root, child), List.of(link(scope, "child-link", scope, "child", 1L)));
    var attempt = new ReplacementAttempt(List.of(
        storedNode("exact-root", scope, List.of(BASE_LABEL, "AstResult"), properties(scope, scope, "root")),
        storedNode("exact-child", "child", List.of(BASE_LABEL, OCCURRENCE_LABEL), properties(scope, "child", 1L))),
        List.of(storedLink("exact-link", "child-link", CHILD_TYPE, "exact-root", "exact-child", properties(scope, "child-link", 1L))), Map.of());
    var backend = driver(attempt);

    // Act
    save(ast, desired, backend);

    // Assert
    assertEquals(List.of(Operation.LOCK, Operation.READ_NODES, Operation.READ_LINKS), attempt.completed);
    assertEquals(3, backend.statements.size());
  }

  @Test
  void givenChangesAroundBatchCapacity_whenSavingCompleteAst_thenEveryWriteFamilyUsesBoundedCompleteBatches() {
    var ast = completeAst();
    var scope = subjectKey(ast);
    for (var count : List.of(511, 512, 513)) {
      // Arrange
      var fixture = new BatchFixture(scope, count);
      var attempt = new ReplacementAttempt(fixture.storedNodes, fixture.storedLinks, fixture.insertedAddresses);
      var backend = driver(attempt);
      var desired = projection(ast, fixture.expectedNodes, fixture.expectedLinks);

      // Act
      save(ast, desired, backend);

      // Assert
      for (var write : fixture.writes) {
        assertEquals(write.payload(), attempt.payload(write.operation(), write.parameter()), write.operation().name());
        assertEquals(count <= 512 ? List.of(count) : List.of(512, 1), attempt.batchSizes(write.operation(), write.parameter()),
            write.operation().name());
      }
    }
  }

  @Test
  void givenManagedReplayWithDifferentAddressesAndRows_whenSavingCompleteAst_thenEachAttemptReconcilesItsOwnState() {
    // Arrange
    var ast = completeAst();
    var scope = subjectKey(ast);
    var root = node(scope, scope, "AstResult", "root");
    var existing = node(scope, "existing", OCCURRENCE_LABEL, "present");
    var missing = node(scope, "missing", OCCURRENCE_LABEL, "new");
    var desired = projection(ast, List.of(root, existing, missing),
        List.of(link(scope, "existing-link", scope, "existing", "retained once"), link(scope, "new-link", scope, "missing", "inserted twice")));
    var first = new ReplacementAttempt(List.of(
        storedNode("first-root", scope, List.of(BASE_LABEL, "AstResult"), properties(scope, scope, "root")),
        storedNode("first-existing", "existing", List.of(BASE_LABEL, OCCURRENCE_LABEL), properties(scope, "existing", "present"))),
        List.of(storedLink("first-link", "existing-link", CHILD_TYPE, "first-root", "first-existing", properties(scope, "existing-link", "retained once"))),
        Map.of("missing", "first-new"));
    var replay = new ReplacementAttempt(List.of(
        storedNode("replay-root", scope, List.of(BASE_LABEL, "AstResult"), properties(scope, scope, "root")),
        storedNode("replay-existing", "existing", List.of(BASE_LABEL, OCCURRENCE_LABEL), properties(scope, "existing", "present"))),
        List.of(), Map.of("missing", "replay-new"));
    var backend = driver(first, replay);

    // Act
    save(ast, desired, backend);

    // Assert
    assertEquals(List.of(Operation.LOCK, Operation.READ_NODES, Operation.INSERT_NODES, Operation.READ_LINKS, Operation.INSERT_LINKS), first.completed);
    assertEquals(first.completed, replay.completed);
    assertEquals(List.of(properties(scope, "missing", "new")), first.payload(Operation.INSERT_NODES, "nodes"));
    assertEquals(List.of(properties(scope, "missing", "new")), replay.payload(Operation.INSERT_NODES, "nodes"));
    assertEquals(List.of(linkInsert("first-root", "first-new", properties(scope, "new-link", "inserted twice"))),
        first.payload(Operation.INSERT_LINKS, "relationships"));
    assertEquals(List.of(linkInsert("replay-root", "replay-existing", properties(scope, "existing-link", "retained once")),
        linkInsert("replay-root", "replay-new", properties(scope, "new-link", "inserted twice"))), replay.payload(Operation.INSERT_LINKS, "relationships"));
    assertEquals(List.of("session.open:0", "session.close:0", "session.open:1", "write.begin", "callback.return", "callback.return",
        "write.acknowledged", "session.close:1", "driver.close"), backend.events);
  }

  @Test
  void givenLinkStreamAndCleanupFailures_whenSavingCompleteAst_thenTheOriginalPhaseAndOrderedCausesSurvive() {
    // Arrange
    var ast = completeAst();
    var scope = subjectKey(ast);
    var desired = projection(ast, List.of(node(scope, scope, "AstResult", "root")), List.of());
    var attempt = new ReplacementAttempt(List.of(
        storedNode("root-address", scope, List.of(BASE_LABEL, "AstResult"), properties(scope, scope, "root")),
        storedNode("old-address", "old", List.of(BASE_LABEL, OCCURRENCE_LABEL), properties(scope, "old", "obsolete"))),
        List.of(storedLink("selected-address", "selected", CHILD_TYPE, "root-address", "foreign-address", properties(scope, "selected", "obsolete"))),
        Map.of());
    var streamFailure = new Neo4jException("Link stream failed after a row");
    attempt.linkStreamFailure = streamFailure;
    var backend = driver(attempt);
    var sessionCloseFailure = new IllegalStateException("Publication session close failed");
    var driverCloseFailure = new Neo4jException("Repository driver close failed");
    backend.failingClose = 1;
    backend.closeFailure = sessionCloseFailure;
    backend.driverCloseFailure = driverCloseFailure;

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> save(ast, desired, backend));

    // Assert
    assertEquals(RepositoryOperationContext.saveAst(desired.identity()), failure.context());
    assertEquals(Neo4jRepositoryPhase.REPLACEMENT, failure.phase());
    assertEquals(CommitKnowledge.UNKNOWN, failure.knowledge().orElseThrow());
    assertSame(streamFailure, assertInstanceOf(RepositoryException.class, failure.effectivePrimary()).getCause());
    assertEquals(2, failure.getSuppressed().length);
    var sessionClose = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[0]);
    var driverClose = assertInstanceOf(Neo4jRepositoryOperationException.class, failure.getSuppressed()[1]);
    assertEquals(Neo4jRepositoryPhase.PUBLICATION_SESSION_CLOSE, sessionClose.phase());
    assertSame(sessionCloseFailure, sessionClose.effectivePrimary());
    assertEquals(Neo4jRepositoryPhase.REPOSITORY_CLOSE, driverClose.phase());
    assertSame(driverCloseFailure, assertInstanceOf(RepositoryException.class, driverClose.effectivePrimary()).getCause());
    assertEquals(List.of(Operation.LOCK, Operation.READ_NODES, Operation.DELETE_NODES), attempt.completedBeforeLinkRead);
    assertEquals(List.of(Operation.LOCK, Operation.READ_NODES, Operation.DELETE_NODES, Operation.READ_LINKS), attempt.completed);
    assertEquals(4, backend.statements.size());
    assertEquals(List.of("session.open:0", "session.close:0", "session.open:1", "write.begin", "session.close:1", "driver.close"), backend.events);
  }

  @Test
  void givenSecondNodeUpdateBatchFails_whenSavingAndRepeatingTheAcquiredSave_thenLaterEffectsAndReuseAreRejected() {
    // Arrange
    var ast = completeAst();
    var scope = subjectKey(ast);
    var fixture = new BatchFixture(scope, 513);
    var attempt = new ReplacementAttempt(fixture.storedNodes, fixture.storedLinks, fixture.insertedAddresses);
    var batchFailure = new Neo4jException("Second node update batch failed");
    attempt.onResultClose = operation -> {
      if (operation == Operation.UPDATE_NODES && attempt.completed.contains(Operation.UPDATE_NODES)) {
        throw batchFailure;
      }
    };
    var backend = driver(attempt);
    var desired = projection(ast, fixture.expectedNodes, fixture.expectedLinks);
    var context = RepositoryOperationContext.saveAst(desired.identity());
    Neo4jRepositoryOperationException firstFailure;
    Neo4jRepositoryOperationException repeatedFailure;

    // Act
    try (var repository = openSave(desired, backend)) {
      firstFailure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.saveComplete(ast));
      repeatedFailure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.saveComplete(ast));
    }

    // Assert
    assertEquals(context, firstFailure.context());
    assertEquals(Neo4jRepositoryPhase.REPLACEMENT, firstFailure.phase());
    assertEquals(CommitKnowledge.UNKNOWN, firstFailure.knowledge().orElseThrow());
    assertSame(batchFailure, assertInstanceOf(RepositoryException.class, firstFailure.effectivePrimary()).getCause());
    assertEquals(context, repeatedFailure.context());
    assertEquals(Neo4jRepositoryPhase.VALIDATION, repeatedFailure.phase());
    assertEquals(CommitKnowledge.UNKNOWN, repeatedFailure.knowledge().orElseThrow());
    assertInstanceOf(IllegalArgumentException.class, repeatedFailure.effectivePrimary());
    assertEquals(List.of(512, 1), attempt.batchSizes(Operation.UPDATE_NODES, "nodes"));
    var expectedUpdates = fixture.writes.stream().filter(write -> write.operation() == Operation.UPDATE_NODES).findFirst().orElseThrow();
    assertEquals(expectedUpdates.payload(), attempt.payload(Operation.UPDATE_NODES, "nodes"));
    assertEquals(List.of(Operation.LOCK, Operation.READ_NODES, Operation.DELETE_NODES, Operation.DELETE_NODES, Operation.REMOVE_LABELS,
        Operation.REMOVE_LABELS, Operation.UPDATE_NODES), attempt.completed);
    assertEquals(8, backend.statements.size());
    assertEquals(List.of("session.open:0", "session.close:0", "session.open:1", "write.begin", "session.close:1", "driver.close"), backend.events);
  }

  private static void assertScopeAndQueryShape(ReplacementAttempt attempt, String scope) {
    var lock = attempt.one(Operation.LOCK);
    assertEquals(Map.of("scope", scope), lock.parameters().asMap());
    assertTrue(lock.query().contains("{astResultKey: $scope, key: $scope}"));
    var nodes = attempt.one(Operation.READ_NODES);
    assertEquals(Map.of("scope", scope), nodes.parameters().asMap());
    assertTrue(nodes.query().contains("MATCH (entity:AstEntity {astResultKey: $scope})"));
    assertTrue(nodes.query().contains("CASE WHEN entity.key IS :: STRING NOT NULL THEN entity.key ELSE null END AS key"));
    var links = attempt.one(Operation.READ_LINKS);
    assertEquals(Map.of("scope", scope), links.parameters().asMap());
    assertTrue(links.query().contains("WHERE (source:AstEntity AND source.astResultKey = $scope) OR relation.astResultKey = $scope "
        + "OR (target:AstEntity AND target.astResultKey = $scope)"));
    assertTrue(links.query().contains("CASE WHEN relation.key IS :: STRING NOT NULL THEN relation.key ELSE null END AS key"));
  }

  private AstOutcome completeAst() {
    return StatementBranchFixture.produce(directory, AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }

  private static String subjectKey(AstOutcome ast) {
    return ast.result().orElseThrow().identity().astResultKey();
  }

  private static Neo4jAstProjection projection(AstOutcome ast, List<Neo4jNode> nodes, List<Neo4jRelationship> links) {
    return new Neo4jAstProjection(ast.result().orElseThrow().identity(), nodes, links);
  }

  private static AstRepositoryDriverStub driver(ReplacementAttempt... attempts) {
    var backend = new AstRepositoryDriverStub();
    backend.attempts = attempts.length;
    backend.replacementResult = (attempt, statement) -> attempts[attempt].result(statement);
    return backend;
  }

  private static void save(AstOutcome ast, Neo4jAstProjection desired, AstRepositoryDriverStub backend) {
    try (var repository = openSave(desired, backend)) {
      repository.saveComplete(ast);
    }
  }

  private static Neo4jAstResultRepository openSave(Neo4jAstProjection desired, AstRepositoryDriverStub backend) {
    return new Neo4jAstResultRepository(backend.driver(), SessionConfig.defaultConfig(), RepositoryOperationContext.saveAst(desired.identity()),
        new Neo4jAstResultCodec(), ignored -> desired);
  }

  private static Neo4jNode node(String scope, String key, String label, Object value) {
    return new Neo4jNode(key, label, properties(scope, key, value));
  }

  private static Neo4jRelationship link(String scope, String key, String source, String target, Object value) {
    return new Neo4jRelationship(key, CHILD_TYPE, source, target, properties(scope, key, value));
  }

  private static Map<String, Object> properties(String scope, String key, Object value) {
    return Map.of("astResultKey", scope, "key", key, "value", value);
  }

  private static Map<String, Object> update(String address, Map<String, Object> properties) {
    return Map.of("address", address, "properties", properties);
  }

  private static Map<String, Object> linkInsert(String source, String target, Map<String, Object> properties) {
    return Map.of("source", source, "target", target, "properties", properties);
  }

  private static Record storedNode(String address, Object projectedKey, List<String> labels, Map<String, Object> properties) {
    return AstRepositoryDriverStub.record(Map.of("address", address, "key", projectedKey, "labels", labels, "properties", properties));
  }

  private static Record storedLink(String address, Object projectedKey, String type, String source, String target, Map<String, Object> properties) {
    return AstRepositoryDriverStub.record(Map.of("address", address, "key", projectedKey, "type", type, "source", source, "target", target,
        "properties", properties));
  }

  private enum Operation {
    LOCK("MERGE (subject:AstEntity"),
    READ_NODES("labels(entity) AS labels"),
    DELETE_NODES("DETACH DELETE entity"),
    REMOVE_LABELS("REMOVE entity:$(item.labels)"),
    UPDATE_NODES("SET entity:`"),
    INSERT_NODES("CREATE (entity:AstEntity:"),
    READ_LINKS("elementId(relation) AS address"),
    DELETE_LINKS("DELETE relation"),
    INSERT_LINKS("CREATE (source)-[relation:");

    private final String clause;

    Operation(String clause) {
      this.clause = clause;
    }

    static Operation of(String query) {
      for (var operation : values()) {
        if (query.contains(operation.clause)) {
          return operation;
        }
      }
      throw new AssertionError("Unexpected materialization statement: " + query);
    }
  }

  private static final class ReplacementAttempt {
    private final List<Record> storedNodes;
    private final List<Record> postNodeLinks;
    private final Map<String, String> insertedAddresses;
    private final Map<Operation, List<Statement>> statements = new EnumMap<>(Operation.class);
    private final List<Operation> completed = new ArrayList<>();
    private List<Operation> completedBeforeLinkRead = List.of();
    private RuntimeException linkStreamFailure;
    private Consumer<Operation> onResultClose = ignored -> {};

    ReplacementAttempt(List<Record> storedNodes, List<Record> postNodeLinks, Map<String, String> insertedAddresses) {
      this.storedNodes = storedNodes;
      this.postNodeLinks = postNodeLinks;
      this.insertedAddresses = insertedAddresses;
    }

    Result result(Statement statement) {
      var operation = Operation.of(statement.query());
      statements.computeIfAbsent(operation, ignored -> new ArrayList<>()).add(statement);
      var rows = switch (operation) {
        case READ_NODES -> storedNodes;
        case INSERT_NODES -> insertedRows(statement.parameters());
        case READ_LINKS -> {
          completedBeforeLinkRead = List.copyOf(completed);
          yield postNodeLinks;
        }
        default -> List.<Record>of();
      };
      return AstRepositoryDriverStub.observedResult(rows, operation == Operation.READ_LINKS ? linkStreamFailure : null, () -> {
        onResultClose.accept(operation);
        completed.add(operation);
      });
    }

    private List<Record> insertedRows(Value parameters) {
      return parameters.get("nodes").asList(node -> {
        var key = node.get("key").asString();
        assertTrue(insertedAddresses.containsKey(key), "No controlled insertion address for " + key);
        return AstRepositoryDriverStub.record(Map.of("key", key, "address", insertedAddresses.get(key)));
      });
    }

    Statement one(Operation operation) {
      return statements.getOrDefault(operation, List.of()).getFirst();
    }

    List<Object> payload(Operation operation, String parameter) {
      return statements.getOrDefault(operation, List.of()).stream()
          .flatMap(statement -> statement.parameters().get(parameter).asList().stream()).toList();
    }

    List<Integer> batchSizes(Operation operation, String parameter) {
      return statements.getOrDefault(operation, List.of()).stream().map(statement -> statement.parameters().get(parameter).size()).toList();
    }
  }

  private record ExpectedWrite(Operation operation, String parameter, List<?> payload) {
  }

  private static final class BatchFixture {
    private final List<Neo4jNode> expectedNodes = new ArrayList<>();
    private final List<Neo4jRelationship> expectedLinks = new ArrayList<>();
    private final List<Record> storedNodes = new ArrayList<>();
    private final List<Record> storedLinks = new ArrayList<>();
    private final Map<String, String> insertedAddresses = new HashMap<>();
    private final List<ExpectedWrite> writes;

    BatchFixture(String scope, int count) {
      expectedNodes.add(node(scope, scope, "AstResult", "root"));
      storedNodes.add(storedNode("root-address", scope, List.of(BASE_LABEL, "AstResult"), properties(scope, scope, "root")));
      var obsoleteNodes = new ArrayList<String>();
      var obsoleteLinks = new ArrayList<String>();
      var oldLabels = new ArrayList<Map<String, Object>>();
      var replacements = new ArrayList<Map<String, Object>>();
      var nodeInserts = new ArrayList<Map<String, Object>>();
      var linkInserts = new ArrayList<Map<String, Object>>();
      for (var index = 0; index < count; index++) {
        var obsoleteKey = "obsolete-" + index;
        var obsoleteAddress = "obsolete-address-" + index;
        storedNodes.add(storedNode(obsoleteAddress, obsoleteKey, List.of(BASE_LABEL, OCCURRENCE_LABEL), properties(scope, obsoleteKey, "old")));
        obsoleteNodes.add(obsoleteAddress);
        var existingKey = "existing-" + index;
        var existingAddress = "existing-address-" + index;
        storedNodes.add(storedNode(existingAddress, existingKey, List.of(BASE_LABEL, "AstValue"), properties(scope, existingKey, "old")));
        expectedNodes.add(node(scope, existingKey, OCCURRENCE_LABEL, "updated"));
        oldLabels.add(Map.of("address", existingAddress, "labels", List.of("AstValue")));
        replacements.add(update(existingAddress, properties(scope, existingKey, "updated")));
        var newKey = "new-" + index;
        var newAddress = "new-address-" + index;
        expectedNodes.add(node(scope, newKey, OCCURRENCE_LABEL, "new"));
        nodeInserts.add(properties(scope, newKey, "new"));
        insertedAddresses.put(newKey, newAddress);
        var linkKey = "link-" + index;
        var linkAddress = "link-address-" + index;
        storedLinks.add(storedLink(linkAddress, linkKey, CHILD_TYPE, "root-address", existingAddress, properties(scope, linkKey, "old")));
        obsoleteLinks.add(linkAddress);
        expectedLinks.add(link(scope, linkKey, scope, newKey, "new"));
        linkInserts.add(linkInsert("root-address", newAddress, properties(scope, linkKey, "new")));
      }
      writes = List.of(new ExpectedWrite(Operation.DELETE_NODES, "addresses", obsoleteNodes),
          new ExpectedWrite(Operation.REMOVE_LABELS, "nodes", oldLabels), new ExpectedWrite(Operation.UPDATE_NODES, "nodes", replacements),
          new ExpectedWrite(Operation.INSERT_NODES, "nodes", nodeInserts), new ExpectedWrite(Operation.DELETE_LINKS, "addresses", obsoleteLinks),
          new ExpectedWrite(Operation.INSERT_LINKS, "relationships", linkInserts));
    }
  }
}
