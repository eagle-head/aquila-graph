package org.aquilagraph.repository.neo4j;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * Validated Neo4j connection values.
 *
 * @param uri
 *          Neo4j driver URI
 * @param username
 *          authentication username
 * @param password
 *          authentication password
 * @param database
 *          target database name
 */
public record Neo4jConnectionSettings(URI uri, String username, String password, String database) {
  /** Validates connection values. */
  public Neo4jConnectionSettings {
    Objects.requireNonNull(uri, "uri");
    username = requireText(username, "username");
    password = requireText(password, "password");
    database = requireText(database, "database");
  }

  /**
   * Reads connection values from the process environment contract.
   *
   * @param environment
   *          environment-variable map
   * @return validated connection settings
   */
  public static Neo4jConnectionSettings fromEnvironment(Map<String, String> environment) {
    Objects.requireNonNull(environment, "environment");
    var uri = URI.create(environment.getOrDefault("AQUILA_GRAPH_NEO4J_URI", "neo4j://localhost:7687"));
    var username = environment.getOrDefault("AQUILA_GRAPH_NEO4J_USERNAME", "neo4j");
    var password = environment.get("AQUILA_GRAPH_NEO4J_PASSWORD");
    if (password == null || password.isBlank()) {
      password = environment.get("NEO4J_PASSWORD");
    }
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("Set AQUILA_GRAPH_NEO4J_PASSWORD or NEO4J_PASSWORD before using --persist");
    }
    var database = environment.getOrDefault("AQUILA_GRAPH_NEO4J_DATABASE", "neo4j");
    return new Neo4jConnectionSettings(uri, username, password, database);
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
    return value;
  }
}
