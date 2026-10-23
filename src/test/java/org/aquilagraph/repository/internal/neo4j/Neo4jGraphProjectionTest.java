package org.aquilagraph.repository.internal.neo4j;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Neo4jGraphProjectionTest {
  @Test
  void givenForeignRunProperty_whenProjectionIsBuilt_thenOwnershipIsRejected() {
    // Arrange
    var node = new Neo4jNode("source", "SourceFile", Map.of("runId", "other", "key", "source"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new Neo4jGraphProjection("run", List.of(node), List.of()));

    // Assert
    assertEquals("Projected properties must match their run and element identities", failure.getMessage());
  }

  @Test
  void givenMismatchedRelationshipKey_whenProjectionIsBuilt_thenIdentityIsRejected() {
    // Arrange
    var node = new Neo4jNode("source", "SourceFile", Map.of("runId", "run", "key", "source"));
    var relationship = new Neo4jRelationship("relation", "HAS_SOURCE", "source", "source", Map.of("runId", "run", "key", "other"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new Neo4jGraphProjection("run", List.of(node), List.of(relationship)));

    // Assert
    assertEquals("Projected properties must match their run and element identities", failure.getMessage());
  }

  @Test
  void givenNestedPropertyValue_whenNodeIsConstructed_thenUnsupportedShapeIsRejected() {
    // Arrange
    var properties = Map.<String, Object>of("runId", "run", "key", "source", "nested", Map.of("value", "nested"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new Neo4jNode("source", "SourceFile", properties));

    // Assert
    assertTrue(failure.getMessage().startsWith("Unsupported projected property value:"));
  }

  @Test
  void givenMutablePropertyList_whenNodeIsConstructed_thenPublishedPropertiesAreImmutable() {
    // Arrange
    var values = new ArrayList<>(List.of("first"));

    // Act
    var node = new Neo4jNode("source", "SourceFile", Map.of("runId", "run", "key", "source", "values", values));
    values.add("second");

    // Assert
    assertEquals(List.of("first"), node.properties().get("values"));
    assertThrows(UnsupportedOperationException.class, () -> ((List<?>) node.properties().get("values")).clear());
  }

  @Test
  void givenDuplicateNodeKeys_whenProjectionIsBuilt_thenRepeatedIdentityIsRejected() {
    // Arrange
    var node = new Neo4jNode("source", "SourceFile", Map.of("runId", "run", "key", "source"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new Neo4jGraphProjection("run", List.of(node, node), List.of()));

    // Assert
    assertEquals("Projected node keys must be unique within a run", failure.getMessage());
  }

  @Test
  void givenDuplicateRelationshipKeysOrMissingEndpoint_whenProjectionIsBuilt_thenInvalidConnectionsAreRejected() {
    // Arrange
    var node = new Neo4jNode("source", "SourceFile", Map.of("runId", "run", "key", "source"));
    var valid = new Neo4jRelationship("relation", "HAS_SOURCE", "source", "source", Map.of("runId", "run", "key", "relation"));
    var missingSource = new Neo4jRelationship("missing-source", "HAS_SOURCE", "absent", "source",
        Map.of("runId", "run", "key", "missing-source"));
    var missingTarget = new Neo4jRelationship("missing-target", "HAS_SOURCE", "source", "absent",
        Map.of("runId", "run", "key", "missing-target"));

    for (var relationships : List.of(List.of(valid, valid), List.of(missingSource), List.of(missingTarget))) {
      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> new Neo4jGraphProjection("run", List.of(node), relationships));

      // Assert
      assertEquals("Projected relationships must be unique and connect projected nodes", failure.getMessage());
    }
  }

  @Test
  void givenNonallowlistedNodeLabelOrRelationshipType_whenProjectionIsBuilt_thenUnknownSchemaNamesAreRejected() {
    // Arrange
    var invalidNode = new Neo4jNode("source", "Unknown", Map.of("runId", "run", "key", "source"));
    var node = new Neo4jNode("source", "SourceFile", Map.of("runId", "run", "key", "source"));
    var relationship = new Neo4jRelationship("relation", "UNKNOWN", "source", "source", Map.of("runId", "run", "key", "relation"));

    // Act
    var nodeFailure = assertThrows(IllegalArgumentException.class,
        () -> new Neo4jGraphProjection("run", List.of(invalidNode), List.of()));
    var relationFailure = assertThrows(IllegalArgumentException.class,
        () -> new Neo4jGraphProjection("run", List.of(node), List.of(relationship)));

    // Assert
    assertEquals("Unsupported node label: Unknown", nodeFailure.getMessage());
    assertEquals("Unsupported relationship type: UNKNOWN", relationFailure.getMessage());
  }

  @Test
  void givenNestedOrHeterogeneousPropertyList_whenNodeIsConstructed_thenUnsupportedListShapeIsRejected() {
    // Arrange
    var cases = Map.<List<?>, String>of(List.of(List.of("nested")), "Projected property lists require scalar values",
        List.of("text", 1), "Projected property lists require one scalar type");

    for (var entry : cases.entrySet()) {
      // Act
      var failure = assertThrows(IllegalArgumentException.class,
          () -> new Neo4jNode("source", "SourceFile", Map.of("runId", "run", "key", "source", "values", entry.getKey())));

      // Assert
      assertEquals(entry.getValue(), failure.getMessage());
    }
  }
}
