package org.aquilagraph.frontend.internal;

import java.util.HashMap;
import java.util.Map;
import org.aquilagraph.identity.api.SourceSpan;
import org.jspecify.annotations.Nullable;

/** Retains the canonical annotation occurrence shared by one written variable header. */
final class JavaSharedAnnotations {
  private final Map<SourceSpan, String> owners = new HashMap<>();

  JavaSharedAnnotations copy() {
    var copy = new JavaSharedAnnotations();
    copy.owners.putAll(owners);
    return copy;
  }

  boolean contains(SourceSpan range) {
    return owners.containsKey(range);
  }

  @Nullable
  String find(SourceSpan range) {
    return owners.get(range);
  }

  void retain(SourceSpan range, String nodeKey) {
    owners.put(range, nodeKey);
  }
}
