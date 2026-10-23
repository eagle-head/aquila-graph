package org.aquilagraph.source.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Source occurrences belonging to one execution scope, including its deferred lambdas. */
public final class SourceExecutionTraversal {
  private SourceExecutionTraversal() {
  }

  /**
   * Returns source occurrences without entering a nested type's members.
   *
   * @param root
   *          scope root
   * @return immutable occurrences in containment order
   */
  public static List<SyntaxNode> nodes(SyntaxNode root) {
    Objects.requireNonNull(root, "root");
    var result = new ArrayList<SyntaxNode>();
    var pending = new ArrayDeque<SyntaxNode>();
    pending.push(root);
    while (!pending.isEmpty()) {
      var node = pending.pop();
      result.add(node);
      if (node.syntaxKind() == SyntaxKind.TYPE_DECLARATION) {
        continue;
      }
      var children = node.children();
      for (var index = children.size() - 1; index >= 0; index--) {
        pending.push(children.get(index).node());
      }
    }
    return List.copyOf(result);
  }
}
