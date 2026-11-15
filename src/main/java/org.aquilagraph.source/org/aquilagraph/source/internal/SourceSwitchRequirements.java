package org.aquilagraph.source.internal;

import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeCategory;

/** Source switch qualification and the retained bounded case contract. */
public final class SourceSwitchRequirements {
  private SourceSwitchRequirements() {
  }

  /**
   * Reports whether the case belongs to the bounded switch contract.
   *
   * @param node
   *          node
   * @param source
   *          source
   * @return whether the facts satisfy the required relation
   */
  public static boolean isBoundedCase(SyntaxNode node, JavaAstAggregate source) {
    var value = node.value(SyntaxValue.SwitchCase.class);
    return value.form() == SyntaxValue.SwitchCaseForm.RULE && node.children(SyntaxRole.GUARD).isEmpty()
        && value.labels().stream().noneMatch(SyntaxValue.SwitchLabel.Pattern.class::isInstance)
        && (value.labels().size() == 1 || value.labels().stream().noneMatch(SyntaxValue.SwitchLabel.Default.class::isInstance))
        && node.children(SyntaxRole.LABEL).stream()
            .allMatch(label -> source.executableLiteralText(label.nodeId()).isPresent()
                ? label.sourceKind() != JavaSourceKind.NULL_LITERAL
                : label.semanticReference().filter(reference -> reference.referenceKind() == ReferenceKind.ENUM_CONSTANT)
                    .flatMap(reference -> reference.symbolKey()).isPresent());
  }

  /**
   * Returns all unsupported switch contexts without short-circuiting descendants.
   *
   * @param node
   *          admitted execution root
   * @param enclosingExpression
   *          whether the root is within a switch expression
   * @return offending nodes in source traversal order
   */
  public static List<SyntaxNode> unsupportedNodes(SyntaxNode node, boolean enclosingExpression) {
    var gaps = new ArrayList<SyntaxNode>();
    collectUnsupported(node, enclosingExpression, gaps);
    return List.copyOf(gaps);
  }

  private static void collectUnsupported(SyntaxNode node, boolean enclosingExpression, List<SyntaxNode> gaps) {
    if (!supportsNode(node, enclosingExpression)) {
      gaps.add(node);
    }
    if (node.syntaxKind() == SyntaxKind.TYPE_DECLARATION) {
      return;
    }
    var expression = node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION;
    var selectedExpression = node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION ? false : expression || enclosingExpression;
    node.children().forEach(child -> collectUnsupported(child.node(), selectedExpression, gaps));
  }

  private static boolean supportsNode(SyntaxNode node, boolean enclosingExpression) {
    var expression = node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION;
    var selection = expression || node.syntaxKind() == SyntaxKind.SWITCH_STATEMENT;
    if (selection) {
      var cases = node.children(SyntaxRole.CASE);
      if (cases.stream().anyMatch(value -> !(value.value() instanceof SyntaxValue.SwitchCase)) || (expression && cases.isEmpty())
          || cases.stream().map(value -> value.value(SyntaxValue.SwitchCase.class).form()).distinct().count() > 1) {
        return false;
      }
      if (expression && cases.stream().flatMap(value -> value.children(SyntaxRole.BODY).stream())
          .anyMatch(body -> body.syntaxKind() == SyntaxKind.EXPRESSION_STATEMENT)) {
        return false;
      }
    }
    if (node.syntaxKind() == SyntaxKind.YIELD && !enclosingExpression) {
      return false;
    }
    if (node.syntaxKind() == SyntaxKind.CASE) {
      if (node.children(SyntaxRole.GUARD).stream().anyMatch(guard -> guard.resolvedType()
          .filter(type -> type.category() == JavaTypeCategory.BOOLEAN || "java.lang.Boolean".equals(type.erasedName())).isEmpty())) {
        return false;
      }
      var labels = node.children(SyntaxRole.LABEL);
      if (labels.size() > 1 && labels.stream().anyMatch(label -> label.sourceKind() == JavaSourceKind.NULL_LITERAL)) {
        return false;
      }
      if (labels.size() > 1 && node.value(SyntaxValue.SwitchCase.class).labels().getFirst() instanceof SyntaxValue.SwitchLabel.Pattern
          && labels.stream().flatMap(label -> label.descendantsAndSelf().stream())
              .anyMatch(label -> label.value() instanceof SyntaxValue.LocalDeclaration local && !"_".equals(local.name()))) {
        return false;
      }
    }
    return true;
  }
}
