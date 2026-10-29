package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.aquilagraph.source.retention.AstCodec;
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

}
