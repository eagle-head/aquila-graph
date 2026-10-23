package org.aquilagraph.cfg.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import org.aquilagraph.cfg.internal.ExceptionHandler.Alternative;

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
    var orderedHandlers = orderedHandlers();
    orderedHandlers.forEach(ExceptionHandler::requireResolved);
    if (exceptionType.mayBeNull()) {
      return nullableRoute(exceptionType, orderedHandlers);
    }
    if (exceptionType.unknown()) {
      return unknownRoute();
    }
    var selectedHandlers = new ArrayList<String>();
    var earlierAlternatives = new ArrayList<Alternative>();
    for (var handler : orderedHandlers) {
      var residualAlternatives = handler.alternatives().stream().filter(alternative -> !alternative.shadowedBy(earlierAlternatives))
          .toList();
      if (residualAlternatives.stream().anyMatch(alternative -> alternative.mayCatch(exceptionType))) {
        selectedHandlers.add(handler.operationNodeId());
      }
      if (residualAlternatives.stream().anyMatch(alternative -> alternative.covers(exceptionType))) {
        return new ExceptionRoute(selectedHandlers, false);
      }
      earlierAlternatives.addAll(handler.alternatives());
    }
    return new ExceptionRoute(selectedHandlers, true);
  }

  private ExceptionRoute nullableRoute(ExceptionType exceptionType, List<ExceptionHandler> orderedHandlers) {
    var routes = exceptionType.outcomes().stream().map(this::route).toList();
    var selected = new HashSet<String>();
    routes.forEach(route -> selected.addAll(route.handlerOperationNodeIds()));
    var handlerIds = orderedHandlers.stream().map(ExceptionHandler::operationNodeId).filter(selected::contains).distinct().toList();
    return new ExceptionRoute(handlerIds, routes.stream().anyMatch(ExceptionRoute::exitsMethod));
  }

  private List<ExceptionHandler> orderedHandlers() {
    var ordered = new ArrayList<ExceptionHandler>();
    ExceptionTarget target = this;
    while (true) {
      ordered.addAll(target.handlers());
      if (target.outer().isEmpty()) {
        return List.copyOf(ordered);
      }
      target = target.outer().orElseThrow();
    }
  }

  private ExceptionRoute unknownRoute() {
    return new ExceptionRoute(orderedHandlers().stream().map(ExceptionHandler::operationNodeId).toList(), true);
  }

}
