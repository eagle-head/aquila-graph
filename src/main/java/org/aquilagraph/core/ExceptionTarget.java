package org.aquilagraph.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

record ExceptionTarget(List<ExceptionHandler> handlers, Optional<ExceptionTarget> outer) {
  static final String UNKNOWN_INVOCATION_EXCEPTION = "unchecked-invocation";
  static final String UNKNOWN_CONSTRUCTOR_EXCEPTION = "unchecked-constructor";

  ExceptionTarget {
    handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers"));
    Objects.requireNonNull(outer, "outer");
  }

  static ExceptionTarget methodExit() {
    return new ExceptionTarget(List.of(), Optional.empty());
  }

  ExceptionTarget nested(List<ExceptionHandler> nestedHandlers) {
    return new ExceptionTarget(nestedHandlers, Optional.of(this));
  }

  ExceptionRoute route(ExceptionType exceptionType) {
    Objects.requireNonNull(exceptionType, "exceptionType");
    if (exceptionType.unknown()) {
      return unknownRoute();
    }
    var unresolvedHandlerNodeIds = new ArrayList<String>();
    for (var handler : handlers) {
      if (handler.unresolved()) {
        unresolvedHandlerNodeIds.add(handler.operationNodeId());
        continue;
      }
      if (handler.catches(exceptionType)) {
        unresolvedHandlerNodeIds.add(handler.operationNodeId());
        return new ExceptionRoute(unresolvedHandlerNodeIds, false);
      }
    }
    var outerRoute = outer.map(target -> target.route(exceptionType)).orElseGet(ExceptionRoute::methodExit);
    unresolvedHandlerNodeIds.addAll(outerRoute.handlerOperationNodeIds());
    return new ExceptionRoute(unresolvedHandlerNodeIds, outerRoute.exitsMethod());
  }

  private ExceptionRoute unknownRoute() {
    var handlerNodeIds = new ArrayList<String>();
    ExceptionTarget target = this;
    while (true) {
      target.handlers().stream().map(ExceptionHandler::operationNodeId).forEach(handlerNodeIds::add);
      if (target.outer().isEmpty()) {
        return new ExceptionRoute(handlerNodeIds, true);
      }
      target = target.outer().orElseThrow();
    }
  }

}
