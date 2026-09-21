package org.aquilagraph.repository.neo4j;

import java.util.Map;
import java.util.Objects;
import org.aquilagraph.model.JavaTypeIdentity;

/** Writes one compiler-resolved Java type identity into Neo4j properties. */
final class Neo4jTypeProperties {
  private Neo4jTypeProperties() {
  }

  static void put(Map<String, Object> properties, String prefix, JavaTypeIdentity type) {
    Objects.requireNonNull(properties, "properties");
    Objects.requireNonNull(prefix, "prefix");
    Objects.requireNonNull(type, "type");
    properties.put(prefix, type.displayName());
    properties.put(prefix + "Key", type.typeKey());
    properties.put(prefix + "Erasure", type.erasedName());
    properties.put(prefix + "Category", type.category().name());
  }
}
