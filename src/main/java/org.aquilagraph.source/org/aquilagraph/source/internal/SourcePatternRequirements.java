package org.aquilagraph.source.internal;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SourceExecutionTraversal;

/** Qualifies non-preview pattern placement while retaining diagnosed invalid source as incomplete. */
public final class SourcePatternRequirements {
  private SourcePatternRequirements() {
  }

  /**
   * Returns every independently unsupported pattern placement.
   *
   * @param root
   *          admitted execution root
   * @return offending nodes in execution occurrence order
   */
  public static List<SyntaxNode> unsupportedNodes(SyntaxNode root) {
    var gaps = new ArrayList<SyntaxNode>();
    var parents = new HashMap<String, SyntaxNode>();
    var nodes = SourceExecutionTraversal.nodes(root);
    nodes.forEach(node -> node.children().forEach(child -> parents.put(child.node().nodeId(), node)));
    for (var node : nodes) {
      if (node.syntaxKind() == SyntaxKind.ANY_PATTERN) {
        var parent = parents.get(node.nodeId());
        if (parent == null || parent.syntaxKind() != SyntaxKind.DECONSTRUCTION_PATTERN) {
          gaps.add(node);
        }
      }
      var parent = parents.get(node.nodeId());
      if (node.syntaxKind() == SyntaxKind.BINDING_PATTERN && parent != null
          && (parent.syntaxKind() == SyntaxKind.INSTANCE_OF || parent.syntaxKind() == SyntaxKind.CASE)) {
        var binding = node.requiredChild(SyntaxRole.PARAMETER);
        if (binding.value(SyntaxValue.LocalDeclaration.class).bindingEvidence()
            .filter(evidence -> evidence.typeForm() != LocalBindingEvidence.TypeForm.DECLARED).isPresent()
            || binding.type().knownIdentity().filter(identity -> isPrimitive(identity.category())).isPresent()) {
          gaps.add(node);
        }
      }
    }
    return List.copyOf(gaps);
  }

  private static boolean isPrimitive(JavaTypeCategory category) {
    return switch (category) {
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID -> true;
      default -> false;
    };
  }
}
