package org.aquilagraph.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.junit.jupiter.api.Test;

class StringConcatenationDecisionTest {
  @Test
  void givenNoSideOrEitherOperandSide_whenDecisionIsCreated_thenEachSelectedConversionSideIsRetained() {
    // Arrange
    var type = stringType();
    var sides = List.of(Optional.<SyntaxRole>empty(), Optional.of(SyntaxRole.LEFT_OPERAND), Optional.of(SyntaxRole.RIGHT_OPERAND));

    // Act
    var decisions = sides.stream().map(side -> new StringConcatenationDecision("method", "binary", type, side, false)).toList();

    // Assert
    for (var position = 0; position < sides.size(); position++) {
      var decision = decisions.get(position);
      assertEquals("method", decision.methodKey());
      assertEquals("binary", decision.binaryNodeId());
      assertEquals(type, decision.stringType());
      assertEquals(sides.get(position), decision.convertedSide());
      assertFalse(decision.compileTimeConstant());
    }
  }

  @Test
  void givenSuppliedConstantAndRuntimeClassifications_whenDecisionsAreCreated_thenTheFlagIsRetainedWithoutInventingEvidence() {
    // Arrange
    var type = stringType();

    // Act
    var constant = new StringConcatenationDecision("method", "binary", type, Optional.of(SyntaxRole.RIGHT_OPERAND), true);
    var runtime = new StringConcatenationDecision("method", "binary", type, Optional.of(SyntaxRole.RIGHT_OPERAND), false);

    // Assert
    assertTrue(constant.compileTimeConstant());
    assertFalse(runtime.compileTimeConstant());
    assertEquals(constant.convertedSide(), runtime.convertedSide());
    assertEquals(constant.stringType(), runtime.stringType());
  }

  @Test
  void givenNonStringErasureOrCategory_whenDecisionIsCreated_thenEachResultTypeContradictionIsRejected() {
    // Arrange
    var object = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.Object");
    var wrongCategory = new JavaTypeIdentity(JavaTypeCategory.TYPE_VARIABLE, "java.lang.String", "java.lang.String");

    // Act
    var erasure = assertThrows(IllegalArgumentException.class,
        () -> new StringConcatenationDecision("method", "binary", object, Optional.empty(), false));
    var category = assertThrows(IllegalArgumentException.class,
        () -> new StringConcatenationDecision("method", "binary", wrongCategory, Optional.empty(), false));

    // Assert
    assertEquals("String concatenation requires a declared java.lang.String result", erasure.getMessage());
    assertEquals("String concatenation requires a declared java.lang.String result", category.getMessage());
  }

  @Test
  void givenAChildRoleOtherThanAnOperand_whenDecisionIsCreated_thenTheInvalidConversionSideIsRejected() {
    // Arrange
    var type = stringType();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new StringConcatenationDecision("method", "binary", type, Optional.of(SyntaxRole.ARGUMENT), false));

    // Assert
    assertEquals("String conversion may select only the left or right operand", exception.getMessage());
  }

  @Test
  void givenBlankIdentityOrNullRequiredField_whenDecisionIsCreated_thenInvalidLocalInputIsRejected() {
    // Arrange
    var type = stringType();

    // Act
    var method = assertThrows(IllegalArgumentException.class,
        () -> new StringConcatenationDecision(" ", "binary", type, Optional.empty(), false));
    var binary = assertThrows(IllegalArgumentException.class,
        () -> new StringConcatenationDecision("method", " ", type, Optional.empty(), false));

    // Assert
    assertEquals("methodKey cannot be blank", method.getMessage());
    assertEquals("binaryNodeId cannot be blank", binary.getMessage());
    assertThrows(NullPointerException.class, () -> new StringConcatenationDecision("method", "binary", null, Optional.empty(), false));
    assertThrows(NullPointerException.class, () -> new StringConcatenationDecision("method", "binary", type, null, false));
  }

  private static JavaTypeIdentity stringType() {
    return new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.String");
  }
}
