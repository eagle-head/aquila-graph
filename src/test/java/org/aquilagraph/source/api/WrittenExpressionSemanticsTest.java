package org.aquilagraph.source.api;

import org.aquilagraph.source.internal.WrittenExpressionSemantics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.SourceSpan;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class WrittenExpressionSemanticsTest {
  @TestFactory
  Stream<DynamicTest> givenTypedUnaryOperands_whenWrittenSemanticsAreValidated_thenOperatorDomainsAndPromotionsAreEnforced() {
    // Arrange
    var cases = new ArrayList<UnaryCase>();
    var numeric = List.of(new NumericType(JavaTypeCategory.BYTE, "java.lang.Byte", JavaTypeCategory.INT, true),
        new NumericType(JavaTypeCategory.SHORT, "java.lang.Short", JavaTypeCategory.INT, true),
        new NumericType(JavaTypeCategory.CHAR, "java.lang.Character", JavaTypeCategory.INT, true),
        new NumericType(JavaTypeCategory.INT, "java.lang.Integer", JavaTypeCategory.INT, true),
        new NumericType(JavaTypeCategory.LONG, "java.lang.Long", JavaTypeCategory.LONG, true),
        new NumericType(JavaTypeCategory.FLOAT, "java.lang.Float", JavaTypeCategory.FLOAT, false),
        new NumericType(JavaTypeCategory.DOUBLE, "java.lang.Double", JavaTypeCategory.DOUBLE, false));
    for (var type : numeric) {
      for (var boxed : List.of(false, true)) {
        var category = boxed ? JavaTypeCategory.DECLARED : type.category();
        var name = boxed ? type.boxedName() : type.category().name().toLowerCase(Locale.ROOT);
        cases.add(new UnaryCase(SyntaxOperator.UNARY_PLUS, category, name, type.promoted(), true));
        cases.add(new UnaryCase(SyntaxOperator.UNARY_MINUS, category, name, type.promoted(), true));
        cases.add(new UnaryCase(SyntaxOperator.BITWISE_COMPLEMENT, category, name, type.promoted(), type.integral()));
        cases.add(new UnaryCase(SyntaxOperator.LOGICAL_COMPLEMENT, category, name, JavaTypeCategory.BOOLEAN, false));
      }
    }
    for (var boxed : List.of(false, true)) {
      var category = boxed ? JavaTypeCategory.DECLARED : JavaTypeCategory.BOOLEAN;
      var name = boxed ? "java.lang.Boolean" : "boolean";
      cases.add(new UnaryCase(SyntaxOperator.LOGICAL_COMPLEMENT, category, name, JavaTypeCategory.BOOLEAN, true));
      cases.add(new UnaryCase(SyntaxOperator.UNARY_PLUS, category, name, JavaTypeCategory.INT, false));
      cases.add(new UnaryCase(SyntaxOperator.UNARY_MINUS, category, name, JavaTypeCategory.INT, false));
      cases.add(new UnaryCase(SyntaxOperator.BITWISE_COMPLEMENT, category, name, JavaTypeCategory.INT, false));
    }
    cases.add(new UnaryCase(SyntaxOperator.LOGICAL_COMPLEMENT, JavaTypeCategory.DECLARED, "java.lang.String", JavaTypeCategory.BOOLEAN, false));

    // Act
    var observations = cases.stream().map(value -> DynamicTest.dynamicTest(
        "given" + value.operandName() + "Operand_when" + value.operator() + "IsValidated_thenTheDomainIs" + (value.valid() ? "Accepted" : "Rejected"), () -> {
          // Arrange
          var expression = expression(value);

          // Act / Assert
          if (value.valid()) {
            assertDoesNotThrow(() -> WrittenExpressionSemantics.validate(expression));
          } else {
            assertThrows(IllegalArgumentException.class, () -> WrittenExpressionSemantics.validate(expression));
          }
        }));

    // Assert
    return observations;
  }

  private static SyntaxNode expression(UnaryCase value) {
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0)
        .child(SyntaxRole.BODY, 0).child(SyntaxRole.EXPRESSION, 0);
    var operandType = new SyntaxType.Resolved(new JavaTypeIdentity(value.operandCategory(), value.operandName(), value.operandName()));
    var reference = new SemanticReference(ReferenceKind.PARAMETER, ResolutionStatus.RESOLVED, Optional.of("parameter:value"),
        Optional.of(value.operandName()), List.of(value.operandName()), List.of(value.operandName()));
    var operand = SyntaxNode.source("source", path.child(SyntaxRole.OPERAND, 0), JavaSourceKind.IDENTIFIER, SyntaxKind.VARIABLE_ACCESS,
        operandType, new SyntaxValue.SourceSymbol("value"), List.of(), Optional.of(reference), new SourceSpan("source", 1, 2));
    var resultName = value.resultCategory().name().toLowerCase(Locale.ROOT);
    var resultType = new SyntaxType.Resolved(new JavaTypeIdentity(value.resultCategory(), resultName, resultName));
    return SyntaxNode.source("source", path, JavaSourceKind.valueOf(value.operator().name()), SyntaxKind.UNARY, resultType,
        new SyntaxValue.Operator(value.operator()), List.of(new SyntaxChild(SyntaxRole.OPERAND, 0, operand)), Optional.empty(), new SourceSpan("source", 0, 2));
  }

  private record NumericType(JavaTypeCategory category, String boxedName, JavaTypeCategory promoted, boolean integral) {
  }

  private record UnaryCase(SyntaxOperator operator, JavaTypeCategory operandCategory, String operandName, JavaTypeCategory resultCategory,
      boolean valid) {
  }
}
