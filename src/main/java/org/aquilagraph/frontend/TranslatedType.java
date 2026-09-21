package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.syntax.MethodSyntax;

record TranslatedType(List<JavaType> types, List<MethodSyntax> methodSyntax) {
  TranslatedType {
    types = List.copyOf(Objects.requireNonNull(types, "types"));
    if (types.isEmpty()) {
      throw new IllegalArgumentException("A translated type tree must contain its root type");
    }
    methodSyntax = List.copyOf(Objects.requireNonNull(methodSyntax, "methodSyntax"));
  }
}
