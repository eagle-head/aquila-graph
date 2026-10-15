package org.aquilagraph.frontend;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.aquilagraph.source.api.SyntaxNode;

/** Tracks source occurrence instances through staged attachment and final scope publication. */
final class JavaSourceCopyCorrespondence {
  private final Function<SyntaxNode, Optional<SyntaxNode>> lookup;
  private final BiConsumer<SyntaxNode, SyntaxNode> retention;

  JavaSourceCopyCorrespondence() {
    var occurrences = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    lookup = node -> Optional.ofNullable(occurrences.get(node));
    retention = occurrences::put;
  }

  void retain(SyntaxNode original, SyntaxNode copied) {
    retention.accept(original, copied);
  }

  boolean contains(SyntaxNode node) {
    return lookup.apply(node).isPresent();
  }

  SyntaxNode finalCopy(SyntaxNode original) {
    var current = original;
    var next = lookup.apply(current);
    while (next.isPresent() && !JavaProjectionIdentity.sameInstance(current, next.orElseThrow())) {
      current = next.orElseThrow();
      next = lookup.apply(current);
    }
    return current;
  }

  Map<String, SyntaxNode> scopeCopies(SyntaxNode root, boolean retainedOnly) {
    var result = new HashMap<String, SyntaxNode>();
    root.descendantsAndSelf().stream().filter(node -> !retainedOnly || contains(node))
        .forEach(node -> result.put(node.nodeId(), finalCopy(node)));
    return result;
  }
}
