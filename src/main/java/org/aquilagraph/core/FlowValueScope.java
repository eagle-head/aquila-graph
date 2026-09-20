package org.aquilagraph.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.aquilagraph.model.FlowValue;

final class FlowValueScope {
  private final Deque<Map<String, FlowValue>> scopes = new ArrayDeque<>();

  FlowValueScope(List<FlowValue> parameters) {
    Objects.requireNonNull(parameters, "parameters");
    var methodScope = new HashMap<String, FlowValue>();
    for (var parameter : parameters) {
      methodScope.put(parameter.displayName().orElseThrow(), parameter);
    }
    scopes.push(methodScope);
  }

  Optional<FlowValue> resolve(String name) {
    Objects.requireNonNull(name, "name");
    for (var scope : scopes) {
      var value = scope.get(name);
      if (value != null) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }

  void bind(String name, FlowValue value) {
    scopes.getFirst().put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
  }

  <T> T inChildScope(Supplier<T> action) {
    Objects.requireNonNull(action, "action");
    scopes.push(new HashMap<>());
    try {
      return action.get();
    } finally {
      scopes.pop();
    }
  }
}
