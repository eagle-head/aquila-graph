package org.aquilagraph.source.internal;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.aquilagraph.source.api.SyntaxNode;
import org.jspecify.annotations.Nullable;

/** Immutable links between exact in-memory occurrences, including equal detached copies. */
final class OccurrenceIndex<V> {
  private final Map<Occurrence, V> entries;

  private OccurrenceIndex(Map<Occurrence, V> entries) {
    this.entries = Map.copyOf(entries);
  }

  @Nullable
  V get(SyntaxNode occurrence) {
    return entries.get(new Occurrence(occurrence));
  }

  Builder<V> extend() {
    return new Builder<>(entries);
  }

  static boolean same(SyntaxNode occurrence, @Nullable SyntaxNode other) {
    if (other == null) {
      return false;
    }
    var carried = new IdentityHashMap<SyntaxNode, Boolean>(1);
    carried.put(occurrence, Boolean.TRUE);
    return carried.containsKey(other);
  }

  static final class Builder<V> {
    private final Map<Occurrence, V> entries;

    Builder() {
      this(Map.of());
    }

    private Builder(Map<Occurrence, V> entries) {
      this.entries = new HashMap<>(entries);
    }

    @Nullable
    V putIfAbsent(SyntaxNode occurrence, V value) {
      return entries.putIfAbsent(new Occurrence(occurrence), value);
    }

    OccurrenceIndex<V> freeze() {
      return new OccurrenceIndex<>(entries);
    }
  }

  private record Occurrence(SyntaxNode node) {
    @Override
    public boolean equals(@Nullable Object other) {
      return other instanceof Occurrence occurrence && same(node, occurrence.node);
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(node);
    }
  }
}
