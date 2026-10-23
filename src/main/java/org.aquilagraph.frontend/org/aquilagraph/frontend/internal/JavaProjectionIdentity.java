package org.aquilagraph.frontend.internal;

import java.util.IdentityHashMap;
import org.jspecify.annotations.Nullable;
import org.aquilagraph.source.api.SyntaxNode;

/** Compares compiler and projection carriers by reference identity. */
final class JavaProjectionIdentity {
  private JavaProjectionIdentity() {
  }

  static boolean sameInstance(@Nullable Object first, @Nullable Object second) {
    var identity = new IdentityHashMap<@Nullable Object, Boolean>();
    identity.put(first, Boolean.TRUE);
    return identity.containsKey(second);
  }

  /** Selects one in-memory source carrier without changing its durable value identity. */
  record Occurrence(SyntaxNode node) {
    @Override
    public boolean equals(@Nullable Object other) {
      return other instanceof Occurrence occurrence && sameInstance(node, occurrence.node);
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(node);
    }
  }

}
