package org.aquilagraph.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.syntax.SyntaxNode;

record ExceptionType(String typeName, List<String> assignableToTypeNames, boolean unknown) {
  ExceptionType {
    Objects.requireNonNull(typeName, "typeName");
    assignableToTypeNames = List.copyOf(Objects.requireNonNull(assignableToTypeNames, "assignableToTypeNames"));
    if (unknown && !assignableToTypeNames.isEmpty()) {
      throw new IllegalArgumentException("An unknown exception cannot declare an assignability hierarchy");
    }
  }

  static ExceptionType resolved(String typeName, List<String> assignableToTypeNames) {
    var hierarchy = new LinkedHashSet<String>();
    hierarchy.add(Objects.requireNonNull(typeName, "typeName"));
    hierarchy.addAll(Objects.requireNonNull(assignableToTypeNames, "assignableToTypeNames"));
    return new ExceptionType(typeName, List.copyOf(hierarchy), false);
  }

  static ExceptionType runtime(String typeName) {
    return resolved(typeName,
        List.of(typeName, "java.lang.RuntimeException", "java.lang.Exception", "java.lang.Throwable", "java.lang.Object"));
  }

  static ExceptionType fromSyntax(SyntaxNode node, String typeName) {
    Objects.requireNonNull(node, "node");
    return node.semanticReference().stream().filter(reference -> !reference.assignableToTypeNames().isEmpty())
        .map(reference -> resolved(typeName, reference.assignableToTypeNames())).findFirst().orElseGet(() -> unknown(typeName));
  }

  static ExceptionType unknown(String category) {
    return new ExceptionType(category, List.of(), true);
  }
}
