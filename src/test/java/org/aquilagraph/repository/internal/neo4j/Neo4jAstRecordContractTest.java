package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstLookup;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;

class Neo4jAstRecordContractTest {
  @TempDir Path directory;

  @Test
  void givenDistinctOrderedChildren_whenOneStoredPositionChanges_thenDecodeFailsWithTheRequestedIdentity() throws IOException {
    // Arrange
    var fixture = fixture();
    var binary = fixture.original().aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINARY)
        .findFirst().orElseThrow();
    var owner = fixture.projection().nodes().stream()
        .filter(node -> binary.nodeId().equals(node.properties().get("sourceNodeKey"))).findFirst().orElseThrow();
    var children = fixture.projection().relationships().stream()
        .filter(link -> link.sourceKey().equals(owner.key()) && link.type().equals("AST_CHILD")).findFirst().orElseThrow();
    var ordered = fixture.projection().relationships().stream().filter(link -> link.sourceKey().equals(children.targetKey())).toList();
    assertEquals(List.of(0L, 1L), ordered.stream().map(link -> link.properties().get("position")).toList());
    assertNotEquals(ordered.getFirst().targetKey(), ordered.getLast().targetKey());
    var target = ordered.getLast();
    var driver = supplied(fixture.projection());
    driver.relationshipRows = rows -> changeRow(rows, target.key(), row -> {
      var properties = properties(row);
      assertEquals(1L, properties.put("position", 0L));
      row.put("properties", Map.copyOf(properties));
      return row;
    });

    // Act
    var failure = reject(fixture, driver);

    // Assert
    assertFailure(fixture, driver, failure, IllegalArgumentException.class, "Materialized AST fields");
  }

  @Test
  void givenValidEnvelope_whenOnlyThePayloadIsTruncated_thenTheSourceContractFailureSurvivesTheAdapter() throws IOException {
    // Arrange
    var fixture = fixture();
    var truncated = Base64.getEncoder().encodeToString(Arrays.copyOf(fixture.bytes(), fixture.bytes().length - 1));
    var driver = supplied(withRootProperty(fixture.projection(), "payload", truncated));
    assertEquals(truncated, driver.stored.root().properties().get("payload"));

    // Act
    var failure = reject(fixture, driver);

    // Assert
    assertFailure(fixture, driver, failure, AstContractException.class, "Retained AST decoding failed");
    assertInstanceOf(IllegalArgumentException.class, failure.effectivePrimary().getCause());
  }

  @Test
  void givenValidEnvelope_whenOnlyItsStoredDigestDiffers_thenTheIdentityContradictionCannotReturnAnOutcome() throws IOException {
    // Arrange
    var fixture = fixture();
    var changed = withRootProperty(fixture.projection(), "canonicalContentDigest", "different-content");
    var driver = supplied(changed);
    assertEquals("different-content", changed.root().properties().get("canonicalContentDigest"));
    assertNotEquals(fixture.original().identity().canonicalContentDigest(), changed.root().properties().get("canonicalContentDigest"));

    // Act
    var failure = reject(fixture, driver);

    // Assert
    assertFailure(fixture, driver, failure, IllegalArgumentException.class, "Materialized AST fields");
  }

  @Test
  void givenCompleteEnvelope_whenOnlyStoredCompletionChanges_thenTheContradictionCannotBecomeAnIncompleteLookup() throws IOException {
    // Arrange
    var fixture = fixture();
    var driver = supplied(withRootProperty(fixture.projection(), "completion", "INCOMPLETE"));
    assertEquals("INCOMPLETE", driver.stored.root().properties().get("completion"));

    // Act
    var failure = reject(fixture, driver);

    // Assert
    assertFailure(fixture, driver, failure, IllegalArgumentException.class, "Materialized AST fields");
  }

  @TestFactory
  Stream<DynamicTest> givenValidRecords_whenOneTopologyFactChanges_thenEachFaultIsIndependentlyRejected() throws IOException {
    // Arrange: immutable compiler values are shared only within this factory invocation.
    var fixture = fixture();
    return Stream.of("ExtraLabel", "ExtraNode", "DuplicateLink", "MissingLink", "ForeignEndpoint", "FloatingOrdinal")
        .map(fault -> DynamicTest.dynamicTest("given" + fault + "_whenFindingSuppliedRecords_thenDecodeRejectsTheSingleFault", () -> {
          // Arrange
          var driver = supplied(fixture.projection());
          switch (fault) {
            case "ExtraLabel" -> driver.nodeRows = rows -> changeRow(rows, fixture.projection().root().key(), row -> {
              assertEquals(List.of("AstEntity", "AstResult"), row.get("labels"));
              row.put("labels", List.of("AstEntity", "AstResult", "Unexpected"));
              return row;
            });
            case "ExtraNode" -> driver.nodeRows = rows -> {
              var extra = new HashMap<>(rows.getLast());
              var key = extra.get("key") + ":extra";
              extra.put("key", key);
              var properties = properties(extra);
              properties.put("key", key);
              extra.put("properties", Map.copyOf(properties));
              var changed = new ArrayList<>(rows);
              changed.add(Map.copyOf(extra));
              assertEquals(rows.size() + 1, changed.size());
              return List.copyOf(changed);
            };
            case "DuplicateLink" -> driver.relationshipRows = rows -> {
              var changed = new ArrayList<>(rows);
              changed.add(rows.getLast());
              assertEquals(2L, changed.stream().filter(rows.getLast()::equals).count());
              return List.copyOf(changed);
            };
            case "MissingLink" -> driver.relationshipRows = rows -> {
              var changed = new ArrayList<>(rows);
              var removed = changed.removeLast();
              assertEquals(rows.size() - 1, changed.size());
              assertTrue(changed.stream().noneMatch(row -> row.get("key").equals(removed.get("key"))));
              return List.copyOf(changed);
            };
            case "ForeignEndpoint" -> driver.relationshipRows = rows -> changeRow(rows,
                fixture.projection().relationships().getLast().key(), row -> {
                  assertEquals(fixture.original().identity().astResultKey(), row.put("targetAstResultKey", "other-subject"));
                  return row;
                });
            case "FloatingOrdinal" -> driver.nodeRows = rows -> changeRow(rows, fixture.projection().nodes().get(1).key(), row -> {
              var properties = properties(row);
              var ordinal = assertInstanceOf(Long.class, properties.get("ordinal"));
              properties.put("ordinal", ordinal.doubleValue());
              row.put("properties", Map.copyOf(properties));
              return row;
            });
            default -> throw new AssertionError(fault);
          }

          // Act
          var failure = reject(fixture, driver);

          // Assert
          var message = switch (fault) {
            case "ExtraLabel" -> "exact namespace";
            case "DuplicateLink" -> "unique keys";
            case "ForeignEndpoint" -> "exact owned namespace";
            default -> "Materialized AST fields";
          };
          assertFailure(fixture, driver, failure, IllegalArgumentException.class, message);
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenUnsupportedIdentityAndMalformedPayload_whenFinding_thenIncompatiblePrecedesPayloadDecoding() throws IOException {
    // Arrange
    var fixture = fixture();
    var original = fixture.original().identity();
    return Stream.of("format", "profile").map(component -> DynamicTest.dynamicTest(
        "givenUnsupported" + component + "_whenFindingMalformedPayload_thenRequestedIdentityRemainsIncompatible", () -> {
          // Arrange
          var unknown = AstIdentity.fromContent(original.softwareSignature(),
              component.equals("format") ? "java-source/99" : original.sourceModelVersion(),
              component.equals("profile") ? "java-source-expanded/99" : original.astProfileVersion(),
              original.semanticProductionRevision(), original.canonicalContentDigest());
          var properties = new HashMap<>(fixture.projection().root().properties());
          properties.put("key", unknown.astResultKey());
          properties.put("astResultKey", unknown.astResultKey());
          properties.put("astProfileVersion", unknown.astProfileVersion());
          properties.put("formatVersion", unknown.sourceModelVersion());
          properties.put("sourceModelVersion", unknown.sourceModelVersion());
          properties.put("payload", "invalid-base64!");
          var driver = supplied(new Neo4jAstProjection(unknown,
              List.of(new Neo4jNode(unknown.astResultKey(), "AstResult", properties)), List.of()));

          // Act
          var lookup = find(unknown, driver);

          // Assert
          assertEquals(unknown, lookup.requestedIdentity());
          assertEquals(AstLookupStatus.INCOMPATIBLE, lookup.status());
          assertEquals(component.equals("format") ? unknown.sourceModelVersion() : unknown.astProfileVersion(),
              lookup.unsupportedFormat().orElseThrow());
          assertTrue(lookup.outcome().isEmpty());
          assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
          assertOriginal(fixture);
        }));
  }

  @Test
  void givenUnsupportedFormatAndContradictoryProfileWithMalformedPayload_whenQuerying_thenFormatPrecedesInterpretation()
      throws IOException {
    // Arrange
    var fixture = fixture();
    var identity = fixture.original().identity();
    var unsupported = "java-source/99";
    var projection = withRootProperty(fixture.projection(), "formatVersion", unsupported);
    projection = withRootProperty(projection, "astProfileVersion", "java-source-expanded/99");
    projection = withRootProperty(projection, "payload", "invalid-base64!");
    var driver = supplied(projection);
    assertNotEquals(identity.astProfileVersion(), projection.root().properties().get("astProfileVersion"));

    // Act
    var lookup = query(identity, driver);

    // Assert
    assertEquals(identity, lookup.requestedIdentity());
    assertEquals(AstLookupStatus.INCOMPATIBLE, lookup.status());
    assertEquals(unsupported, lookup.unsupportedFormat().orElseThrow());
    assertTrue(lookup.outcome().isEmpty());
    assertEquals(1, driver.queries.size());
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
    assertOriginal(fixture);
  }

  @Test
  void givenMatchingUnsupportedProfileAndMalformedRows_whenQuerying_thenRequiredReadsPrecedeIncompatibleAdmission()
      throws IOException {
    // Arrange
    var fixture = fixture();
    var projection = unsupportedProfileRoot(fixture);
    var identity = projection.identity();
    var driver = supplied(projection);
    driver.malformedNodeRecords = true;
    assertEquals(identity.astProfileVersion(), projection.root().properties().get("astProfileVersion"));

    // Act
    var lookup = query(identity, driver);

    // Assert
    assertEquals(identity, lookup.requestedIdentity());
    assertEquals(AstLookupStatus.INCOMPATIBLE, lookup.status());
    assertEquals(identity.astProfileVersion(), lookup.unsupportedFormat().orElseThrow());
    assertTrue(lookup.outcome().isEmpty());
    assertTrue(driver.queries.stream().anyMatch(statement -> statement.contains("labels(entity)")));
    assertTrue(driver.queries.stream().anyMatch(statement -> statement.contains("type(relation)")));
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
    assertOriginal(fixture);
  }

  @Test
  void givenMatchingUnsupportedProfileAndLaterStreamFailure_whenQuerying_thenReadFailurePreventsIncompatibleAdmission()
      throws IOException {
    // Arrange
    var fixture = fixture();
    var projection = unsupportedProfileRoot(fixture);
    var identity = projection.identity();
    var driver = supplied(projection);
    driver.malformedNodeRecords = true;
    var primary = new Neo4jException("Read failed after malformed source rows");
    driver.nodeStreamFailure = primary;
    assertEquals(identity.astProfileVersion(), projection.root().properties().get("astProfileVersion"));

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query(identity, driver));

    // Assert
    assertEquals(RepositoryOperationContext.queryAst(identity), failure.context());
    assertEquals(Neo4jRepositoryPhase.READ, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    assertSame(primary, failure.effectivePrimary().getCause());
    assertEquals(0, failure.getSuppressed().length);
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
    assertOriginal(fixture);
  }

  @TestFactory
  Stream<DynamicTest> givenMissingOrBlankFormatMetadata_whenQuerying_thenEachRequiredFormatFailurePreventsNormalLookup()
      throws IOException {
    // Arrange
    var fixture = fixture();
    var partitions = List.of("Missing", "Empty", "Whitespace");

    // Act
    var observations = partitions.stream().map(partition -> DynamicTest.dynamicTest(
        "given" + partition + "FormatMetadata_whenQuerying_thenRequiredFormatFailurePreventsNormalLookup", () -> {
          // Arrange
          var root = fixture.projection().root();
          var properties = new HashMap<>(root.properties());
          if (partition.equals("Missing")) {
            properties.remove("formatVersion");
          } else {
            properties.put("formatVersion", partition.equals("Empty") ? "" : " \t\n");
          }
          var nodes = new ArrayList<>(fixture.projection().nodes());
          nodes.set(nodes.indexOf(root), new Neo4jNode(root.key(), root.label(), properties));
          var driver = supplied(new Neo4jAstProjection(fixture.original().identity(), nodes, fixture.projection().relationships()));

          // Act
          var failure = assertThrows(Neo4jRepositoryOperationException.class,
              () -> query(fixture.original().identity(), driver));

          // Assert
          assertFailure(fixture, driver, failure, IllegalArgumentException.class, "explicit format version");
          assertEquals(1, driver.queries.size());
        }));

    // Assert
    return observations;
  }

  @Test
  void givenUnsupportedProfileAndRelationshipStreamTailFailure_whenQuerying_thenExactReadFailurePreventsIncompatibility()
      throws IOException {
    // Arrange
    var fixture = fixture();
    var projection = unsupportedProfileWithRelationship(fixture);
    var identity = projection.identity();
    var driver = supplied(projection);
    driver.malformedNodeRecords = true;
    var primary = new Neo4jException("Read failed after a source relationship row");
    driver.relationshipStreamFailure = primary;
    assertEquals(identity.astProfileVersion(), projection.root().properties().get("astProfileVersion"));
    assertEquals(1, projection.relationships().size());

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query(identity, driver));

    // Assert
    assertEquals(RepositoryOperationContext.queryAst(identity), failure.context());
    assertEquals(Neo4jRepositoryPhase.READ, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    assertSame(primary, failure.effectivePrimary().getCause());
    assertEquals(0, failure.getSuppressed().length);
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
    assertOriginal(fixture);
  }

  @Test
  void givenNoStoredSubject_whenFinding_thenAbsenceRetainsTheRequestedIdentity() throws IOException {
    // Arrange
    var fixture = fixture();
    var driver = new AstRepositoryDriverStub();

    // Act
    var lookup = find(fixture.original().identity(), driver);

    // Assert
    assertEquals(fixture.original().identity(), lookup.requestedIdentity());
    assertEquals(AstLookupStatus.NOT_FOUND, lookup.status());
    assertTrue(lookup.outcome().isEmpty());
    assertTrue(lookup.unsupportedFormat().isEmpty());
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
    assertOriginal(fixture);
  }

  private Fixture fixture() throws IOException {
    var outcome = DeclarationAstRetentionFixture.produce(directory, "Stored.java", """
        class Stored {
          int first = 17;
          int second = 29;
          int sum() { return 41 + 53; }
        }
        """);
    var original = outcome.result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    var projection = new Neo4jAstResultCodec().map(outcome);
    var driver = supplied(projection);
    var control = find(original.identity(), driver);
    assertEquals(original.identity(), control.requestedIdentity());
    assertEquals(AstLookupStatus.FOUND, control.status());
    var retained = control.outcome().orElseThrow().result().orElseThrow();
    DeclarationAstRetentionFixture.assertPreserved(original, retained);
    assertSourceValues(retained);
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
    return new Fixture(original, projection, AstCodec.encode(original));
  }

  private static void assertSourceValues(AstResult result) {
    var owner = result.program().types().stream().filter(type -> type.qualifiedName().equals("Stored")).findFirst().orElseThrow();
    assertEquals(List.of("first", "second"), owner.fields().stream().map(field -> field.name()).toList());
    var method = result.program().methods().stream().filter(value -> value.name().equals("sum")).findFirst().orElseThrow();
    var binary = result.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.BINARY).findFirst().orElseThrow();
    assertEquals(List.of(SyntaxRole.LEFT_OPERAND, SyntaxRole.RIGHT_OPERAND), binary.children().stream().map(child -> child.role()).toList());
    assertEquals("41", binary.requiredChild(SyntaxRole.LEFT_OPERAND).value(SyntaxValue.Literal.class).text());
    assertEquals("53", binary.requiredChild(SyntaxRole.RIGHT_OPERAND).value(SyntaxValue.Literal.class).text());
  }

  private static AstRepositoryDriverStub supplied(Neo4jAstProjection projection) {
    var driver = new AstRepositoryDriverStub();
    driver.stored = projection;
    return driver;
  }

  private static AstLookup find(AstIdentity identity, AstRepositoryDriverStub driver) {
    try (var repository = new Neo4jAstResultRepository(driver.driver(), SessionConfig.defaultConfig(),
        RepositoryOperationContext.queryAst(identity))) {
      return repository.find(identity);
    }
  }

  private static AstLookup query(AstIdentity identity, AstRepositoryDriverStub driver) {
    return AnalysisCapabilities.astQuery(context -> new Neo4jAstResultRepository(driver.driver(), SessionConfig.defaultConfig(), context))
        .findAst(identity);
  }

  private static Neo4jAstProjection unsupportedProfileRoot(Fixture fixture) {
    var original = fixture.original().identity();
    var identity = AstIdentity.fromContent(original.softwareSignature(), original.sourceModelVersion(), "java-source-expanded/99",
        original.semanticProductionRevision(), original.canonicalContentDigest());
    var properties = new HashMap<>(fixture.projection().root().properties());
    properties.put("key", identity.astResultKey());
    properties.put("astResultKey", identity.astResultKey());
    properties.put("astProfileVersion", identity.astProfileVersion());
    return new Neo4jAstProjection(identity, List.of(new Neo4jNode(identity.astResultKey(), "AstResult", properties)), List.of());
  }

  private static Neo4jAstProjection unsupportedProfileWithRelationship(Fixture fixture) {
    var unsupported = unsupportedProfileRoot(fixture);
    var identity = unsupported.identity();
    var original = fixture.projection();
    var rootKey = original.root().key();
    var relationship = original.relationships().stream()
        .filter(value -> !rootKey.equals(value.sourceKey()) && !rootKey.equals(value.targetKey())).findFirst().orElseThrow();
    var nodes = new ArrayList<>(unsupported.nodes());
    for (var node : original.nodes()) {
      if (node.key().equals(relationship.sourceKey()) || node.key().equals(relationship.targetKey())) {
        nodes.add(new Neo4jNode(node.key(), node.label(), scopedProperties(node.properties(), identity)));
      }
    }
    var link = new Neo4jRelationship(relationship.key(), relationship.type(), relationship.sourceKey(), relationship.targetKey(),
        scopedProperties(relationship.properties(), identity));
    return new Neo4jAstProjection(identity, nodes, List.of(link));
  }

  private static Map<String, Object> scopedProperties(Map<String, Object> original, AstIdentity identity) {
    var properties = new HashMap<>(original);
    properties.put("astResultKey", identity.astResultKey());
    return properties;
  }

  private static Neo4jRepositoryOperationException reject(Fixture fixture, AstRepositoryDriverStub driver) {
    return assertThrows(Neo4jRepositoryOperationException.class, () -> find(fixture.original().identity(), driver));
  }

  private static void assertFailure(Fixture fixture, AstRepositoryDriverStub driver, Neo4jRepositoryOperationException failure,
      Class<? extends IllegalArgumentException> primaryType, String message) {
    assertEquals(RepositoryOperationContext.queryAst(fixture.original().identity()), failure.context());
    assertEquals(Neo4jRepositoryPhase.DECODE, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    assertInstanceOf(primaryType, failure.effectivePrimary());
    assertTrue(failure.effectivePrimary().getMessage().contains(message), failure.effectivePrimary().toString());
    assertEquals(0, failure.getSuppressed().length);
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
    assertOriginal(fixture);
  }

  private static void assertOriginal(Fixture fixture) {
    assertArrayEquals(fixture.bytes(), AstCodec.encode(fixture.original()));
    assertSourceValues(fixture.original());
    DeclarationAstRetentionFixture.assertPreserved(fixture.original(),
        new Neo4jAstResultCodec().decode(fixture.projection(), fixture.original().identity()).result().orElseThrow());
  }

  private static Neo4jAstProjection withRootProperty(Neo4jAstProjection projection, String name, Object value) {
    var root = projection.root();
    var properties = new HashMap<>(root.properties());
    assertNotEquals(value, properties.put(name, value));
    var nodes = new ArrayList<>(projection.nodes());
    nodes.set(nodes.indexOf(root), new Neo4jNode(root.key(), root.label(), properties));
    return new Neo4jAstProjection(projection.identity(), nodes, projection.relationships());
  }

  private static List<Map<String, Object>> changeRow(List<Map<String, Object>> rows, String key,
      UnaryOperator<Map<String, Object>> change) {
    assertEquals(1L, rows.stream().filter(row -> key.equals(row.get("key"))).count());
    var changed = rows.stream().map(row -> key.equals(row.get("key")) ? Map.copyOf(change.apply(new HashMap<>(row))) : row).toList();
    assertEquals(rows.size(), changed.size());
    assertEquals(1L, IntStream.range(0, rows.size()).filter(index -> !rows.get(index).equals(changed.get(index))).count());
    return changed;
  }

  private static Map<String, Object> properties(Map<String, Object> row) {
    var result = new HashMap<String, Object>();
    ((Map<?, ?>) row.get("properties")).forEach((key, value) -> result.put((String) key, value));
    return result;
  }

  private record Fixture(AstResult original, Neo4jAstProjection projection, byte[] bytes) {}
}
