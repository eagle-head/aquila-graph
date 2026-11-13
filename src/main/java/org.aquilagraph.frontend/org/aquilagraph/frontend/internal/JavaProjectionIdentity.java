package org.aquilagraph.frontend.internal;

import com.sun.source.util.TreePath;
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

  static boolean sameTreePath(@Nullable TreePath first, @Nullable TreePath second) {
    var left = first;
    var right = second;
    while (left != null && right != null) {
      if (!sameInstance(left.getLeaf(), right.getLeaf())) {
        return false;
      }
      left = left.getParentPath();
      right = right.getParentPath();
    }
    return left == null && right == null;
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
