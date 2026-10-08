package org.aquilagraph.source.admission;

import java.util.HashMap;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxRole;

/** Qualifies non-preview pattern placement while retaining diagnosed invalid source as incomplete. */
final class SourcePatternRequirements {
  private SourcePatternRequirements() {
  }

  static boolean supports(SyntaxNode root) {
    var parents = new HashMap<String, SyntaxNode>();
    root.descendantsAndSelf().forEach(node -> node.children().forEach(child -> parents.put(child.node().nodeId(), node)));
    for (var node : root.descendantsAndSelf()) {
      if (node.syntaxKind() == SyntaxKind.ANY_PATTERN) {
        var parent = parents.get(node.nodeId());
        if (parent == null || parent.syntaxKind() != SyntaxKind.DECONSTRUCTION_PATTERN) {
          return false;
        }
      }
      var parent = parents.get(node.nodeId());
      if (node.syntaxKind() == SyntaxKind.BINDING_PATTERN && parent != null && parent.syntaxKind() == SyntaxKind.INSTANCE_OF) {
        var binding = node.requiredChild(SyntaxRole.PARAMETER);
        if (binding.value(SyntaxValue.LocalDeclaration.class).bindingEvidence()
            .filter(evidence -> evidence.typeForm() != LocalBindingEvidence.TypeForm.DECLARED).isPresent()
            || binding.type().knownIdentity().filter(identity -> isPrimitive(identity.category())).isPresent()) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean isPrimitive(JavaTypeCategory category) {
    return switch (category) {
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID -> true;
      default -> false;
    };
  }
}
