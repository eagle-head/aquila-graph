package org.aquilagraph.frontend;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/** Evaluates only attributed primitive and String constant-expression forms. */
final class JavaConstantExpression {
  private final SemanticResolver semantics;

  JavaConstantExpression(SemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
  }

  Optional<Object> value(TreePath path) {
    var type = semantics.attributedMirror(path);
    if (type.isEmpty() || !constantType(type.orElseThrow())) {
      return Optional.empty();
    }
    return switch (path.getLeaf()) {
      case LiteralTree literal -> Optional.ofNullable(literal.getValue());
      case ParenthesizedTree parenthesized -> value(new TreePath(path, parenthesized.getExpression()));
      case TypeCastTree cast ->
        value(new TreePath(path, cast.getExpression())).flatMap(operand -> converted(operand, type.orElseThrow().getKind()));
      case UnaryTree unary ->
        value(new TreePath(path, unary.getExpression())).flatMap(operand -> unary(unary.getKind(), operand, type.orElseThrow().getKind()));
      case BinaryTree binary -> binary(path, binary, type.orElseThrow().getKind());
      case ConditionalExpressionTree conditional -> conditional(path, conditional, type.orElseThrow().getKind());
      default -> switch (semantics.element(path)) {
        case VariableElement variable ->
          constantVariableName(path).equals(Optional.of(true)) ? Optional.ofNullable(variable.getConstantValue()) : Optional.empty();
        case null, default -> Optional.empty();
      };
    };
  }

  Optional<Boolean> constantStatus(TreePath path) {
    var type = semantics.attributedMirror(path);
    if (type.isEmpty() || switch (type.orElseThrow().getKind()) {
      case ERROR, VOID, NONE, PACKAGE, MODULE, EXECUTABLE, OTHER, WILDCARD -> true;
      default -> false;
    } || path.getLeaf() instanceof ErroneousTree || path.getLeaf().getKind() == Tree.Kind.OTHER) {
      return Optional.empty();
    }
    if (!constantType(type.orElseThrow())) {
      return Optional.of(false);
    }
    if (value(path).isPresent()) {
      return Optional.of(true);
    }
    return switch (path.getLeaf()) {
      case LiteralTree ignored -> Optional.of(false);
      case ParenthesizedTree parentheses -> constantStatus(new TreePath(path, parentheses.getExpression()));
      case TypeCastTree cast -> failedEvaluationStatus(path, List.of(cast.getExpression()));
      case UnaryTree unary -> switch (unary.getKind()) {
        case UNARY_PLUS, UNARY_MINUS, BITWISE_COMPLEMENT, LOGICAL_COMPLEMENT ->
          failedEvaluationStatus(path, List.of(unary.getExpression()));
        default -> Optional.of(false);
      };
      case BinaryTree binary -> failedBinaryStatus(path, binary, type.orElseThrow().getKind());
      case ConditionalExpressionTree conditional -> failedEvaluationStatus(path,
          List.of(conditional.getCondition(), conditional.getTrueExpression(), conditional.getFalseExpression()));
      default -> switch (semantics.element(path)) {
        case VariableElement variable -> variable.getConstantValue() == null ? Optional.of(false) : constantVariableName(path);
        case null ->
          path.getLeaf() instanceof IdentifierTree || path.getLeaf() instanceof MemberSelectTree ? Optional.empty() : Optional.of(false);
        default -> Optional.of(false);
      };
    };
  }

  private boolean constantType(TypeMirror type) {
    return type.getKind().isPrimitive()
        || (type.getKind() == TypeKind.DECLARED && "java.lang.String".equals(semantics.typeIdentity(type).erasedName()));
  }

  private static boolean qualifiedName(Tree tree) {
    return switch (tree) {
      case IdentifierTree identifier -> SourceVersion.isName(identifier.getName());
      case MemberSelectTree selection -> SourceVersion.isName(selection.getIdentifier()) && qualifiedName(selection.getExpression());
      default -> false;
    };
  }

  private Optional<Boolean> constantVariableName(TreePath path) {
    if (!qualifiedName(path.getLeaf())) {
      return Optional.of(false);
    }
    if (path.getLeaf() instanceof MemberSelectTree selection) {
      var qualifier = semantics.element(new TreePath(path, selection.getExpression()));
      return qualifier == null ? Optional.empty() : Optional.of(qualifier instanceof TypeElement);
    }
    return Optional.of(true);
  }

  private Optional<Boolean> failedEvaluationStatus(TreePath path, List<? extends Tree> operands) {
    return operands.stream().map(operand -> constantStatus(new TreePath(path, operand)))
        .anyMatch(status -> status.equals(Optional.of(false))) ? Optional.of(false) : Optional.empty();
  }

  private Optional<Boolean> failedBinaryStatus(TreePath path, BinaryTree binary, TypeKind resultKind) {
    var operands = List.of(binary.getLeftOperand(), binary.getRightOperand());
    var status = failedEvaluationStatus(path, operands);
    if (status.isPresent()) {
      return status;
    }
    if ((binary.getKind() == Tree.Kind.DIVIDE || binary.getKind() == Tree.Kind.REMAINDER)
        && (resultKind == TypeKind.INT || resultKind == TypeKind.LONG)
        && operands.stream().allMatch(operand -> constantStatus(new TreePath(path, operand)).equals(Optional.of(true)))) {
      var right = value(new TreePath(path, binary.getRightOperand())).map(JavaConstantExpression::number);
      if (right.isPresent() && right.orElseThrow().longValue() == 0) {
        return Optional.of(false);
      }
    }
    return Optional.empty();
  }

  private Optional<Object> conditional(TreePath path, ConditionalExpressionTree conditional, TypeKind resultKind) {
    var condition = value(new TreePath(path, conditional.getCondition()));
    var whenTrue = value(new TreePath(path, conditional.getTrueExpression()));
    var whenFalse = value(new TreePath(path, conditional.getFalseExpression()));
    if (condition.orElse(null) instanceof Boolean selected && whenTrue.isPresent() && whenFalse.isPresent()) {
      return converted((selected ? whenTrue : whenFalse).orElseThrow(), resultKind);
    }
    return Optional.empty();
  }

  private Optional<Object> binary(TreePath path, BinaryTree binary, TypeKind resultKind) {
    var left = value(new TreePath(path, binary.getLeftOperand()));
    var right = value(new TreePath(path, binary.getRightOperand()));
    if (left.isEmpty() || right.isEmpty()) {
      return Optional.empty();
    }
    var first = left.orElseThrow();
    var second = right.orElseThrow();
    if (binary.getKind() == Tree.Kind.PLUS && (first instanceof String || second instanceof String)) {
      return Optional.of(String.valueOf(first) + second);
    }
    if (first instanceof Boolean firstBoolean && second instanceof Boolean secondBoolean) {
      return Optional.ofNullable(switch (binary.getKind()) {
        case CONDITIONAL_AND, AND -> firstBoolean && secondBoolean;
        case CONDITIONAL_OR, OR -> firstBoolean || secondBoolean;
        case XOR -> firstBoolean ^ secondBoolean;
        case EQUAL_TO -> firstBoolean.booleanValue() == secondBoolean.booleanValue();
        case NOT_EQUAL_TO -> firstBoolean.booleanValue() != secondBoolean.booleanValue();
        default -> null;
      });
    }
    if (first instanceof String firstString && second instanceof String secondString) {
      return switch (binary.getKind()) {
        case EQUAL_TO -> Optional.of(firstString.equals(secondString));
        case NOT_EQUAL_TO -> Optional.of(!firstString.equals(secondString));
        default -> Optional.empty();
      };
    }
    var firstNumber = number(first);
    var secondNumber = number(second);
    if (firstNumber == null || secondNumber == null) {
      return Optional.empty();
    }
    try {
      if (resultKind == TypeKind.BOOLEAN) {
        return comparison(binary.getKind(), firstNumber, secondNumber);
      }
      return Optional.ofNullable(arithmetic(binary.getKind(), firstNumber, secondNumber, resultKind));
    } catch (ArithmeticException abruptConstant) {
      return Optional.empty();
    }
  }

  private static Optional<Object> unary(Tree.Kind operator, Object operand, TypeKind resultKind) {
    if (operator == Tree.Kind.LOGICAL_COMPLEMENT && operand instanceof Boolean booleanValue) {
      return Optional.of(!booleanValue);
    }
    var numeric = number(operand);
    if (numeric == null) {
      return Optional.empty();
    }
    if (operator == Tree.Kind.UNARY_PLUS) {
      return converted(numeric, resultKind);
    }
    if (operator == Tree.Kind.UNARY_MINUS) {
      return Optional.of(switch (resultKind) {
        case DOUBLE -> Double.valueOf(-numeric.doubleValue());
        case FLOAT -> Float.valueOf(-numeric.floatValue());
        case LONG -> Long.valueOf(-numeric.longValue());
        default -> Integer.valueOf(-numeric.intValue());
      });
    }
    if (operator == Tree.Kind.BITWISE_COMPLEMENT) {
      return resultKind == TypeKind.LONG ? Optional.of(~numeric.longValue()) : Optional.of(~numeric.intValue());
    }
    return Optional.empty();
  }

  private static Optional<Object> converted(Object value, TypeKind resultKind) {
    if (value instanceof String || value instanceof Boolean) {
      return Optional.of(value);
    }
    var numeric = number(value);
    if (numeric == null) {
      return Optional.empty();
    }
    return Optional.of(switch (resultKind) {
      case BYTE -> Byte.valueOf(numeric.byteValue());
      case SHORT -> Short.valueOf(numeric.shortValue());
      case CHAR -> Character.valueOf((char) numeric.intValue());
      case INT -> Integer.valueOf(numeric.intValue());
      case LONG -> Long.valueOf(numeric.longValue());
      case FLOAT -> Float.valueOf(numeric.floatValue());
      case DOUBLE -> Double.valueOf(numeric.doubleValue());
      default -> value;
    });
  }

  private static Optional<Object> comparison(Tree.Kind operator, Number first, Number second) {
    var floating = first instanceof Float || first instanceof Double || second instanceof Float || second instanceof Double;
    var doublePrecision = first instanceof Double || second instanceof Double;
    var left = doublePrecision ? first.doubleValue() : first.floatValue();
    var right = doublePrecision ? second.doubleValue() : second.floatValue();
    return Optional.ofNullable(switch (operator) {
      case EQUAL_TO -> floating ? floatingEqual(left, right) : first.longValue() == second.longValue();
      case NOT_EQUAL_TO -> floating ? !floatingEqual(left, right) : first.longValue() != second.longValue();
      case LESS_THAN -> floating ? left < right : first.longValue() < second.longValue();
      case LESS_THAN_EQUAL -> floating ? left <= right : first.longValue() <= second.longValue();
      case GREATER_THAN -> floating ? left > right : first.longValue() > second.longValue();
      case GREATER_THAN_EQUAL -> floating ? left >= right : first.longValue() >= second.longValue();
      default -> null;
    });
  }

  private static boolean floatingEqual(double left, double right) {
    if (Double.isNaN(left) || Double.isNaN(right)) {
      return false;
    }
    var leftBits = Double.doubleToRawLongBits(left);
    var rightBits = Double.doubleToRawLongBits(right);
    // Numeric equality collapses the two zero signs while keeping NaN unequal to every value.
    return leftBits == rightBits || ((leftBits & Long.MAX_VALUE) == 0 && (rightBits & Long.MAX_VALUE) == 0);
  }

  private static @Nullable Object arithmetic(Tree.Kind operator, Number first, Number second, TypeKind resultKind) {
    return switch (resultKind) {
      case DOUBLE -> switch (operator) {
        case PLUS -> first.doubleValue() + second.doubleValue();
        case MINUS -> first.doubleValue() - second.doubleValue();
        case MULTIPLY -> first.doubleValue() * second.doubleValue();
        case DIVIDE -> first.doubleValue() / second.doubleValue();
        case REMAINDER -> first.doubleValue() % second.doubleValue();
        default -> null;
      };
      case FLOAT -> switch (operator) {
        case PLUS -> first.floatValue() + second.floatValue();
        case MINUS -> first.floatValue() - second.floatValue();
        case MULTIPLY -> first.floatValue() * second.floatValue();
        case DIVIDE -> first.floatValue() / second.floatValue();
        case REMAINDER -> first.floatValue() % second.floatValue();
        default -> null;
      };
      case LONG -> switch (operator) {
        case PLUS -> first.longValue() + second.longValue();
        case MINUS -> first.longValue() - second.longValue();
        case MULTIPLY -> first.longValue() * second.longValue();
        case DIVIDE -> first.longValue() / second.longValue();
        case REMAINDER -> first.longValue() % second.longValue();
        case LEFT_SHIFT -> first.longValue() << second.intValue();
        case RIGHT_SHIFT -> first.longValue() >> second.intValue();
        case UNSIGNED_RIGHT_SHIFT -> first.longValue() >>> second.intValue();
        case AND -> first.longValue() & second.longValue();
        case OR -> first.longValue() | second.longValue();
        case XOR -> first.longValue() ^ second.longValue();
        default -> null;
      };
      case INT -> switch (operator) {
        case PLUS -> first.intValue() + second.intValue();
        case MINUS -> first.intValue() - second.intValue();
        case MULTIPLY -> first.intValue() * second.intValue();
        case DIVIDE -> first.intValue() / second.intValue();
        case REMAINDER -> first.intValue() % second.intValue();
        case LEFT_SHIFT -> first.intValue() << second.intValue();
        case RIGHT_SHIFT -> first.intValue() >> second.intValue();
        case UNSIGNED_RIGHT_SHIFT -> first.intValue() >>> second.intValue();
        case AND -> first.intValue() & second.intValue();
        case OR -> first.intValue() | second.intValue();
        case XOR -> first.intValue() ^ second.intValue();
        default -> null;
      };
      default -> null;
    };
  }

  private static @Nullable Number number(Object value) {
    return switch (value) {
      case Number number -> number;
      case Character character -> (int) character.charValue();
      default -> null;
    };
  }
}
