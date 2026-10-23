package org.aquilagraph.cfg.internal;

import java.util.List;
import java.util.Objects;

record ExceptionRoute(List<String> handlerOperationNodeIds, boolean exitsMethod) {
  ExceptionRoute {
    handlerOperationNodeIds = List.copyOf(Objects.requireNonNull(handlerOperationNodeIds, "handlerOperationNodeIds"));
  }

  static ExceptionRoute methodExit() {
    return new ExceptionRoute(List.of(), true);
  }
}
