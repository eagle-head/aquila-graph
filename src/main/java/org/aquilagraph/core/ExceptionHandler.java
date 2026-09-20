package org.aquilagraph.core;

import java.util.List;
import java.util.Objects;

record ExceptionHandler(List<String> exceptionTypeNames, String operationNodeId) {
  ExceptionHandler {
    exceptionTypeNames = List.copyOf(Objects.requireNonNull(exceptionTypeNames, "exceptionTypeNames"));
    Objects.requireNonNull(operationNodeId, "operationNodeId");
  }

  boolean catches(ExceptionType thrownType) {
    Objects.requireNonNull(thrownType, "thrownType");
    return exceptionTypeNames.stream().anyMatch(thrownType.assignableToTypeNames()::contains);
  }

  boolean unresolved() {
    return exceptionTypeNames.isEmpty();
  }
}
