package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InvocationApplicabilityTest {
  @Test
  void givenFixedApplicabilityWithZeroOrMultipleFormals_whenDecisionIsCreated_thenNoPackingEvidenceIsInvented() {
    // Arrange
    var counts = List.of(0, 3);

    // Act
    var decisions = counts.stream().map(count -> new InvocationApplicability("method", "call", InvocationApplicabilityMode.FIXED, count,
        Optional.empty(), Optional.empty(), Optional.empty())).toList();

    // Assert
    for (var position = 0; position < counts.size(); position++) {
      var decision = decisions.get(position);
      assertEquals("method", decision.methodKey());
      assertEquals("call", decision.consumerNodeId());
      assertEquals(InvocationApplicabilityMode.FIXED, decision.mode());
      assertEquals(counts.get(position).intValue(), decision.formalCount());
      assertTrue(decision.varargArrayType().isEmpty());
      assertTrue(decision.erasedArrayType().isEmpty());
      assertTrue(decision.elementType().isEmpty());
    }
  }

  @Test
  void givenVariableApplicabilityWithOneArrayFormal_whenDecisionIsCreated_thenSelectedArrayAndElementTypesAreRetained() {
    // Arrange
    var array = type(JavaTypeCategory.ARRAY, "int[]", "int[]");
    var element = type(JavaTypeCategory.INT, "int", "int");

    // Act
    var decision = variable(1, Optional.of(array), Optional.of(array), Optional.of(element));

    // Assert
    assertEquals(InvocationApplicabilityMode.VARIABLE, decision.mode());
    assertEquals(1, decision.formalCount());
    assertEquals(Optional.of(array), decision.varargArrayType());
    assertEquals(Optional.of(array), decision.erasedArrayType());
    assertEquals(Optional.of(element), decision.elementType());
  }

  @Test
  void givenParameterizedOrArrayElements_whenVariableDecisionIsCreated_thenFormalAndAllocationShapesRemainDistinct() {
    // Arrange
    var formal = type(JavaTypeCategory.ARRAY, "java.util.List<java.lang.String>[]", "java.util.List[]");
    var erased = type(JavaTypeCategory.ARRAY, "java.util.List[]", "java.util.List[]");
    var element = type(JavaTypeCategory.DECLARED, "java.util.List<java.lang.String>", "java.util.List");
    var nestedArray = type(JavaTypeCategory.ARRAY, "java.lang.String[][]", "java.lang.String[][]");
    var arrayElement = type(JavaTypeCategory.ARRAY, "java.lang.String[]", "java.lang.String[]");

    // Act
    var generic = variable(2, Optional.of(formal), Optional.of(erased), Optional.of(element));
    var nested = variable(1, Optional.of(nestedArray), Optional.of(nestedArray), Optional.of(arrayElement));

    // Assert
    assertEquals(Optional.of(formal), generic.varargArrayType());
    assertEquals(Optional.of(erased), generic.erasedArrayType());
    assertEquals(Optional.of(element), generic.elementType());
    assertEquals(Optional.of(arrayElement), nested.elementType());
  }

  @Test
  void givenFixedModeWithAnyVariableArrayField_whenDecisionIsCreated_thenEachInventedPackingFieldIsRejected() {
    // Arrange
    var array = type(JavaTypeCategory.ARRAY, "int[]", "int[]");
    var element = type(JavaTypeCategory.INT, "int", "int");

    // Act
    var formal = assertThrows(IllegalArgumentException.class, () -> new InvocationApplicability("method", "call", InvocationApplicabilityMode.FIXED,
        1, Optional.of(array), Optional.empty(), Optional.empty()));
    var erased = assertThrows(IllegalArgumentException.class, () -> new InvocationApplicability("method", "call", InvocationApplicabilityMode.FIXED,
        1, Optional.empty(), Optional.of(array), Optional.empty()));
    var trailing = assertThrows(IllegalArgumentException.class, () -> new InvocationApplicability("method", "call", InvocationApplicabilityMode.FIXED,
        1, Optional.empty(), Optional.empty(), Optional.of(element)));

    // Assert
    for (var exception : List.of(formal, erased, trailing)) {
      assertEquals("Fixed arity has no synthetic array type evidence", exception.getMessage());
    }
  }

  @Test
  void givenVariableModeMissingAnyArrayField_whenDecisionIsCreated_thenEveryMissingEvidencePartitionIsRejected() {
    // Arrange
    var array = type(JavaTypeCategory.ARRAY, "int[]", "int[]");
    var element = type(JavaTypeCategory.INT, "int", "int");

    // Act
    var missingFormal = assertThrows(IllegalArgumentException.class,
        () -> variable(1, Optional.empty(), Optional.of(array), Optional.of(element)));
    var missingErased = assertThrows(IllegalArgumentException.class,
        () -> variable(1, Optional.of(array), Optional.empty(), Optional.of(element)));
    var missingElement = assertThrows(IllegalArgumentException.class,
        () -> variable(1, Optional.of(array), Optional.of(array), Optional.empty()));

    // Assert
    for (var exception : List.of(missingFormal, missingErased, missingElement)) {
      assertEquals("Variable arity requires formal array, erased array and element types", exception.getMessage());
    }
  }

  @Test
  void givenNegativeCountOrVariableModeWithoutFinalFormal_whenDecisionIsCreated_thenEachImpossibleCountIsRejected() {
    // Arrange
    var array = type(JavaTypeCategory.ARRAY, "int[]", "int[]");
    var element = type(JavaTypeCategory.INT, "int", "int");

    // Act
    var negative = assertThrows(IllegalArgumentException.class, () -> new InvocationApplicability("method", "call", InvocationApplicabilityMode.FIXED,
        -1, Optional.empty(), Optional.empty(), Optional.empty()));
    var missingFinal = assertThrows(IllegalArgumentException.class,
        () -> variable(0, Optional.of(array), Optional.of(array), Optional.of(element)));

    // Assert
    assertEquals("Selected arity requires a nonnegative count and a final formal in variable mode", negative.getMessage());
    assertEquals("Selected arity requires a nonnegative count and a final formal in variable mode", missingFinal.getMessage());
  }

  @Test
  void givenNonarrayOrConflictingErasureFields_whenVariableDecisionIsCreated_thenEachTypeContradictionIsRejected() {
    // Arrange
    var array = type(JavaTypeCategory.ARRAY, "int[]", "int[]");
    var differentArray = type(JavaTypeCategory.ARRAY, "long[]", "long[]");
    var element = type(JavaTypeCategory.INT, "int", "int");

    // Act
    var nonarrayFormal = assertThrows(IllegalArgumentException.class,
        () -> variable(1, Optional.of(element), Optional.of(array), Optional.of(element)));
    var nonarrayAllocation = assertThrows(IllegalArgumentException.class,
        () -> variable(1, Optional.of(array), Optional.of(element), Optional.of(element)));
    var mismatchedErasure = assertThrows(IllegalArgumentException.class,
        () -> variable(1, Optional.of(array), Optional.of(differentArray), Optional.of(element)));

    // Assert
    for (var exception : List.of(nonarrayFormal, nonarrayAllocation, mismatchedErasure)) {
      assertEquals("Variable arity array types must have array category and the same erasure", exception.getMessage());
    }
  }

  @Test
  void givenBlankIdentityNullModeOrVoidElement_whenDecisionIsCreated_thenInvalidLocalInputIsRejected() {
    // Arrange
    var array = type(JavaTypeCategory.ARRAY, "int[]", "int[]");
    var element = type(JavaTypeCategory.VOID, "void", "void");

    // Act
    var blank = assertThrows(IllegalArgumentException.class, () -> new InvocationApplicability("method", " ", InvocationApplicabilityMode.FIXED,
        0, Optional.empty(), Optional.empty(), Optional.empty()));
    var voidElement = assertThrows(IllegalArgumentException.class,
        () -> variable(1, Optional.of(array), Optional.of(array), Optional.of(element)));

    // Assert
    assertEquals("consumerNodeId cannot be blank", blank.getMessage());
    assertEquals("elementType must identify a resolved value type", voidElement.getMessage());
    assertThrows(NullPointerException.class, () -> new InvocationApplicability("method", "call", null,
        0, Optional.empty(), Optional.empty(), Optional.empty()));
    assertThrows(NullPointerException.class, () -> new InvocationApplicability("method", "call", InvocationApplicabilityMode.FIXED,
        0, null, Optional.empty(), Optional.empty()));
  }

  private static InvocationApplicability variable(int count, Optional<JavaTypeIdentity> formal, Optional<JavaTypeIdentity> erased,
      Optional<JavaTypeIdentity> element) {
    return new InvocationApplicability("method", "call", InvocationApplicabilityMode.VARIABLE, count, formal, erased, element);
  }

  private static JavaTypeIdentity type(JavaTypeCategory category, String display, String erased) {
    return new JavaTypeIdentity(category, display, erased);
  }
}
