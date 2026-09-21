package org.aquilagraph.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.aquilagraph.model.FlowValue;

final class FlowValueScope {
  private final Deque<Map<String, FlowValue>> scopes = new ArrayDeque<>();

  FlowValueScope() {
    scopes.push(new HashMap<>());
  }

  Optional<FlowValue> resolve(String declarationKey) {
    Objects.requireNonNull(declarationKey, "declarationKey");
    for (var scope : scopes) {
      var value = scope.get(declarationKey);
      if (value != null) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }

  void bind(String declarationKey, FlowValue value) {
    scopes.getFirst().put(Objects.requireNonNull(declarationKey, "declarationKey"), Objects.requireNonNull(value, "value"));
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
