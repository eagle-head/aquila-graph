package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

@ExtendWith(Neo4jIntegration.class)
class Neo4jFixtureCleanupNeo4jIT {
  @Test
  void givenOwnedFixturesAcrossCleanupBatches_whenDiscardingOneSubject_thenOtherSubjectRemains() {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var scope = "cleanup-" + UUID.randomUUID();
    var other = "cleanup-" + UUID.randomUUID();
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      try {
        session.run("UNWIND range(0, 2500) AS ordinal "
            + "CREATE (entity:AstEntity {astResultKey: $scope, key: toString(ordinal)}) "
            + "WITH collect(entity) AS entities UNWIND range(0, size(entities) - 2) AS ordinal "
            + "WITH entities[ordinal] AS source, entities[ordinal + 1] AS target "
            + "CREATE (source)-[:FIXTURE_NEXT]->(target)", Values.parameters("scope", scope)).consume();
        session.run("UNWIND range(0, 6) AS ordinal "
            + "CREATE (:AstEntity {astResultKey: $scope, key: toString(ordinal)})", Values.parameters("scope", other)).consume();

        // Act
        Neo4jIntegration.discardAstFixture(session, scope);

        // Assert
        assertEquals(0, session.run("MATCH (entity:AstEntity {astResultKey: $scope}) RETURN count(entity) AS count",
            Values.parameters("scope", scope)).single().get("count").asLong());
        assertEquals(7, session.run("MATCH (entity:AstEntity {astResultKey: $scope}) RETURN count(entity) AS count",
            Values.parameters("scope", other)).single().get("count").asLong());
      } finally {
        Neo4jIntegration.discardAstFixture(session, scope);
        Neo4jIntegration.discardAstFixture(session, other);
      }
    }
  }
}
