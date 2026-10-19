package org.aquilagraph.source.api;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/** Checks grammar-derived type results independently of frontend compiler objects. */
final class WrittenExpressionSemantics {
  private WrittenExpressionSemantics() {
  }

  static void validate(SyntaxNode node) {
    var result = type(node);
    var category = switch (node.sourceKind()) {
      case INT_LITERAL -> JavaTypeCategory.INT;
      case LONG_LITERAL -> JavaTypeCategory.LONG;
      case FLOAT_LITERAL -> JavaTypeCategory.FLOAT;
      case DOUBLE_LITERAL -> JavaTypeCategory.DOUBLE;
      case BOOLEAN_LITERAL -> JavaTypeCategory.BOOLEAN;
      case CHAR_LITERAL -> JavaTypeCategory.CHAR;
      case NULL_LITERAL -> JavaTypeCategory.NULL;
      case STRING_LITERAL -> JavaTypeCategory.DECLARED;
      case UNARY_MINUS, UNARY_PLUS, BITWISE_COMPLEMENT, LOGICAL_COMPLEMENT -> unary(node);
      case PLUS, MINUS -> binary(node);
      default -> throw new IllegalArgumentException("A derived written occurrence requires its supported operator or literal kind");
    };
    if (result.category() != category || ((node.sourceKind() == JavaSourceKind.STRING_LITERAL
        || (node.syntaxKind() == SyntaxKind.BINARY && category == JavaTypeCategory.DECLARED)) && !isString(result))) {
      throw new IllegalArgumentException("A derived written result type contradicts its literal category or operand promotion");
    }
    if (node.value() instanceof SyntaxValue.Literal literal && literal.restrictedDecimal().isPresent()
        && literal.restrictedDecimal().orElseThrow().category() != result.category()) {
      throw new IllegalArgumentException("A restricted decimal operand must retain its exact integral category");
    }
  }

  static void validateCorrespondence(SyntaxNode node, NormalizedExpressionCorrespondence correspondence,
      Map<String, WrittenSourceAssociation> associations) {
    var occurrence = correspondence.region().occurrences().stream().filter(value -> value.path().equals(correspondence.occurrencePath()))
        .findFirst().orElseThrow();
    Value expected;
    if (node.value() instanceof SyntaxValue.Literal) {
      expected = new Value(occurrence.literalValue(),
          occurrence.restrictedDecimal().isPresent()
              ? Optional.empty()
              : occurrence.sourceKind() == JavaSourceKind.NULL_LITERAL
                  ? Optional.of(false)
                  : occurrence.literalValue().isPresent() ? Optional.of(true) : Optional.empty());
    } else if (node.syntaxKind() == SyntaxKind.UNARY) {
      var operand = node.requiredChild(SyntaxRole.OPERAND);
      if (operand.value() instanceof SyntaxValue.Literal literal && literal.restrictedDecimal().isPresent()) {
        expected = new Value(Optional.of(WrittenExpressionConstants.minimum(literal.restrictedDecimal().orElseThrow())), Optional.of(true));
      } else {
        var input = childValue(node, SyntaxRole.OPERAND, correspondence, associations);
        expected = new Value(
            input.constant().flatMap(
                value -> WrittenExpressionConstants.unary(node.value(SyntaxValue.Operator.class).operator(), value, type(node).category())),
            input.status());
      }
    } else {
      var left = childValue(node, SyntaxRole.LEFT_OPERAND, correspondence, associations);
      var right = childValue(node, SyntaxRole.RIGHT_OPERAND, correspondence, associations);
      var value = left.constant().flatMap(first -> right.constant().flatMap(second -> WrittenExpressionConstants
          .additive(node.value(SyntaxValue.Operator.class).operator(), first, second, type(node).category())));
      var status = left.status().equals(Optional.of(false)) || right.status().equals(Optional.of(false))
          ? Optional.of(false)
          : left.status().isPresent() && right.status().isPresent() ? Optional.of(true) : Optional.<Boolean>empty();
      expected = new Value(value, status);
    }
    if (!expected.constant().equals(correspondence.derivedConstant())
        || !expected.status().equals(correspondence.derivedConstantExpression())) {
      throw new IllegalArgumentException("Derived written values and constant classifications must follow their captured operands");
    }
  }

  private static Value childValue(SyntaxNode node, SyntaxRole role, NormalizedExpressionCorrespondence parent,
      Map<String, WrittenSourceAssociation> associations) {
    var child = node.requiredChild(role);
    var path = new ArrayList<>(parent.occurrencePath());
    path.add(new SyntaxStructuralPath.Step(role, 0));
    var occurrence = parent.region().occurrences().stream().filter(value -> value.path().equals(path)).findFirst().orElseThrow();
    var association = Optional.ofNullable(associations.get(child.nodeId()))
        .orElseThrow(() -> new IllegalArgumentException("A written expression operand requires its exact source provenance"));
    if (occurrence.compilerOperand().isPresent()) {
      var observation = parent.observations().stream()
          .filter(value -> occurrence.compilerOperand().filter(value.ownerPath()::equals).isPresent()).findFirst()
          .orElseThrow(() -> new IllegalArgumentException("An intact written operand requires its original compiler observation"));
      if (!association.ownerPath().equals(observation.ownerPath()) || !child.type().equals(observation.type())
          || child.sourceKind() != observation.sourceKind() || !child.origin().equals(observation.origin())) {
        throw new IllegalArgumentException("An intact written operand requires its exact observed compiler source and type");
      }
      return new Value(observation.constant(), observation.constantExpression());
    }
    var derived = association.normalizedExpression()
        .orElseThrow(() -> new IllegalArgumentException("A grammar-derived operand requires its explicit normalized correspondence"));
    if (!derived.region().equals(parent.region()) || !derived.occurrencePath().equals(path)
        || !derived.compilerAnchor().equals(parent.compilerAnchor()) || derived.compilerKind() != parent.compilerKind()
        || !derived.compilerType().equals(parent.compilerType()) || !derived.observations().equals(parent.observations())) {
      throw new IllegalArgumentException("A derived operand must select its exact role in the same written region");
    }
    return new Value(derived.derivedConstant(), derived.derivedConstantExpression());
  }

  private record Value(Optional<AnnotationScalar> constant, Optional<Boolean> status) {
  }

  private static JavaTypeIdentity type(SyntaxNode node) {
    return node.resolvedType().orElseThrow(() -> new IllegalArgumentException("A derived written expression requires its resolved type"));
  }

  private static JavaTypeCategory unary(SyntaxNode node) {
    var category = primitive(type(node.requiredChild(SyntaxRole.OPERAND)));
    if (node.sourceKind() == JavaSourceKind.LOGICAL_COMPLEMENT) {
      if (category != JavaTypeCategory.BOOLEAN) {
        throw new IllegalArgumentException("A logical complement requires a boolean operand");
      }
      return JavaTypeCategory.BOOLEAN;
    }
    if (category == JavaTypeCategory.BOOLEAN) {
      throw new IllegalArgumentException("A numeric unary occurrence requires a numeric operand");
    }
    if (node.sourceKind() == JavaSourceKind.BITWISE_COMPLEMENT
        && (category == JavaTypeCategory.FLOAT || category == JavaTypeCategory.DOUBLE)) {
      throw new IllegalArgumentException("A bitwise complement requires an integral operand");
    }
    return List.of(JavaTypeCategory.BYTE, JavaTypeCategory.SHORT, JavaTypeCategory.CHAR).contains(category)
        ? JavaTypeCategory.INT
        : category;
  }

  private static JavaTypeCategory binary(SyntaxNode node) {
    var left = type(node.requiredChild(SyntaxRole.LEFT_OPERAND));
    var right = type(node.requiredChild(SyntaxRole.RIGHT_OPERAND));
    if (node.sourceKind() == JavaSourceKind.PLUS && (isString(left) || isString(right))) {
      return JavaTypeCategory.DECLARED;
    }
    var operands = List.of(primitive(left), primitive(right));
    if (operands.contains(JavaTypeCategory.BOOLEAN)) {
      throw new IllegalArgumentException("A numeric additive occurrence requires numeric operands");
    }
    return operands.contains(JavaTypeCategory.DOUBLE)
        ? JavaTypeCategory.DOUBLE
        : operands.contains(JavaTypeCategory.FLOAT)
            ? JavaTypeCategory.FLOAT
            : operands.contains(JavaTypeCategory.LONG) ? JavaTypeCategory.LONG : JavaTypeCategory.INT;
  }

  private static JavaTypeCategory primitive(JavaTypeIdentity type) {
    if (List.of(JavaTypeCategory.BOOLEAN, JavaTypeCategory.BYTE, JavaTypeCategory.SHORT, JavaTypeCategory.CHAR, JavaTypeCategory.INT,
        JavaTypeCategory.LONG, JavaTypeCategory.FLOAT, JavaTypeCategory.DOUBLE).contains(type.category())) {
      return type.category();
    }
    return switch (type.erasedName()) {
      case "java.lang.Boolean" -> JavaTypeCategory.BOOLEAN;
      case "java.lang.Byte" -> JavaTypeCategory.BYTE;
      case "java.lang.Short" -> JavaTypeCategory.SHORT;
      case "java.lang.Character" -> JavaTypeCategory.CHAR;
      case "java.lang.Integer" -> JavaTypeCategory.INT;
      case "java.lang.Long" -> JavaTypeCategory.LONG;
      case "java.lang.Float" -> JavaTypeCategory.FLOAT;
      case "java.lang.Double" -> JavaTypeCategory.DOUBLE;
      default -> throw new IllegalArgumentException("A written operator requires a primitive or unboxable operand type");
    };
  }

  private static boolean isString(JavaTypeIdentity type) {
    return type.category() == JavaTypeCategory.DECLARED && "java.lang.String".equals(type.erasedName());
  }

  static void validateRestrictedParent(SyntaxNode node, Optional<SyntaxNode> parent) {
    if (!(node.value() instanceof SyntaxValue.Literal literal) || literal.restrictedDecimal().isEmpty()) {
      return;
    }
    if (parent.isEmpty() || parent.orElseThrow().sourceKind() != JavaSourceKind.UNARY_MINUS
        || parent.orElseThrow().syntaxKind() != SyntaxKind.UNARY
        || !parent.orElseThrow().requiredChild(SyntaxRole.OPERAND).nodeId().equals(node.nodeId())
        || !parent.orElseThrow().type().equals(node.type())) {
      throw new IllegalArgumentException("A restricted decimal magnitude is legal only as its immediate typed unary-minus operand");
    }
  }
}
