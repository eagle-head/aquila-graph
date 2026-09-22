package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisEnvironmentSnapshot;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CfgEdge;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.ControlFlowGraph;
import org.aquilagraph.model.EntryNode;
import org.aquilagraph.model.ExitNode;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.JavaField;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaParameter;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.JavaTypeKind;
import org.aquilagraph.model.MethodAnalysis;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.OperationOperand;
import org.aquilagraph.model.ProgramRepresentation;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.SoftwareVersion;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

class Neo4jAnalysisRunRepositoryNeo4jIT {
  @TempDir Path temporaryDirectory;

  @Test
  void givenRunningNeo4j_whenRunsArePersisted_thenStorageIsIdempotentIsolatedAndTransactional()
      throws IOException {
    // Arrange
    var settings = Neo4jConnectionSettings.fromEnvironment(System.getenv());
    var primarySourceRoot = Files.createDirectories(temporaryDirectory.resolve("primary"));
    var primaryRun = completedRun(primarySourceRoot, "PersistentSample");
    var independentSourceRoot = Files.createDirectories(temporaryDirectory.resolve("independent"));
    var independentRun = completedRun(independentSourceRoot, "Independent");
    var sessionConfig = SessionConfig.forDatabase(settings.database());

    try {
      // Act
      var expectedPrimaryProjection = new Neo4jAnalysisRunMapper().map(primaryRun);
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(primaryRun);
        repository.save(primaryRun);
        repository.save(independentRun);
      }

      // Assert
      try (var driver =
              GraphDatabase.driver(
                  settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var session = driver.session(sessionConfig)) {
        driver.verifyConnectivity();
        assertEquals(expectedPrimaryProjection.nodes().size(), nodeCount(session, primaryRun.runId()));
        assertTrue(nodeCount(session, independentRun.runId()) > 0);
        assertEquals(
            0,
            session
                .run(
                    "MATCH (left:AnalysisEntity)-[relation]->(right:AnalysisEntity) "
                        + "WHERE left.runId <> right.runId RETURN count(relation) AS count")
                .single()
                .get("count")
                .asLong());
        assertTrue(
            session
                    .run(
                        "SHOW CONSTRAINTS YIELD name WHERE name IN "
                            + "['analysis_entity_identity', 'analysis_run_identity'] "
                            + "RETURN count(*) AS count")
                    .single()
                    .get("count")
                    .asLong()
                == 2);
        var flowTypes =
            session
                .run(
                    "MATCH (:AnalysisEntity {runId: $runId})-[relation]->() "
                        + "WHERE relation.runId = $runId AND type(relation) ENDS WITH '_FLOW' "
                        + "RETURN collect(DISTINCT type(relation)) AS types",
                    Values.parameters("runId", primaryRun.runId()))
                .single()
                .get("types")
                .asList(value -> value.asString());
        assertTrue(flowTypes.contains("EXCEPTIONAL_FLOW"));
        assertTrue(flowTypes.contains("SWITCH_FLOW"));
        assertTrue(
            session
                    .run(
                        "MATCH (:FlowValue {runId: $runId}) RETURN count(*) AS count",
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (value:FlowValue {runId: $runId}) "
                            + "WHERE value.staticTypeKey IS NOT NULL "
                            + "AND value.staticTypeErasure IS NOT NULL "
                            + "AND value.staticTypeCategory IS NOT NULL "
                            + "RETURN count(value) AS count",
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (parameter:JavaParameter {runId: $runId}) "
                            + "WHERE parameter.staticTypeKey IS NOT NULL "
                            + "AND parameter.staticTypeErasure IS NOT NULL "
                            + "AND parameter.staticTypeCategory IS NOT NULL "
                            + "RETURN count(parameter) AS count",
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (:JavaType {runId: $runId})-"
                            + "[declaration:DECLARES_FIELD]->(field:JavaField {runId: $runId}) "
                            + "WHERE declaration.runId = $runId "
                            + "AND field.staticTypeKey IS NOT NULL "
                            + "AND field.staticTypeErasure IS NOT NULL "
                            + "AND field.staticTypeCategory IS NOT NULL "
                            + "RETURN count(field) AS count",
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (operation:OperationNode {runId: $runId}) "
                            + "WHERE operation.kind IS NOT NULL RETURN count(*) AS count",
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertEquals(
            0,
            session
                .run(
                    "MATCH (entity:AnalysisEntity {runId: $runId}) "
                        + "WHERE any(label IN labels(entity) WHERE label IN ['MethodBody', 'SyntaxNode']) "
                        + "RETURN count(entity) AS count",
                    Values.parameters("runId", primaryRun.runId()))
                .single()
                .get("count")
                .asLong());
        assertEquals(
            0,
            session
                .run(
                    "MATCH (:AnalysisEntity {runId: $runId})-[relation]->() "
                        + "WHERE type(relation) IN ['HAS_BODY', 'HAS_SYNTAX_ROOT', 'CHILD'] "
                        + "RETURN count(relation) AS count",
                    Values.parameters("runId", primaryRun.runId()))
                .single()
                .get("count")
                .asLong());
        assertTrue(
            session
                    .run(
                        "MATCH (method:JavaMethod {runId: $runId}) "
                            + "WHERE method.hasBody = true RETURN count(method) AS count",
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (operation:OperationNode {runId: $runId})-[:HAS_REFERENCE|HAS_ORIGIN]->() "
                            + "RETURN count(operation) AS count",
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
        assertTrue(
            session
                    .run(
                        "MATCH (:SourceOrigin {runId: $runId})-[:IN_SOURCE]->"
                            + "(:SourceFile {runId: $runId}) RETURN count(*) AS count",
                        Values.parameters("runId", primaryRun.runId()))
                    .single()
                    .get("count")
                    .asLong()
                > 0);
      }

      var invalidProjection = invalidProjection(primaryRun);
      try (var failingDriver =
              GraphDatabase.driver(
                  settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var failingRepository =
              new Neo4jAnalysisRunRepository(
                  failingDriver, sessionConfig, ignored -> invalidProjection)) {
        assertThrows(RepositoryException.class, () -> failingRepository.save(primaryRun));
      }
      try (var verificationDriver =
              GraphDatabase.driver(
                  settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var verificationSession = verificationDriver.session(sessionConfig)) {
        assertEquals(expectedPrimaryProjection.nodes().size(), nodeCount(verificationSession, primaryRun.runId()));
      }
    } finally {
      cleanup(settings, List.of(primaryRun.runId(), independentRun.runId()));
    }
  }

  private static AnalysisRun completedRun(Path sourceRoot, String typeName) throws IOException {
    var source = """
        class %s {
          int field;
          int evaluate(int input, int divisor) {
            return switch (input) {
              case 0 -> field;
              default -> input / divisor;
            };
          }
        }
        """.formatted(typeName);
    Files.writeString(sourceRoot.resolve(typeName + ".java"), source);
    var snapshot = new JavaSourceLoader().discover(sourceRoot);
    var sourceFile = snapshot.sourceFiles().getFirst();
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var environment = new AnalysisEnvironmentSnapshot(configuration, "test-compiler", "test-vendor", "25-test",
        List.of("--release", "25"), List.of(), List.of());
    var typeKey = StableId.sha256("type", typeName);
    var fieldKey = StableId.sha256("field", typeKey, "field");
    var methodKey = StableId.sha256("method", typeKey, "evaluate(int,int)");
    var intType = new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int");
    var field = new JavaField(fieldKey, typeKey, "field", intType, Set.of(), false, true,
        List.of(span(sourceFile, "int field;")));
    var parameters = List.of(
        new JavaParameter(StableId.sha256("parameter", methodKey, "input"), "input", intType,
            List.of(span(sourceFile, "int input"))),
        new JavaParameter(StableId.sha256("parameter", methodKey, "divisor"), "divisor", intType,
            List.of(span(sourceFile, "int divisor"))));
    var method = new JavaMethod(methodKey, typeKey, "evaluate", "evaluate(int,int)", "int", parameters, Set.of(), false, true, true,
        List.of(span(sourceFile, "int evaluate")));
    var type = new JavaType(typeKey, typeName, JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
        List.of(), true, List.of(field), List.of(method), List.of(new SourceSpan(sourceFile.sourceFileKey(), 0, source.length())));
    var graph = controlFlowGraph(sourceFile, methodKey, fieldKey, intType);
    var representation = new ProgramRepresentation(new JavaProgram(List.of(type)),
        List.of(new MethodAnalysis(StableId.sha256("method-analysis", methodKey), method, graph)));
    var runId = StableId.sha256("analysis-run", SoftwareVersion.from(snapshot, environment).signature());
    return new AnalysisRun(runId, snapshot, environment, Optional.of(representation), List.of(), AnalysisCompletion.COMPLETED);
  }

  private static ControlFlowGraph controlFlowGraph(SourceFile sourceFile, String methodKey, String fieldKey,
      JavaTypeIdentity intType) {
    var graphId = StableId.sha256("cfg", methodKey);
    var entry = new EntryNode(StableId.sha256("cfg-node", graphId, "entry"), graphId);
    var exit = new ExitNode(StableId.sha256("cfg-node", graphId, "exit"), graphId);
    var inputValue = new FlowValue(StableId.sha256("cfg-value", graphId, "input"), graphId, ValueKind.PARAMETER,
        Optional.of("input"), Optional.empty(), Optional.of(intType));
    var divisorValue = new FlowValue(StableId.sha256("cfg-value", graphId, "divisor"), graphId, ValueKind.PARAMETER,
        Optional.of("divisor"), Optional.empty(), Optional.of(intType));
    var divisionValue = new FlowValue(StableId.sha256("cfg-value", graphId, "division"), graphId, ValueKind.TEMPORARY,
        Optional.empty(), Optional.empty(), Optional.of(intType));
    var selector = operation(graphId, "selector", OperationKind.SWITCH_SELECTOR, "switch (input)",
        List.of(new OperationOperand(0, inputValue)), Optional.empty(), List.of(), List.of(), span(sourceFile, "switch (input)"));
    var fieldReference = new SemanticReference(ReferenceKind.FIELD, ResolutionStatus.RESOLVED, Optional.of(fieldKey),
        Optional.of("int"), List.of("int"), List.of("int"));
    var fieldRead = operation(graphId, "field-read", OperationKind.READ, "field", List.of(), Optional.empty(),
        List.of(fieldReference), List.of(), span(sourceFile, "case 0 -> field"));
    var division = operation(graphId, "division", OperationKind.BINARY, "input / divisor",
        List.of(new OperationOperand(0, inputValue), new OperationOperand(1, divisorValue)), Optional.of(divisionValue), List.of(),
        List.of("java.lang.ArithmeticException"), span(sourceFile, "input / divisor"));
    var returning = operation(graphId, "return", OperationKind.RETURN, "return switch", List.of(), Optional.empty(), List.of(),
        List.of(), span(sourceFile, "return switch"));
    var edges = List.of(
        edge(graphId, "entry-selector", entry.nodeId(), selector.nodeId(), TransferKind.NORMAL, Optional.empty()),
        edge(graphId, "selector-field", selector.nodeId(), fieldRead.nodeId(), TransferKind.SWITCH_ALTERNATIVE,
            Optional.of("case 0")),
        edge(graphId, "selector-division", selector.nodeId(), division.nodeId(), TransferKind.SWITCH_ALTERNATIVE,
            Optional.of("default")),
        edge(graphId, "field-return", fieldRead.nodeId(), returning.nodeId(), TransferKind.NORMAL, Optional.empty()),
        edge(graphId, "division-return", division.nodeId(), returning.nodeId(), TransferKind.NORMAL, Optional.empty()),
        edge(graphId, "division-exception", division.nodeId(), exit.nodeId(), TransferKind.EXCEPTIONAL,
            Optional.of("java.lang.ArithmeticException")),
        edge(graphId, "return-exit", returning.nodeId(), exit.nodeId(), TransferKind.RETURN, Optional.empty()));
    return new ControlFlowGraph(graphId, methodKey, entry, List.of(selector, fieldRead, division, returning),
        List.of(inputValue, divisorValue, divisionValue), exit, edges);
  }

  private static OperationNode operation(String graphId, String identity, OperationKind kind, String detail,
      List<OperationOperand> operands, Optional<FlowValue> definedValue, List<SemanticReference> references,
      List<String> possibleExceptions, SourceSpan origin) {
    return new OperationNode(StableId.sha256("cfg-node", graphId, identity), graphId, kind, detail, operands, definedValue,
        Optional.empty(), references, possibleExceptions, List.of(origin));
  }

  private static CfgEdge edge(String graphId, String identity, String sourceNodeId, String targetNodeId, TransferKind kind,
      Optional<String> discriminator) {
    return new CfgEdge(StableId.sha256("cfg-edge", graphId, identity), graphId, sourceNodeId, targetNodeId, kind, discriminator);
  }

  private static SourceSpan span(SourceFile sourceFile, String fragment) {
    var start = sourceFile.content().indexOf(fragment);
    if (start < 0) {
      throw new IllegalArgumentException("Missing source fragment: " + fragment);
    }
    return new SourceSpan(sourceFile.sourceFileKey(), start, start + fragment.length());
  }

  private static Neo4jGraphProjection invalidProjection(AnalysisRun run) {
    var valid = new Neo4jAnalysisRunMapper().map(run);
    var nodes = new ArrayList<>(valid.nodes());
    var first = nodes.getFirst();
    var invalidProperties = new LinkedHashMap<>(first.properties());
    invalidProperties.put("invalidNestedMap", Map.of("not", "a Neo4j property"));
    nodes.set(0, new Neo4jNode(first.key(), first.label(), invalidProperties));
    return new Neo4jGraphProjection(valid.runId(), nodes, valid.relationships());
  }

  private static long nodeCount(Session session, String runId) {
    return session
        .run(
            "MATCH (node:AnalysisEntity {runId: $runId}) RETURN count(node) AS count",
            Values.parameters("runId", runId))
        .single()
        .get("count")
        .asLong();
  }

  private static void cleanup(Neo4jConnectionSettings settings, List<String> runIds) {
    try (var driver =
            GraphDatabase.driver(
                settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      session
          .run(
              "MATCH (entity:AnalysisEntity) WHERE entity.runId IN $runIds DETACH DELETE entity",
              Values.parameters("runIds", runIds))
          .consume();
    }
  }
}
