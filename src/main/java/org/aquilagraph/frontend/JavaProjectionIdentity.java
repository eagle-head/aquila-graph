package org.aquilagraph.frontend;

import java.util.IdentityHashMap;
import org.jspecify.annotations.Nullable;

/** Compares compiler and projection carriers by reference identity. */
final class JavaProjectionIdentity {
  private JavaProjectionIdentity() {
  }

  static boolean sameInstance(@Nullable Object first, @Nullable Object second) {
    var identity = new IdentityHashMap<@Nullable Object, Boolean>();
    identity.put(first, Boolean.TRUE);
    return identity.containsKey(second);
  }
}
