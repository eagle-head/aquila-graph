package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConversionEvidenceTest {
  @Test
  void givenPrimitiveAndReferenceUsesWithoutBoxing_whenDecisionsAreCreated_thenExplicitEmptyStepsAndSelectedTypesAreRetained() {
    // Arrange
    var primitive = type(JavaTypeCategory.INT, "int");
    var reference = type(JavaTypeCategory.DECLARED, "java.lang.Integer");
    var object = type(JavaTypeCategory.DECLARED, "java.lang.Object");

    // Act
    var primitiveUse = use(primitive, primitive, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    var referenceUse = use(reference, object, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    // Assert
    assertEquals("method", primitiveUse.methodKey());
    assertEquals("consumer", primitiveUse.consumerNodeId());
    assertEquals(SyntaxRole.EXPRESSION, primitiveUse.role());
    assertEquals(0, primitiveUse.position());
    assertEquals(primitive, primitiveUse.sourceType());
    assertEquals(primitive, primitiveUse.selectedTarget());
    assertEquals(reference, referenceUse.sourceType());
    assertEquals(object, referenceUse.selectedTarget());
    for (var decision : List.of(primitiveUse, referenceUse)) {
      assertTrue(decision.checkedReferenceTarget().isEmpty());
      assertTrue(decision.unboxedType().isEmpty());
      assertTrue(decision.effectivePrimitiveType().isEmpty());
      assertTrue(decision.preBoxPrimitiveType().isEmpty());
      assertTrue(decision.boxedType().isEmpty());
    }
  }

  @Test
  void givenEachPrimitiveAndItsWrapper_whenBoxingAndUnboxingDecisionsAreCreated_thenEveryExactStepIsRetained() {
    // Arrange
    var cases = List.of(
        new WrapperCase(JavaTypeCategory.BOOLEAN, "boolean", "java.lang.Boolean"),
        new WrapperCase(JavaTypeCategory.BYTE, "byte", "java.lang.Byte"),
        new WrapperCase(JavaTypeCategory.SHORT, "short", "java.lang.Short"),
        new WrapperCase(JavaTypeCategory.CHAR, "char", "java.lang.Character"),
        new WrapperCase(JavaTypeCategory.INT, "int", "java.lang.Integer"),
        new WrapperCase(JavaTypeCategory.LONG, "long", "java.lang.Long"),
        new WrapperCase(JavaTypeCategory.FLOAT, "float", "java.lang.Float"),
        new WrapperCase(JavaTypeCategory.DOUBLE, "double", "java.lang.Double"));

    // Act
    var boxed = cases.stream().map(testCase -> use(testCase.primitive(), testCase.wrapper(), Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.of(testCase.primitive()), Optional.of(testCase.wrapper()))).toList();
    var unboxed = cases.stream().map(testCase -> use(testCase.wrapper(), testCase.primitive(), Optional.empty(),
        Optional.of(testCase.primitive()), Optional.of(testCase.primitive()), Optional.empty(), Optional.empty())).toList();

    // Assert
    for (var position = 0; position < cases.size(); position++) {
      var testCase = cases.get(position);
      assertEquals(Optional.of(testCase.primitive()), boxed.get(position).preBoxPrimitiveType());
      assertEquals(Optional.of(testCase.wrapper()), boxed.get(position).boxedType());
      assertEquals(Optional.of(testCase.primitive()), unboxed.get(position).unboxedType());
      assertEquals(Optional.of(testCase.primitive()), unboxed.get(position).effectivePrimitiveType());
      assertTrue(boxed.get(position).unboxedType().isEmpty());
      assertTrue(unboxed.get(position).boxedType().isEmpty());
    }
  }

  @Test
  void givenUnboxingAndPrimitiveWidening_whenDecisionIsCreated_thenEffectiveSelectedTypeIsPreserved() {
    // Arrange
    var source = type(JavaTypeCategory.DECLARED, "java.lang.Integer");
    var unboxed = type(JavaTypeCategory.INT, "int");
    var widened = type(JavaTypeCategory.DOUBLE, "double");

    // Act
    var decision = use(source, widened, Optional.empty(), Optional.of(unboxed), Optional.of(widened), Optional.empty(), Optional.empty());

    // Assert
    assertEquals(Optional.of(unboxed), decision.unboxedType());
    assertEquals(Optional.of(widened), decision.effectivePrimitiveType());
    assertEquals(widened, decision.selectedTarget());
  }

  @Test
  void givenCheckedPrimitiveCastUse_whenDecisionIsCreated_thenWrapperCheckPrecedesTheSelectedUnboxingFields() {
    // Arrange
    var source = type(JavaTypeCategory.DECLARED, "java.lang.Object");
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Integer");
    var primitive = type(JavaTypeCategory.INT, "int");

    // Act
    var decision = use(source, primitive, Optional.of(wrapper), Optional.of(primitive), Optional.of(primitive), Optional.empty(), Optional.empty());

    // Assert
    assertEquals(source, decision.sourceType());
    assertEquals(Optional.of(wrapper), decision.checkedReferenceTarget());
    assertEquals(Optional.of(primitive), decision.unboxedType());
  }

  @Test
  void givenSuppliedConstantNarrowingBeforeBoxing_whenDecisionIsCreated_thenPreBoxAndWidenedReferenceTypesRemainDistinct() {
    // Arrange
    var source = type(JavaTypeCategory.INT, "int");
    var narrowed = type(JavaTypeCategory.BYTE, "byte");
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Byte");
    var target = type(JavaTypeCategory.DECLARED, "java.lang.Number");

    // Act
    var decision = use(source, target, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(narrowed), Optional.of(wrapper));

    // Assert
    assertEquals(source, decision.sourceType());
    assertEquals(Optional.of(narrowed), decision.preBoxPrimitiveType());
    assertEquals(Optional.of(wrapper), decision.boxedType());
    assertEquals(target, decision.selectedTarget());
  }

  @Test
  void givenUnpairedConversionFields_whenDecisionIsCreated_thenEveryOrphanFieldIsRejected() {
    // Arrange
    var primitive = type(JavaTypeCategory.INT, "int");
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Integer");

    // Act
    var unboxedOnly = assertThrows(IllegalArgumentException.class, () -> use(wrapper, primitive, Optional.empty(), Optional.of(primitive),
        Optional.empty(), Optional.empty(), Optional.empty()));
    var effectiveOnly = assertThrows(IllegalArgumentException.class, () -> use(wrapper, primitive, Optional.empty(), Optional.empty(),
        Optional.of(primitive), Optional.empty(), Optional.empty()));
    var preBoxOnly = assertThrows(IllegalArgumentException.class, () -> use(primitive, wrapper, Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.of(primitive), Optional.empty()));
    var boxedOnly = assertThrows(IllegalArgumentException.class, () -> use(primitive, wrapper, Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty(), Optional.of(wrapper)));

    // Assert
    for (var exception : List.of(unboxedOnly, effectiveOnly, preBoxOnly, boxedOnly)) {
      assertEquals("Each selected conversion requires its paired type fields", exception.getMessage());
    }
  }

  @Test
  void givenSimultaneousBoxingAndUnboxing_whenDecisionIsCreated_thenTheContradictorySelectionIsRejected() {
    // Arrange
    var primitive = type(JavaTypeCategory.INT, "int");
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Integer");

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> use(wrapper, primitive, Optional.empty(), Optional.of(primitive),
        Optional.of(primitive), Optional.of(primitive), Optional.of(wrapper)));

    // Assert
    assertEquals("Boxing and unboxing are mutually exclusive for one use", exception.getMessage());
  }

  @Test
  void givenCheckedTargetWithoutUnboxingOrWithWrongWrapper_whenDecisionIsCreated_thenEachContradictionIsRejected() {
    // Arrange
    var primitive = type(JavaTypeCategory.INT, "int");
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Integer");
    var otherWrapper = type(JavaTypeCategory.DECLARED, "java.lang.Long");
    var source = type(JavaTypeCategory.DECLARED, "java.lang.Object");

    // Act
    var orphan = assertThrows(IllegalArgumentException.class,
        () -> use(wrapper, wrapper, Optional.of(wrapper), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    var mismatch = assertThrows(IllegalArgumentException.class, () -> use(source, primitive, Optional.of(otherWrapper), Optional.of(primitive),
        Optional.of(primitive), Optional.empty(), Optional.empty()));

    // Assert
    assertEquals("A checked wrapper target requires selected unboxing", orphan.getMessage());
    assertEquals("A checked unboxing step requires the matching cast wrapper target", mismatch.getMessage());
  }

  @Test
  void givenNarrowingUnboxingOrDifferentSelectedTarget_whenDecisionIsCreated_thenPrimitiveStepContradictionsAreRejected() {
    // Arrange
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Integer");
    var primitive = type(JavaTypeCategory.INT, "int");
    var narrowed = type(JavaTypeCategory.BYTE, "byte");
    var widened = type(JavaTypeCategory.LONG, "long");

    // Act
    var narrowing = assertThrows(IllegalArgumentException.class, () -> use(wrapper, narrowed, Optional.empty(), Optional.of(primitive),
        Optional.of(narrowed), Optional.empty(), Optional.empty()));
    var targetMismatch = assertThrows(IllegalArgumentException.class, () -> use(wrapper, primitive, Optional.empty(), Optional.of(primitive),
        Optional.of(widened), Optional.empty(), Optional.empty()));

    // Assert
    assertEquals("Unboxing requires consistent reference, primitive and selected target types", narrowing.getMessage());
    assertEquals("Unboxing requires consistent reference, primitive and selected target types", targetMismatch.getMessage());
  }

  @Test
  void givenNonWrapperUnboxingWithoutCheckedStep_whenDecisionIsCreated_thenTheMissingRequiredStepIsRejected() {
    // Arrange
    var source = type(JavaTypeCategory.DECLARED, "java.lang.Object");
    var primitive = type(JavaTypeCategory.INT, "int");

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> use(source, primitive, Optional.empty(), Optional.of(primitive),
        Optional.of(primitive), Optional.empty(), Optional.empty()));

    // Assert
    assertEquals("A declared unboxing source must be the matching wrapper or have a checked wrapper target", exception.getMessage());
  }

  @Test
  void givenWrongWrapperOrPrimitiveWideningBeforeBoxing_whenDecisionIsCreated_thenEachBoxingContradictionIsRejected() {
    // Arrange
    var source = type(JavaTypeCategory.INT, "int");
    var widened = type(JavaTypeCategory.LONG, "long");
    var wrongWrapper = type(JavaTypeCategory.DECLARED, "java.lang.Byte");
    var longWrapper = type(JavaTypeCategory.DECLARED, "java.lang.Long");

    // Act
    var wrapperMismatch = assertThrows(IllegalArgumentException.class, () -> use(source, wrongWrapper, Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.of(source), Optional.of(wrongWrapper)));
    var widening = assertThrows(IllegalArgumentException.class, () -> use(source, longWrapper, Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.of(widened), Optional.of(longWrapper)));

    // Assert
    assertEquals("Boxing requires consistent primitive, wrapper and selected reference target types", wrapperMismatch.getMessage());
    assertEquals("Boxing requires consistent primitive, wrapper and selected reference target types", widening.getMessage());
  }

  @Test
  void givenPrimitiveReferenceCrossingWithoutSelectedConversion_whenDecisionIsCreated_thenMissingStepsAreRejected() {
    // Arrange
    var primitive = type(JavaTypeCategory.INT, "int");
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Integer");

    // Act
    var missingBox = assertThrows(IllegalArgumentException.class,
        () -> use(primitive, wrapper, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    var missingUnbox = assertThrows(IllegalArgumentException.class,
        () -> use(wrapper, primitive, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

    // Assert
    assertEquals("A primitive and reference use requires a selected conversion", missingBox.getMessage());
    assertEquals("A primitive and reference use requires a selected conversion", missingUnbox.getMessage());
  }

  @Test
  void givenBooleanAndNumericPrimitiveTargets_whenDecisionIsCreated_thenBothImpossibleDirectionsAreRejected() {
    // Arrange
    var numeric = type(JavaTypeCategory.INT, "int");
    var booleanType = type(JavaTypeCategory.BOOLEAN, "boolean");

    // Act
    var numericToBoolean = assertThrows(IllegalArgumentException.class,
        () -> use(numeric, booleanType, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    var booleanToNumeric = assertThrows(IllegalArgumentException.class,
        () -> use(booleanType, numeric, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

    // Assert
    assertEquals("Boolean and numeric primitives cannot share a conversion", numericToBoolean.getMessage());
    assertEquals("Boolean and numeric primitives cannot share a conversion", booleanToNumeric.getMessage());
  }

  @Test
  void givenNumericNarrowingUseShape_whenDecisionIsCreated_thenTheSuppliedCastTargetIsPreserved() {
    // Arrange
    var source = type(JavaTypeCategory.DOUBLE, "double");
    var target = type(JavaTypeCategory.INT, "int");

    // Act
    var decision = use(source, target, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    // Assert
    assertEquals(source, decision.sourceType());
    assertEquals(target, decision.selectedTarget());
    assertTrue(decision.unboxedType().isEmpty());
    assertTrue(decision.preBoxPrimitiveType().isEmpty());
  }

  @Test
  void givenTypeVariableSourceWithSuppliedUnboxing_whenDecisionIsCreated_thenUnknownBoundAuthenticationIsNotInvented() {
    // Arrange
    var source = type(JavaTypeCategory.TYPE_VARIABLE, "T");
    var primitive = type(JavaTypeCategory.INT, "int");

    // Act
    var decision = use(source, primitive, Optional.empty(), Optional.of(primitive), Optional.of(primitive), Optional.empty(), Optional.empty());

    // Assert
    assertEquals(source, decision.sourceType());
    assertEquals(Optional.of(primitive), decision.unboxedType());
  }

  @Test
  void givenInvalidUseIdentityPositionOrRequiredField_whenDecisionIsCreated_thenInvalidLocalInputIsRejected() {
    // Arrange
    var type = type(JavaTypeCategory.INT, "int");

    // Act
    var blankMethod = assertThrows(IllegalArgumentException.class, () -> new ConversionEvidence(" ", "consumer", SyntaxRole.EXPRESSION, 0,
        type, type, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    var blankConsumer = assertThrows(IllegalArgumentException.class, () -> new ConversionEvidence("method", " ", SyntaxRole.EXPRESSION, 0,
        type, type, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    var negative = assertThrows(IllegalArgumentException.class, () -> new ConversionEvidence("method", "consumer", SyntaxRole.EXPRESSION, -1,
        type, type, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

    // Assert
    assertEquals("methodKey cannot be blank", blankMethod.getMessage());
    assertEquals("consumerNodeId cannot be blank", blankConsumer.getMessage());
    assertEquals("A typed use position cannot be negative", negative.getMessage());
    assertThrows(NullPointerException.class, () -> new ConversionEvidence("method", "consumer", null, 0, type, type, Optional.empty(),
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    assertThrows(NullPointerException.class, () -> use(type, type, null, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    assertThrows(NullPointerException.class, () -> use(null, type, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
  }

  private static ConversionEvidence use(JavaTypeIdentity source, JavaTypeIdentity target, Optional<JavaTypeIdentity> checked,
      Optional<JavaTypeIdentity> unboxed, Optional<JavaTypeIdentity> effective, Optional<JavaTypeIdentity> preBox, Optional<JavaTypeIdentity> boxed) {
    return new ConversionEvidence("method", "consumer", SyntaxRole.EXPRESSION, 0, source, target, checked, unboxed, effective, preBox, boxed);
  }

  private static JavaTypeIdentity type(JavaTypeCategory category, String name) {
    return new JavaTypeIdentity(category, name, name);
  }

  private record WrapperCase(JavaTypeCategory category, String primitiveName, String wrapperName) {
    JavaTypeIdentity primitive() {
      return type(category, primitiveName);
    }

    JavaTypeIdentity wrapper() {
      return type(JavaTypeCategory.DECLARED, wrapperName);
    }
  }
}
