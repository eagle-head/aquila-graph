package org.aquilagraph.cfg.internal;

import java.util.Objects;
import java.util.Optional;

record CfgEndpoint(Kind kind, Optional<String> operationNodeId) {
  enum Kind {
    ENTRY, OPERATION, EXIT
  }

  CfgEndpoint {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(operationNodeId, "operationNodeId");
    if ((kind == Kind.OPERATION) != operationNodeId.isPresent()) {
      throw new IllegalArgumentException("Only operation endpoints have a node identifier");
    }
  }

  static CfgEndpoint entry() {
    return new CfgEndpoint(Kind.ENTRY, Optional.empty());
  }

  static CfgEndpoint operation(String operationNodeId) {
    return new CfgEndpoint(Kind.OPERATION, Optional.of(operationNodeId));
  }

  static CfgEndpoint exit() {
    return new CfgEndpoint(Kind.EXIT, Optional.empty());
  }

  String canonicalForm() {
    return kind + operationNodeId.map(value -> ":" + value).orElse("");
  }
}
