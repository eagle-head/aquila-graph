package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StringExpressionEvidenceTest {
  @Test
  void givenEitherOrBothStringOperands_whenRetaining_thenEachClassificationIsIndependentOfRuntimeConversionPolicy() {
    var classifications = List.of(List.of(true, true), List.of(false, true), List.of(true, false));
    for (var pair : classifications) {
      var evidence = new StringExpressionEvidence("method", "binary", stringType(), known(StringExpressionEvidence.LEFT_SLOT, pair.getFirst()),
          known(StringExpressionEvidence.RIGHT_SLOT, pair.getLast()), known(StringExpressionEvidence.CONSTANT_SLOT, false));
      assertEquals("method", evidence.methodKey());
      assertEquals("binary", evidence.binaryNodeId());
      assertEquals(stringType(), evidence.stringType());
      assertEquals(Optional.of(pair.getFirst()), evidence.leftIsString().knownValue());
      assertEquals(Optional.of(pair.getLast()), evidence.rightIsString().knownValue());
      assertFalse(evidence.compileTimeConstant().knownValue().orElseThrow());
    }
  }

  @Test
  void givenKnownFalseAndMissingOtherSlots_whenRetaining_thenNoKnownClassificationIsDiscarded() {
    var missing = EvidenceValue.<Boolean>missing(origin(StringExpressionEvidence.RIGHT_SLOT), "Classification unavailable");
    var evidence = new StringExpressionEvidence("method", "binary", stringType(), known(StringExpressionEvidence.LEFT_SLOT, true), missing,
        known(StringExpressionEvidence.CONSTANT_SLOT, false));
    assertTrue(evidence.leftIsString().knownValue().orElseThrow());
    assertEquals(missing, evidence.rightIsString());
    assertFalse(evidence.compileTimeConstant().knownValue().orElseThrow());
  }

  @Test
  void givenNonStringResultOrBothKnownNonStringOperands_whenRetaining_thenEachContradictionRejects() {
    var wrongTypes = List.of(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.Object"),
        new JavaTypeIdentity(JavaTypeCategory.TYPE_VARIABLE, "java.lang.String", "java.lang.String"));
    for (var type : wrongTypes) {
      assertThrows(IllegalArgumentException.class, () -> new StringExpressionEvidence("method", "binary", type,
          known(StringExpressionEvidence.LEFT_SLOT, true), known(StringExpressionEvidence.RIGHT_SLOT, false),
          known(StringExpressionEvidence.CONSTANT_SLOT, false)));
    }
    assertThrows(IllegalArgumentException.class, () -> new StringExpressionEvidence("method", "binary", stringType(),
        known(StringExpressionEvidence.LEFT_SLOT, false), known(StringExpressionEvidence.RIGHT_SLOT, false),
        known(StringExpressionEvidence.CONSTANT_SLOT, false)));
  }

  @Test
  void givenForeignSlotOrInapplicableOperand_whenRetaining_thenTheCreatingAddressAndApplicableStateAreRequired() {
    var left = known(StringExpressionEvidence.LEFT_SLOT, true);
    var right = known(StringExpressionEvidence.RIGHT_SLOT, false);
    var constant = known(StringExpressionEvidence.CONSTANT_SLOT, false);
    assertThrows(IllegalArgumentException.class, () -> new StringExpressionEvidence("method", "binary", stringType(), right, left, constant));
    var inapplicable = EvidenceValue.<Boolean>notApplicable(origin(StringExpressionEvidence.RIGHT_SLOT), "Not applicable");
    assertThrows(IllegalArgumentException.class, () -> new StringExpressionEvidence("method", "binary", stringType(), left, inapplicable, constant));
  }

  @Test
  void givenBlankIdentityOrNullRequiredFields_whenRetaining_thenInvalidLocalInputRejects() {
    var left = known(StringExpressionEvidence.LEFT_SLOT, true);
    var right = known(StringExpressionEvidence.RIGHT_SLOT, false);
    var constant = known(StringExpressionEvidence.CONSTANT_SLOT, false);
    assertThrows(IllegalArgumentException.class, () -> new StringExpressionEvidence(" ", "binary", stringType(), left, right, constant));
    assertThrows(IllegalArgumentException.class, () -> new StringExpressionEvidence("method", " ", stringType(), left, right, constant));
    assertThrows(NullPointerException.class, () -> new StringExpressionEvidence("method", "binary", null, left, right, constant));
    assertThrows(NullPointerException.class, () -> new StringExpressionEvidence("method", "binary", stringType(), null, right, constant));
    assertThrows(NullPointerException.class, () -> new StringExpressionEvidence("method", "binary", stringType(), left, null, constant));
    assertThrows(NullPointerException.class, () -> new StringExpressionEvidence("method", "binary", stringType(), left, right, null));
  }

  private static EvidenceValue<Boolean> known(String slot, boolean value) {
    return EvidenceValue.known(value, origin(slot));
  }

  private static EvidenceOrigin origin(String slot) {
    return new EvidenceOrigin("method", "binary", slot, Optional.empty());
  }

  private static JavaTypeIdentity stringType() {
    return new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.String");
  }
}
