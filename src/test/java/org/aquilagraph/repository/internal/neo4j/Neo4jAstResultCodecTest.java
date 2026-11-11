package org.aquilagraph.repository.internal.neo4j;



import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Neo4jAstResultCodecTest {
  @TempDir Path directory;

  @Test
  void givenCompleteAttributedSource_whenMappingAndDecoding_thenEveryRetainedValueAndCorrelationSurvives() throws IOException {
    // Arrange
    var outcome = produce("""
        @Deprecated final class Stored<T extends Number> {
          int value(int x) { return x + 7; }
          String join(Object value) { return "prefix" + value; }
        }
        """);
    var codec = new Neo4jAstResultCodec();
    var original = outcome.result().orElseThrow();

    // Act
    var projection = codec.map(outcome);
    var decoded = codec.decode(projection, original.identity());
    var retained = decoded.result().orElseThrow();

    // Assert
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(original.identity(), retained.identity());
    assertEquals(original.snapshot(), retained.snapshot());
    assertEquals(original.environment(), retained.environment());
    assertEquals(original.program(), retained.program());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(original.coverage(), retained.coverage());
    assertEquals(original.diagnostics(), retained.diagnostics());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
    var labels = projection.nodes().stream().map(Neo4jNode::label).collect(Collectors.toSet());
    assertTrue(labels.containsAll(Set.of("AstResult", "AstInput", "AstEnvironment", "AstSourceFile", "AstDeclaration", "AstOccurrence",
        "AstType", "AstBody", "AstEvidence", "AstCoverage", "AstAnnotationValue", "AstValue")), labels.toString());
    assertEquals(AstCodec.materialize(original).nodes().size() + 1, projection.nodes().size());
    assertEquals(AstCodec.materialize(original).links().size() + 1, projection.relationships().size());
    assertTrue(projection.nodes().stream().noneMatch(node -> node.properties().containsKey("runId")));
  }

  @Test
  void givenCodecProjectionAndContradictoryRoots_whenAdmittingProjection_thenExactlyOneRootMustHaveTheSubjectKey() throws IOException {
    // Arrange
    var outcome = produce("class Stored {}");
    var identity = outcome.result().orElseThrow().identity();
    var scope = identity.astResultKey();
    var wronglyKeyed = new Neo4jNode("other-root", "AstResult", Map.of("key", "other-root", "astResultKey", scope));
    var codec = new Neo4jAstResultCodec();

    // Act
    var produced = codec.map(outcome);
    var root = produced.root();
    var rejected = List.of(List.<Neo4jNode>of(), List.of(wronglyKeyed), List.of(wronglyKeyed, root), List.of(root, wronglyKeyed))
        .stream().map(nodes -> assertThrows(IllegalArgumentException.class, () -> new Neo4jAstProjection(identity, nodes, List.of())))
        .toList();

    // Assert
    assertEquals("AstResult", root.label());
    assertEquals(scope, root.key());
    assertEquals(List.of(root), produced.nodes().stream().filter(node -> node.label().equals("AstResult")).toList());
    rejected.forEach(failure -> assertEquals("AST storage requires one exact result root", failure.getMessage()));
  }

  @Test
  void givenCanonicalValueOrdinals_whenMapping_thenStoredNodeAndEndpointKeysPreserveTheirIdentityFormat() throws IOException {
    // Arrange
    var outcome = produce("class Stored { int value(int x) { return x + x; } }");
    var original = outcome.result().orElseThrow();
    var materialization = AstCodec.materialize(original);
    var scope = original.identity().astResultKey();
    var expectedKeys = materialization.nodes().stream()
        .collect(Collectors.toMap(node -> (long) node.ordinal(), node -> StableId.sha256("ast-value", scope, Integer.toString(node.ordinal()))));

    // Act
    var projection = new Neo4jAstResultCodec().map(outcome);

    // Assert
    assertEquals(expectedKeys, projection.nodes().stream().filter(node -> node.properties().containsKey("ordinal"))
        .collect(Collectors.toMap(node -> (Long) node.properties().get("ordinal"), Neo4jNode::key)));
    var expectedLinks = materialization.links().stream().map(link -> {
      var source = expectedKeys.get((long) link.sourceOrdinal());
      var target = expectedKeys.get((long) link.targetOrdinal());
      var type = Neo4jAstSchema.relationship(link.field());
      return StableId.sha256("ast-field", scope, source, target, type, link.field(), Integer.toString(link.position()));
    }).collect(Collectors.toSet());
    var valueRootKey = StableId.sha256("ast-field", scope, scope, expectedKeys.get(0L), "HAS_AST_VALUES", "values", "0");
    expectedLinks.add(valueRootKey);
    assertEquals(expectedLinks, projection.relationships().stream().map(Neo4jRelationship::key).collect(Collectors.toSet()));
  }

  @Test
  void givenValidEnvelopeAndChangedMaterializedField_whenDecoding_thenCorruptionRemainsObservable() throws IOException {
    // Arrange
    var outcome = produce("class Stored { int value() { return 1; } }");
    var codec = new Neo4jAstResultCodec();
    var projection = codec.map(outcome);
    var nodes = new ArrayList<>(projection.nodes());
    var original = nodes.get(1);
    var changed = new HashMap<>(original.properties());
    changed.put("tag", "text");
    nodes.set(1, new Neo4jNode(original.key(), original.label(), changed));
    var corrupted = new Neo4jAstProjection(projection.identity(), nodes, projection.relationships());

    // Act and assert
    assertThrows(IllegalArgumentException.class, () -> codec.decode(corrupted, projection.identity()));
  }

  @Test
  void givenRepeatedOrderedComponents_whenOneLinkPositionChanges_thenEnvelopeCannotAuthenticateTheChangedProjection() throws IOException {
    // Arrange
    var outcome = produce("class Stored { int value(int x) { return x + x; } }");
    var codec = new Neo4jAstResultCodec();
    var projection = codec.map(outcome);
    var relationships = new ArrayList<>(projection.relationships());
    var original = relationships.get(1);
    var changed = new HashMap<>(original.properties());
    changed.put("position", ((Long) changed.get("position")) + 1);
    relationships.set(1, new Neo4jRelationship(original.key(), original.type(), original.sourceKey(), original.targetKey(), changed));
    var corrupted = new Neo4jAstProjection(projection.identity(), projection.nodes(), relationships);

    // Act and assert
    assertThrows(IllegalArgumentException.class, () -> codec.decode(corrupted, projection.identity()));
  }

  @Test
  void givenIncompleteSource_whenMapping_thenNoStorageProjectionIsAdmitted() throws IOException {
    // Arrange
    var outcome = produce("class Stored { int value() { return missing; } }");

    // Act and assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertThrows(IllegalArgumentException.class, () -> new Neo4jAstResultCodec().map(outcome));
  }

  @Test
  void givenForeignQualifiedIdentity_whenDecoding_thenNoSourceOutcomeIsReturned() throws IOException {
    // Arrange
    var first = produce("class Stored { int value() { return 1; } }");
    var second = produce("class Stored { int value() { return 2; } }");
    var codec = new Neo4jAstResultCodec();
    var stored = codec.map(first);

    // Act and assert
    assertThrows(IllegalArgumentException.class, () -> codec.decode(stored, second.result().orElseThrow().identity()));
  }

  @Test
  void givenUnorderedStoredRows_whenDecoding_thenAllValuesAndCorrelationsRemainExact() throws IOException {
    // Arrange
    var outcome = produce("class Stored { int value(int x) { return x + x; } }");
    var codec = new Neo4jAstResultCodec();
    var projection = codec.map(outcome);
    var nodes = new ArrayList<>(projection.nodes());
    var relationships = new ArrayList<>(projection.relationships());
    Collections.reverse(nodes);
    Collections.reverse(relationships);

    // Act
    var decoded = codec.decode(new Neo4jAstProjection(projection.identity(), nodes, relationships), projection.identity());

    // Assert
    assertArrayEquals(AstCodec.encode(outcome.result().orElseThrow()), AstCodec.encode(decoded.result().orElseThrow()));
  }

  @Test
  void givenMissingStoredRelationship_whenDecoding_thenExactMaterializationIsStillRequired() throws IOException {
    assertRelationshipMutation(false);
  }

  @Test
  void givenExtraStoredRelationship_whenDecoding_thenExactMaterializationIsStillRequired() throws IOException {
    assertRelationshipMutation(true);
  }

  private void assertRelationshipMutation(boolean extra) throws IOException {
    // Arrange
    var outcome = produce("class Stored { int value(int x) { return x + x; } }");
    var codec = new Neo4jAstResultCodec();
    var projection = codec.map(outcome);
    var relationships = new ArrayList<>(projection.relationships());
    if (extra) {
      var original = relationships.getFirst();
      var key = original.key() + ":extra";
      var properties = new HashMap<>(original.properties());
      properties.put("key", key);
      relationships.add(new Neo4jRelationship(key, original.type(), original.sourceKey(), original.targetKey(), properties));
    } else {
      relationships.removeLast();
    }
    var corrupted = new Neo4jAstProjection(projection.identity(), projection.nodes(), relationships);

    // Act and assert
    assertThrows(IllegalArgumentException.class, () -> codec.decode(corrupted, projection.identity()));
  }

  private AstOutcome produce(String source) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), source))), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED);
  }
}
