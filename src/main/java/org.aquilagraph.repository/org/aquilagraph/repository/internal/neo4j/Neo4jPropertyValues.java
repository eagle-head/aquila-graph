package org.aquilagraph.repository.internal.neo4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates and freezes the scalar and list property shapes emitted by the storage mappers. */
final class Neo4jPropertyValues {
  private Neo4jPropertyValues() {
  }

  static Map<String, Object> copyOf(Map<String, Object> properties) {
    Objects.requireNonNull(properties, "properties");
    var copied = new LinkedHashMap<String, Object>();
    properties.forEach((name, value) -> copied.put(Objects.requireNonNull(name, "property name"), copyValue(value)));
    return Map.copyOf(copied);
  }

  private static Object copyValue(Object value) {
    Objects.requireNonNull(value, "property value");
    if (value instanceof List<?> values) {
      var copied = List.copyOf(values);
      if (copied.stream().anyMatch(element -> !isScalar(element))) {
        throw new IllegalArgumentException("Projected property lists require scalar values");
      }
      if (!copied.isEmpty() && copied.stream().anyMatch(element -> !element.getClass().equals(copied.getFirst().getClass()))) {
        throw new IllegalArgumentException("Projected property lists require one scalar type");
      }
      return copied;
    }
    if (!isScalar(value)) {
      throw new IllegalArgumentException("Unsupported projected property value: " + value.getClass().getSimpleName());
    }
    return value;
  }

  private static boolean isScalar(Object value) {
    return value instanceof String || value instanceof Boolean || value instanceof Byte || value instanceof Short
        || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double;
  }
}
