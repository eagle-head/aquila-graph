package org.aquilagraph.source.admission;

import java.util.function.Predicate;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeCategory;

/** Source switch qualification and the retained bounded case contract. */
final class SourceSwitchRequirements {
  private SourceSwitchRequirements() {
  }

  static boolean isBoundedCase(SyntaxNode node) {
    return isBoundedCase(node, label -> label.syntaxKind() == SyntaxKind.LITERAL);
  }

  static boolean isBoundedCase(SyntaxNode node, JavaAstAggregate source) {
    return isBoundedCase(node, label -> source.executableLiteralText(label.nodeId()).isPresent());
  }

  private static boolean isBoundedCase(SyntaxNode node, Predicate<SyntaxNode> literal) {
    var value = node.value(SyntaxValue.SwitchCase.class);
    return value.form() == SyntaxValue.SwitchCaseForm.RULE && node.children(SyntaxRole.GUARD).isEmpty()
        && value.labels().stream().noneMatch(SyntaxValue.SwitchLabel.Pattern.class::isInstance)
        && (value.labels().size() == 1 || value.labels().stream().noneMatch(SyntaxValue.SwitchLabel.Default.class::isInstance))
        && node.children(SyntaxRole.LABEL).stream()
            .allMatch(label -> literal.test(label)
                ? label.sourceKind() != JavaSourceKind.NULL_LITERAL
                : label.semanticReference().filter(reference -> reference.referenceKind() == ReferenceKind.ENUM_CONSTANT)
                    .flatMap(reference -> reference.symbolKey()).isPresent());
  }

  static boolean supportsBody(SyntaxNode node, boolean enclosingExpression) {
    var expression = node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION;
    var selection = expression || node.syntaxKind() == SyntaxKind.SWITCH_STATEMENT;
    if (selection) {
      var cases = node.children(SyntaxRole.CASE);
      if ((expression && cases.isEmpty())
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
    if (node.syntaxKind() == SyntaxKind.TYPE_DECLARATION) {
      return true;
    }
    var selectedExpression = node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION ? false : expression || enclosingExpression;
    return node.children().stream().allMatch(child -> supportsBody(child.node(), selectedExpression));
  }
}
