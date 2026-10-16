package org.aquilagraph.frontend;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.frontend.JavaProjectionIdentity.Occurrence;

/** Tracks source occurrence instances through staged attachment and final scope publication. */
final class JavaSourceCopyCorrespondence {
  private final Map<Occurrence, SyntaxNode> occurrences = new HashMap<>();
  private final Optional<JavaSourceCopyCorrespondence> parent;

  JavaSourceCopyCorrespondence() {
    parent = Optional.empty();
  }

  private JavaSourceCopyCorrespondence(JavaSourceCopyCorrespondence parent) {
    this.parent = Optional.of(parent);
  }

  JavaSourceCopyCorrespondence child() {
    return new JavaSourceCopyCorrespondence(this);
  }

  void accept() {
    parent.orElseThrow().occurrences.putAll(occurrences);
  }

  private Optional<SyntaxNode> lookup(SyntaxNode node) {
    var value = occurrences.get(new Occurrence(node));
    return value == null ? parent.flatMap(owner -> owner.lookup(node)) : Optional.of(value);
  }

  void retain(SyntaxNode original, SyntaxNode copied) {
    occurrences.put(new Occurrence(original), copied);
  }

  boolean contains(SyntaxNode node) {
    return lookup(node).isPresent();
  }

  SyntaxNode finalCopy(SyntaxNode original) {
    var current = original;
    var next = lookup(current);
    while (next.isPresent() && !JavaProjectionIdentity.sameInstance(current, next.orElseThrow())) {
      current = next.orElseThrow();
      next = lookup(current);
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
