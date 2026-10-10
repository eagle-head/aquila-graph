package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Neo4jExecutableAstRetentionTest {
  @TempDir Path directory;

  @Test
  void givenMixedExecutableSource_whenClosedValuesAreMappedAndDecoded_thenAllFamiliesAndTheirCorrelationsSurvive() throws IOException {
    // Arrange
    var outcome = ExecutableAstRetentionFixture.frontend().produce(ExecutableAstRetentionFixture.snapshot(directory),
        ExecutableAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = outcome.result().orElseThrow();
    var codec = new Neo4jAstResultCodec();

    // Act
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    var projection = codec.map(outcome);
    var retained = codec.decode(projection, original.identity()).result().orElseThrow();

    // Assert
    ExecutableAstRetentionFixture.assertPreserved(original, decoded);
    ExecutableAstRetentionFixture.assertPreserved(original, retained);
    assertEquals(AstCodec.materialize(original).nodes().size() + 1, projection.nodes().size());
    assertEquals(AstCodec.materialize(original).links().size() + 1, projection.relationships().size());
    assertTrue(projection.nodes().stream().noneMatch(node -> node.properties().containsKey("runId")));
  }

  @Test
  void givenMixedExecutableValuesAndAlteredChildOrder_whenDecoding_thenSupportedCorruptionCannotReturnAnOutcome() throws IOException {
    // Arrange
    var outcome = ExecutableAstRetentionFixture.frontend().produce(ExecutableAstRetentionFixture.snapshot(directory),
        ExecutableAstRetentionFixture.configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var codec = new Neo4jAstResultCodec();
    var original = codec.map(outcome);
    var links = new ArrayList<>(original.relationships());
    var childLists = links.stream().filter(link -> link.type().equals("AST_CHILD")).map(Neo4jRelationship::targetKey).toList();
    var child = links.stream().filter(link -> childLists.contains(link.sourceKey())
        && link.properties().get("position").equals(1L)).findFirst().orElseThrow();
    var properties = new HashMap<>(child.properties());
    properties.put("position", 0L);
    links.set(links.indexOf(child), new Neo4jRelationship(child.key(), child.type(), child.sourceKey(), child.targetKey(), properties));
    var corrupted = new Neo4jAstProjection(original.identity(), original.nodes(), links);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> codec.decode(corrupted, original.identity()));

    // Assert
    assertTrue(failure.getMessage().contains("Materialized AST fields"));
    ExecutableAstRetentionFixture.assertPreserved(outcome.result().orElseThrow(),
        codec.decode(original, original.identity()).result().orElseThrow());
  }
}
