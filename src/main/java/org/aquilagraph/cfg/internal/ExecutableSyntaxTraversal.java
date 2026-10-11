package org.aquilagraph.cfg.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.JavaSourceKind;

/** Consumer traversal of executable child roles while retaining source-only AST attachments. */
final class ExecutableSyntaxTraversal {
  private ExecutableSyntaxTraversal() {
  }

  static List<SyntaxNode> nodes(SyntaxNode root) {
    Objects.requireNonNull(root, "root");
    var result = new ArrayList<SyntaxNode>();
    var pending = new ArrayDeque<SyntaxNode>();
    pending.push(root);
    while (!pending.isEmpty()) {
      var node = pending.pop();
      result.add(node);
      var children = orderedChildren(node);
      for (var index = children.size() - 1; index >= 0; index--) {
        var child = children.get(index);
        if (executable(node, child.role())) {
          pending.push(child.node());
        }
      }
    }
    return List.copyOf(result);
  }

  static List<SyntaxChild> orderedChildren(SyntaxNode node) {
    return node.children().stream()
        .sorted(Comparator.comparingLong((SyntaxChild child) -> child.node().origin().map(SourceSpan::startOffset).orElse(Long.MAX_VALUE))
            .thenComparing(child -> child.role().stableId()).thenComparingInt(SyntaxChild::position))
        .toList();
  }

  private static boolean executable(SyntaxNode parent, SyntaxRole role) {
    if (parent.sourceKind() == JavaSourceKind.LAMBDA_EXPRESSION || parent.syntaxKind() == SyntaxKind.TYPE_DECLARATION) {
      return false;
    }
    if (role == SyntaxRole.QUALIFIER) {
      return parent.syntaxKind() == SyntaxKind.ARRAY_LENGTH;
    }
    return switch (role) {
      case STATEMENT, INITIALIZER, EXPRESSION, VALUE, CONDITION, THEN_BRANCH, ELSE_BRANCH, RESOURCE, TRY_BLOCK, CATCH_CLAUSE, FINALLY_BLOCK,
          PARAMETER, BODY, SELECTOR, CASE, LABEL, GUARD, LOCATION, RECEIVER, ARGUMENT, ENCLOSING_INSTANCE, QUALIFIER, OPERAND, LEFT_OPERAND,
          RIGHT_OPERAND, ARRAY, INDEX, TRUE_EXPRESSION, FALSE_EXPRESSION ->
        true;
      default -> false;
    };
  }
}
