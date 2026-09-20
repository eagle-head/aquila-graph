package org.aquilagraph.persistence;

import java.util.Map;
import java.util.Objects;

public record ProjectedNode(String key, String label, Map<String, Object> properties) {
  public ProjectedNode {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(label, "label");
    properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
  }
}
