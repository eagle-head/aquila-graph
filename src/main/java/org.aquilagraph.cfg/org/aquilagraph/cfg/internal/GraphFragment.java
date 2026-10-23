package org.aquilagraph.cfg.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

record GraphFragment(Optional<String> entry, List<String> normalExits, Optional<DraftValue> value, List<YieldExit> yieldExits) {
  GraphFragment {
    Objects.requireNonNull(entry, "entry");
    normalExits = List.copyOf(Objects.requireNonNull(normalExits, "normalExits"));
    Objects.requireNonNull(value, "value");
    yieldExits = List.copyOf(Objects.requireNonNull(yieldExits, "yieldExits"));
  }

  GraphFragment(Optional<String> entry, List<String> normalExits, Optional<DraftValue> value) {
    this(entry, normalExits, value, List.of());
  }

  static GraphFragment empty() {
    return new GraphFragment(Optional.empty(), List.of(), Optional.empty());
  }

  static GraphFragment value(String operationNodeId, DraftValue value) {
    return new GraphFragment(Optional.of(operationNodeId), List.of(operationNodeId), Optional.of(value));
  }

  GraphFragment withoutValue() {
    return new GraphFragment(entry, normalExits, Optional.empty(), yieldExits);
  }
}
