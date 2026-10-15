package org.aquilagraph.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

/** Shares one managed database within a test engine and closes it with the root store. */
public final class Neo4jIntegration implements BeforeAllCallback {
  private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(Neo4jIntegration.class);
  private static ManagedNeo4jDatabase active;

  @Override
  public void beforeAll(ExtensionContext context) {
    active = context.getRoot().getStore(NAMESPACE).computeIfAbsent(ManagedNeo4jDatabase.class, ignored -> {
      var preservation = System.getenv().getOrDefault("AQUILA_GRAPH_TEST_PRESERVE_DATA", "false");
      if (!preservation.equals("true") && !preservation.equals("false")) {
        throw new IllegalArgumentException("AQUILA_GRAPH_TEST_PRESERVE_DATA must be true or false");
      }
      try {
        var output = Path.of("target", "neo4j-integration", UUID.randomUUID().toString());
        var descriptor = System.getenv("AQUILA_GRAPH_TEST_PRESERVED_DATA");
        if (descriptor != null) {
          if (!Boolean.parseBoolean(preservation)) {
            throw new IllegalArgumentException("Resuming preserved data requires AQUILA_GRAPH_TEST_PRESERVE_DATA=true");
          }
          return ManagedNeo4jDatabase.resume(output, Path.of(descriptor));
        }
        return ManagedNeo4jDatabase.start(output, Boolean.parseBoolean(preservation));
      } catch (IOException failure) {
        throw new UncheckedIOException(failure);
      }
    }, ManagedNeo4jDatabase.class);
  }

  public static Neo4jConnectionSettings settings() {
    return database().settings();
  }

  public static Map<String, String> environment() {
    return database().environment();
  }

  public static void discardAstFixture(Session session, String astResultKey) {
    session.run("MATCH (entity:AstEntity {astResultKey: $scope}) "
        + "CALL (entity) { DETACH DELETE entity } IN TRANSACTIONS OF 1000 ROWS",
        Values.parameters("scope", astResultKey)).consume();
  }

  private static ManagedNeo4jDatabase database() {
    if (active == null) {
      throw new IllegalStateException("Register Neo4jIntegration before requesting its database");
    }
    return active;
  }
}
