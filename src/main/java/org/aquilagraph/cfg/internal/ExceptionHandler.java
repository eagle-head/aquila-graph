package org.aquilagraph.cfg.internal;

import java.util.List;
import java.util.Objects;

record ExceptionHandler(List<String> exceptionTypeNames, String operationNodeId, List<Alternative> alternatives) {
  ExceptionHandler(List<String> exceptionTypeNames, String operationNodeId) {
    this(exceptionTypeNames, operationNodeId,
        Objects.requireNonNull(exceptionTypeNames, "exceptionTypeNames").stream().map(name -> new Alternative(name, List.of())).toList());
  }

  ExceptionHandler {
    exceptionTypeNames = List.copyOf(Objects.requireNonNull(exceptionTypeNames, "exceptionTypeNames"));
    Objects.requireNonNull(operationNodeId, "operationNodeId");
    if (operationNodeId.isBlank()) {
      throw new IllegalArgumentException("An exception handler requires an operation identity");
    }
    alternatives = List.copyOf(Objects.requireNonNull(alternatives, "alternatives"));
    if (!exceptionTypeNames.equals(alternatives.stream().map(Alternative::qualifiedTypeName).toList())) {
      throw new IllegalArgumentException("Handler hierarchy facts must retain its ordered qualified alternatives");
    }
  }

  boolean mayCatch(ExceptionType thrownType) {
    Objects.requireNonNull(thrownType, "thrownType");
    requireResolved();
    return alternatives.stream().anyMatch(alternative -> alternative.mayCatch(thrownType));
  }

  boolean covers(ExceptionType thrownType) {
    Objects.requireNonNull(thrownType, "thrownType");
    requireResolved();
    return alternatives.stream().anyMatch(alternative -> alternative.covers(thrownType));
  }

  boolean unresolved() {
    return exceptionTypeNames.isEmpty();
  }

  void requireResolved() {
    if (unresolved()) {
      throw new IllegalStateException("An admitted handler requires resolved qualified alternatives");
    }
  }

  record Alternative(String qualifiedTypeName, List<String> assignableToTypeNames) {
    Alternative {
      requireName(qualifiedTypeName);
      assignableToTypeNames = List.copyOf(Objects.requireNonNull(assignableToTypeNames, "assignableToTypeNames"));
      assignableToTypeNames.forEach(Alternative::requireName);
    }

    boolean covers(ExceptionType thrownType) {
      return !thrownType.unknown()
          && (qualifiedTypeName.equals(thrownType.typeName()) || thrownType.assignableToTypeNames().contains(qualifiedTypeName));
    }

    boolean mayCatch(ExceptionType thrownType) {
      if (covers(thrownType) || thrownType.domainKind() != ExceptionDomainKind.EXACT) {
        return true;
      }
      return !assignableToTypeNames.contains(thrownType.typeName());
    }

    boolean shadowedBy(List<Alternative> earlierAlternatives) {
      return earlierAlternatives.stream().anyMatch(
          earlier -> qualifiedTypeName.equals(earlier.qualifiedTypeName()) || assignableToTypeNames.contains(earlier.qualifiedTypeName()));
    }

    private static void requireName(String name) {
      if (Objects.requireNonNull(name, "qualified type").isBlank()) {
        throw new IllegalArgumentException("A handler alternative or ancestor cannot be blank");
      }
    }
  }
}
