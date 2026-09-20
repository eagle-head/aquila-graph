package org.aquilagraph.persistence;

import java.util.Map;
import java.util.Objects;

public record ProjectedRelationship(
    String key,
    String type,
    String sourceKey,
    String targetKey,
    Map<String, Object> properties) {
  public ProjectedRelationship {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(sourceKey, "sourceKey");
    Objects.requireNonNull(targetKey, "targetKey");
    properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
  }
}
