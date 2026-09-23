package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.JavaType;

record TranslatedType(List<JavaType> types, List<TranslatedMethodBody> methodBodies) {
  TranslatedType {
    types = List.copyOf(Objects.requireNonNull(types, "types"));
    if (types.isEmpty()) {
      throw new IllegalArgumentException("A translated type tree must contain its root type");
    }
    methodBodies = List.copyOf(Objects.requireNonNull(methodBodies, "methodBodies"));
  }
}
