package org.aquilagraph.frontend;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.util.TreePath;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.SyntaxType;

/** Evaluates typed annotation constants and projects actual compiler scalar evidence into domain values. */
final class JavaAnnotationValues {
  private final SemanticResolver semantics;

  JavaAnnotationValues(SemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
  }

  Optional<AnnotationScalar> scalar(TreePath path, TypeMirror expected) {
    if (expected instanceof DeclaredType declared && declared.asElement() instanceof TypeElement type) {
      if (type.getKind() == ElementKind.ENUM) {
        var element = semantics.element(path);
        return element instanceof VariableElement constant && constant.getKind() == ElementKind.ENUM_CONSTANT
            && semantics.sameType(constant.asType(), expected) ? scalar(constant) : Optional.empty();
      }
      if (type.getQualifiedName().contentEquals("java.lang.Class")) {
        if (path.getLeaf() instanceof MemberSelectTree selection && selection.getIdentifier().contentEquals("class")) {
          return semantics.attributedMirror(new TreePath(path, selection.getExpression())).flatMap(this::scalar);
        }
        return Optional.empty();
      }
      if (!type.getQualifiedName().contentEquals("java.lang.String")) {
        return Optional.empty();
      }
    }
    return constant(path).flatMap(result -> assignmentValue(result, expected.getKind())).flatMap(this::scalar);
  }

  Optional<AnnotationScalar> compilerScalar(AnnotationValue value) {
    return scalar(value.getValue());
  }

  private Optional<AnnotationScalar> scalar(Object value) {
    return switch (value) {
      case Boolean item -> Optional.of(new AnnotationScalar.BooleanValue(item));
      case Byte item -> Optional.of(new AnnotationScalar.ByteValue(item));
      case Short item -> Optional.of(new AnnotationScalar.ShortValue(item));
      case Character item -> Optional.of(new AnnotationScalar.CharValue(item));
      case Integer item -> Optional.of(new AnnotationScalar.IntValue(item));
      case Long item -> Optional.of(new AnnotationScalar.LongValue(item));
      case Float item -> Optional.of(new AnnotationScalar.FloatValue(item));
      case Double item -> Optional.of(new AnnotationScalar.DoubleValue(item));
      case String item -> Optional.of(new AnnotationScalar.StringValue(item));
      case VariableElement item when item.getKind() == ElementKind.ENUM_CONSTANT -> {
        var key = semantics.declarationKey(item);
        var owner = item.getEnclosingElement();
        var ownerKey = owner == null ? Optional.<String>empty() : semantics.declarationKey(owner);
        yield key.isPresent() && ownerKey.isPresent()
            ? Optional.of(new AnnotationScalar.EnumConstant(key.orElseThrow(), ownerKey.orElseThrow()))
            : Optional.empty();
      }
      case TypeMirror type -> {
        var projected = semantics.declarationType(type);
        yield projected instanceof SyntaxType.Resolved resolved && resolved.structure().isPresent()
            ? Optional.of(new AnnotationScalar.ClassLiteral(resolved.structure().orElseThrow()))
            : Optional.empty();
      }
      default -> Optional.empty();
    };
  }

  private Optional<Object> constant(TreePath path) {
    return switch (path.getLeaf()) {
      case LiteralTree literal -> Optional.ofNullable(literal.getValue());
      case ParenthesizedTree parentheses -> constant(new TreePath(path, parentheses.getExpression()));
      case TypeCastTree cast ->
        constant(new TreePath(path, cast.getExpression())).flatMap(value -> kind(path).flatMap(target -> castValue(value, target)));
      case UnaryTree unary -> constant(new TreePath(path, unary.getExpression()))
          .flatMap(value -> kind(path).flatMap(target -> unaryValue(unary.getKind(), value, target)));
      case BinaryTree binary -> binaryValue(path, binary);
      case ConditionalExpressionTree conditional -> conditionalValue(path, conditional);
      case MemberSelectTree _,IdentifierTree _ -> constantVariable(path);
      default -> Optional.empty();
    };
  }

  private Optional<Object> constantVariable(TreePath path) {
    var element = semantics.element(path);
    return element instanceof VariableElement variable ? Optional.ofNullable(variable.getConstantValue()) : Optional.empty();
  }

  private Optional<Object> conditionalValue(TreePath path, ConditionalExpressionTree conditional) {
    var condition = constant(new TreePath(path, conditional.getCondition()));
    if (condition.isEmpty() || !(condition.orElseThrow() instanceof Boolean selected)) {
      return Optional.empty();
    }
    var branch = selected ? conditional.getTrueExpression() : conditional.getFalseExpression();
    return constant(new TreePath(path, branch)).flatMap(value -> kind(path).flatMap(target -> castValue(value, target)));
  }

  private Optional<Object> binaryValue(TreePath path, BinaryTree binary) {
    var leftPath = new TreePath(path, binary.getLeftOperand());
    var rightPath = new TreePath(path, binary.getRightOperand());
    var left = constant(leftPath);
    var right = constant(rightPath);
    if (left.isEmpty() || right.isEmpty()) {
      return Optional.empty();
    }
    var first = left.orElseThrow();
    var second = right.orElseThrow();
    if (binary.getKind() == Tree.Kind.PLUS && kind(path).filter(target -> target == TypeKind.DECLARED).isPresent()) {
      return Optional.of(String.valueOf(first) + second);
    }
    if (first instanceof Boolean a && second instanceof Boolean b) {
      return booleanBinary(binary.getKind(), a, b);
    }
    if (first instanceof String a && second instanceof String b) {
      // Constant Strings share interned instances, so equal contents imply equal references here.
      return switch (binary.getKind()) {
        case EQUAL_TO -> Optional.of(a.equals(b));
        case NOT_EQUAL_TO -> Optional.of(!a.equals(b));
        default -> Optional.empty();
      };
    }
    var promoted = switch (binary.getKind()) {
      case LEFT_SHIFT, RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT ->
        kind(leftPath).filter(JavaAnnotationValues::numeric).map(type -> type == TypeKind.LONG ? TypeKind.LONG : TypeKind.INT);
      default -> promotedKind(leftPath, rightPath);
    };
    if (promoted.isEmpty()) {
      return Optional.empty();
    }
    try {
      return numericBinary(binary.getKind(), first, second, promoted.orElseThrow());
    } catch (ArithmeticException ignored) {
      return Optional.empty();
    }
  }

  private Optional<TypeKind> promotedKind(TreePath first, TreePath second) {
    var left = kind(first);
    var right = kind(second);
    if (left.isEmpty() || right.isEmpty() || !numeric(left.orElseThrow()) || !numeric(right.orElseThrow())) {
      return Optional.empty();
    }
    if (left.orElseThrow() == TypeKind.DOUBLE || right.orElseThrow() == TypeKind.DOUBLE) {
      return Optional.of(TypeKind.DOUBLE);
    }
    if (left.orElseThrow() == TypeKind.FLOAT || right.orElseThrow() == TypeKind.FLOAT) {
      return Optional.of(TypeKind.FLOAT);
    }
    return Optional.of(left.orElseThrow() == TypeKind.LONG || right.orElseThrow() == TypeKind.LONG ? TypeKind.LONG : TypeKind.INT);
  }

  private Optional<TypeKind> kind(TreePath path) {
    return semantics.attributedMirror(path).map(TypeMirror::getKind);
  }

  private static boolean numeric(TypeKind kind) {
    return switch (kind) {
      case BYTE, SHORT, CHAR, INT, LONG, FLOAT, DOUBLE -> true;
      default -> false;
    };
  }

  private static Optional<Object> assignmentValue(Object value, TypeKind expected) {
    if (expected == TypeKind.BOOLEAN) {
      return value instanceof Boolean ? Optional.of(value) : Optional.empty();
    }
    if (expected == TypeKind.DECLARED) {
      return value instanceof String ? Optional.of(value) : Optional.empty();
    }
    var number = number(value);
    if (number.isEmpty()) {
      return Optional.empty();
    }
    if (expected == TypeKind.BYTE || expected == TypeKind.SHORT || expected == TypeKind.CHAR) {
      var original = number.orElseThrow().longValue();
      var minimum = expected == TypeKind.BYTE ? Byte.MIN_VALUE : expected == TypeKind.SHORT ? Short.MIN_VALUE : Character.MIN_VALUE;
      var maximum = expected == TypeKind.BYTE ? Byte.MAX_VALUE : expected == TypeKind.SHORT ? Short.MAX_VALUE : Character.MAX_VALUE;
      if (original < minimum || original > maximum) {
        return Optional.empty();
      }
    }
    return castValue(value, expected);
  }

  private static Optional<Object> castValue(Object value, TypeKind target) {
    if (target == TypeKind.BOOLEAN) {
      return value instanceof Boolean ? Optional.of(value) : Optional.empty();
    }
    if (target == TypeKind.DECLARED) {
      return value instanceof String ? Optional.of(value) : Optional.empty();
    }
    var number = number(value);
    if (number.isEmpty()) {
      return Optional.empty();
    }
    var numericValue = number.orElseThrow();
    return switch (target) {
      case BYTE -> Optional.of(numericValue.byteValue());
      case SHORT -> Optional.of(numericValue.shortValue());
      case CHAR -> Optional.of((char) numericValue.intValue());
      case INT -> Optional.of(numericValue.intValue());
      case LONG -> Optional.of(numericValue.longValue());
      case FLOAT -> Optional.of(numericValue.floatValue());
      case DOUBLE -> Optional.of(numericValue.doubleValue());
      default -> Optional.empty();
    };
  }

  private static Optional<Number> number(Object value) {
    return value instanceof Number numericValue
        ? Optional.of(numericValue)
        : value instanceof Character character ? Optional.of((int) character) : Optional.empty();
  }

  private static Optional<Object> unaryValue(Tree.Kind operation, Object value, TypeKind result) {
    if (operation == Tree.Kind.LOGICAL_COMPLEMENT) {
      return value instanceof Boolean booleanValue ? Optional.of(!booleanValue) : Optional.empty();
    }
    var number = number(value);
    if (number.isEmpty()) {
      return Optional.empty();
    }
    var numericValue = number.orElseThrow();
    return switch (result) {
      case INT -> switch (operation) {
        case UNARY_PLUS -> Optional.of(numericValue.intValue());
        case UNARY_MINUS -> Optional.of(-numericValue.intValue());
        case BITWISE_COMPLEMENT -> Optional.of(~numericValue.intValue());
        default -> Optional.empty();
      };
      case LONG -> switch (operation) {
        case UNARY_PLUS -> Optional.of(numericValue.longValue());
        case UNARY_MINUS -> Optional.of(-numericValue.longValue());
        case BITWISE_COMPLEMENT -> Optional.of(~numericValue.longValue());
        default -> Optional.empty();
      };
      case FLOAT -> switch (operation) {
        case UNARY_PLUS -> Optional.of(numericValue.floatValue());
        case UNARY_MINUS -> Optional.of(-numericValue.floatValue());
        default -> Optional.empty();
      };
      case DOUBLE -> switch (operation) {
        case UNARY_PLUS -> Optional.of(numericValue.doubleValue());
        case UNARY_MINUS -> Optional.of(-numericValue.doubleValue());
        default -> Optional.empty();
      };
      default -> Optional.empty();
    };
  }

  private static Optional<Object> booleanBinary(Tree.Kind operation, boolean first, boolean second) {
    return switch (operation) {
      case AND, CONDITIONAL_AND -> Optional.of(first && second);
      case OR, CONDITIONAL_OR -> Optional.of(first || second);
      case XOR, NOT_EQUAL_TO -> Optional.of(first != second);
      case EQUAL_TO -> Optional.of(first == second);
      default -> Optional.empty();
    };
  }

  private static Optional<Object> numericBinary(Tree.Kind operation, Object first, Object second, TypeKind promoted) {
    var a = number(first).orElseThrow();
    var b = number(second).orElseThrow();
    return switch (promoted) {
      case INT -> integerBinary(operation, a.intValue(), b.intValue());
      case LONG -> longBinary(operation, a.longValue(), b.longValue());
      case FLOAT -> floatBinary(operation, a.floatValue(), b.floatValue());
      case DOUBLE -> doubleBinary(operation, a.doubleValue(), b.doubleValue());
      default -> Optional.empty();
    };
  }

  private static Optional<Object> integerBinary(Tree.Kind operation, int a, int b) {
    return switch (operation) {
      case LEFT_SHIFT -> Optional.of(a << b);
      case RIGHT_SHIFT -> Optional.of(a >> b);
      case UNSIGNED_RIGHT_SHIFT -> Optional.of(a >>> b);
      default -> longBinary(operation, a, b).map(result -> result instanceof Long value ? (Object) value.intValue() : result);
    };
  }

  private static Optional<Object> longBinary(Tree.Kind operation, long a, long b) {
    return switch (operation) {
      case PLUS -> Optional.of(a + b);
      case MINUS -> Optional.of(a - b);
      case MULTIPLY -> Optional.of(a * b);
      case DIVIDE -> Optional.of(a / b);
      case REMAINDER -> Optional.of(a % b);
      case LEFT_SHIFT -> Optional.of(a << b);
      case RIGHT_SHIFT -> Optional.of(a >> b);
      case UNSIGNED_RIGHT_SHIFT -> Optional.of(a >>> b);
      case LESS_THAN -> Optional.of(a < b);
      case GREATER_THAN -> Optional.of(a > b);
      case LESS_THAN_EQUAL -> Optional.of(a <= b);
      case GREATER_THAN_EQUAL -> Optional.of(a >= b);
      case EQUAL_TO -> Optional.of(a == b);
      case NOT_EQUAL_TO -> Optional.of(a != b);
      case AND -> Optional.of(a & b);
      case XOR -> Optional.of(a ^ b);
      case OR -> Optional.of(a | b);
      default -> Optional.empty();
    };
  }

  private static Optional<Object> floatBinary(Tree.Kind operation, float a, float b) {
    return switch (operation) {
      case PLUS -> Optional.of(a + b);
      case MINUS -> Optional.of(a - b);
      case MULTIPLY -> Optional.of(a * b);
      case DIVIDE -> Optional.of(a / b);
      case REMAINDER -> Optional.of(a % b);
      default -> doubleBinary(operation, a, b);
    };
  }

  private static Optional<Object> doubleBinary(Tree.Kind operation, double a, double b) {
    return switch (operation) {
      case PLUS -> Optional.of(a + b);
      case MINUS -> Optional.of(a - b);
      case MULTIPLY -> Optional.of(a * b);
      case DIVIDE -> Optional.of(a / b);
      case REMAINDER -> Optional.of(a % b);
      case LESS_THAN -> Optional.of(a < b);
      case GREATER_THAN -> Optional.of(a > b);
      case LESS_THAN_EQUAL -> Optional.of(a <= b);
      case GREATER_THAN_EQUAL -> Optional.of(a >= b);
      case EQUAL_TO -> Optional.of(a == b);
      case NOT_EQUAL_TO -> Optional.of(a != b);
      default -> Optional.empty();
    };
  }

}
