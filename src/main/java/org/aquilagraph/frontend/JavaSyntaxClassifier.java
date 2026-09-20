package org.aquilagraph.frontend;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.Objects;
import java.util.stream.Collectors;
import org.aquilagraph.model.SyntaxKind;

final class JavaSyntaxClassifier {
  private final JavaSemanticResolver semantics;

  JavaSyntaxClassifier(JavaSemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
  }

  JavaSyntaxDescription classify(Tree tree, TreePath path) {
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(path, "path");
    var kind = switch (tree.getKind()) {
      case BLOCK -> SyntaxKind.BLOCK;
      case VARIABLE -> SyntaxKind.LOCAL_DECLARATION;
      case EXPRESSION_STATEMENT -> SyntaxKind.EXPRESSION_STATEMENT;
      case RETURN -> SyntaxKind.RETURN;
      case THROW -> SyntaxKind.THROW;
      case IF -> SyntaxKind.IF;
      case TRY -> SyntaxKind.TRY;
      case CATCH -> SyntaxKind.CATCH;
      case SWITCH_EXPRESSION -> SyntaxKind.SWITCH_EXPRESSION;
      case CASE -> SyntaxKind.CASE;
      case YIELD -> SyntaxKind.YIELD;
      case METHOD_INVOCATION -> SyntaxKind.INVOCATION;
      case NEW_CLASS -> SyntaxKind.OBJECT_CREATION;
      case MEMBER_SELECT -> semantics.isArrayLength((MemberSelectTree) tree, path) ? SyntaxKind.ARRAY_LENGTH : SyntaxKind.MEMBER_ACCESS;
      case IDENTIFIER -> SyntaxKind.VARIABLE_ACCESS;
      case BOOLEAN_LITERAL, CHAR_LITERAL, DOUBLE_LITERAL, FLOAT_LITERAL, INT_LITERAL, LONG_LITERAL, NULL_LITERAL, STRING_LITERAL ->
        SyntaxKind.LITERAL;
      case UNARY_PLUS, UNARY_MINUS, BITWISE_COMPLEMENT, LOGICAL_COMPLEMENT -> SyntaxKind.UNARY;
      case CONDITIONAL_AND -> SyntaxKind.CONDITIONAL_AND;
      case CONDITIONAL_OR -> SyntaxKind.CONDITIONAL_OR;
      case MULTIPLY, DIVIDE, REMAINDER, PLUS, MINUS, LEFT_SHIFT, RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT, LESS_THAN, GREATER_THAN,
          LESS_THAN_EQUAL, GREATER_THAN_EQUAL, EQUAL_TO, NOT_EQUAL_TO, AND, XOR, OR ->
        SyntaxKind.BINARY;
      case ASSIGNMENT -> SyntaxKind.ASSIGNMENT;
      case ARRAY_ACCESS -> SyntaxKind.ARRAY_ACCESS;
      case PARENTHESIZED -> SyntaxKind.PARENTHESIZED;
      case TYPE_CAST -> SyntaxKind.TYPE_CAST;
      case CONDITIONAL_EXPRESSION -> SyntaxKind.CONDITIONAL_EXPRESSION;
      case EMPTY_STATEMENT -> SyntaxKind.EMPTY;
      default -> SyntaxKind.UNSUPPORTED;
    };
    return new JavaSyntaxDescription(kind, detail(tree));
  }

  private static String detail(Tree tree) {
    return switch (tree) {
      case VariableTree variable -> variable.getType() + " " + variable.getName();
      case CatchTree catchTree -> catchTree.getParameter().getType() + " " + catchTree.getParameter().getName();
      case CaseTree caseTree -> caseTree.getLabels().stream().map(Object::toString).collect(Collectors.joining(","));
      case MemberSelectTree selection -> selection.getIdentifier().toString();
      case IdentifierTree identifier -> identifier.getName().toString();
      case LiteralTree literal -> String.valueOf(literal.getValue());
      case UnaryTree unary -> unary.getKind().name();
      case BinaryTree binary -> binary.getKind().name();
      case TypeCastTree cast -> cast.getType().toString();
      default -> tree.getKind().name();
    };
  }
}
