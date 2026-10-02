package org.aquilagraph.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.junit.jupiter.api.Test;

class CaptureTypeAdmissionTest {
  @Test
  void givenRecursiveCapturedVariable_whenResolved_thenTheBoundReferenceIsAdmittedWithinItsBinder() {
    // Arrange
    var identity = new JavaTypeIdentity(JavaTypeCategory.TYPE_VARIABLE, "capture of ?", "Recursive");
    var structure = new JavaTypeStructure.Captured(0,
        new JavaTypeStructure.Declared("type:Recursive", Optional.empty(), List.of(new JavaTypeStructure.CaptureReference(0))),
        Optional.empty());

    // Act
    var resolved = new SyntaxType.Resolved(identity, structure);

    // Assert
    assertEquals(Optional.of(structure), resolved.structure());
    assertEquals(identity, resolved.identity());
  }

  @Test
  void givenUnboundCaptureReference_whenResolved_thenTheIncompleteTypeIsRejected() {
    // Arrange
    var identity = new JavaTypeIdentity(JavaTypeCategory.TYPE_VARIABLE, "capture of ?", "java.lang.Object");
    var structure = new JavaTypeStructure.CaptureReference(0);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxType.Resolved(identity, structure));

    // Assert
    assertEquals("A captured variable reference requires a preceding binder in the same type structure", failure.getMessage());
  }

  @Test
  void givenDuplicateOrSkippedCaptureBinders_whenResolved_thenTheAmbiguousTypeIsRejected() {
    // Arrange
    var identity = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Pair<?, ?>", "Pair");
    var upper = new JavaTypeStructure.Declared("type:java.lang.Object", Optional.empty(), List.of());
    var duplicate = new JavaTypeStructure.Declared("type:Pair", Optional.empty(), List.of(
        new JavaTypeStructure.Captured(0, upper, Optional.empty()), new JavaTypeStructure.Captured(0, upper, Optional.empty())));
    var skipped = new JavaTypeStructure.Declared("type:Pair", Optional.empty(), List.of(new JavaTypeStructure.Captured(1, upper, Optional.empty())));

    // Act
    var duplicateFailure = assertThrows(IllegalArgumentException.class, () -> new SyntaxType.Resolved(identity, duplicate));
    var skippedFailure = assertThrows(IllegalArgumentException.class, () -> new SyntaxType.Resolved(identity, skipped));

    // Assert
    assertEquals("Captured variable binders must have distinct first-encounter ordinals", duplicateFailure.getMessage());
    assertEquals(duplicateFailure.getMessage(), skippedFailure.getMessage());
  }

  @Test
  void givenArrayBounds_whenResolved_thenReferenceBoundsPreserveTheirPrimitiveComponents() {
    // Arrange
    var array = new JavaTypeStructure.Array(new JavaTypeStructure.Primitive(JavaTypeCategory.INT));
    var object = new JavaTypeStructure.Declared("type:java.lang.Object", Optional.empty(), List.of());
    var upperStructure = new JavaTypeStructure.Captured(0, array, Optional.empty());
    var lowerStructure = new JavaTypeStructure.Captured(0, object, Optional.of(array));
    var upperIdentity = new JavaTypeIdentity(JavaTypeCategory.TYPE_VARIABLE, "capture of ? extends int[]", "int[]");
    var lowerIdentity = new JavaTypeIdentity(JavaTypeCategory.TYPE_VARIABLE, "capture of ? super int[]", "java.lang.Object");

    // Act
    var upper = new SyntaxType.Resolved(upperIdentity, upperStructure);
    var lower = new SyntaxType.Resolved(lowerIdentity, lowerStructure);

    // Assert
    assertEquals(Optional.of(upperStructure), upper.structure());
    assertEquals(Optional.of(lowerStructure), lower.structure());
  }

  @Test
  void givenPrimitiveOrVoidBounds_whenCaptured_thenUpperAndLowerBoundsAreRejected() {
    // Arrange
    var object = new JavaTypeStructure.Declared("type:java.lang.Object", Optional.empty(), List.of());
    var primitive = new JavaTypeStructure.Primitive(JavaTypeCategory.INT);
    var noValue = new JavaTypeStructure.Primitive(JavaTypeCategory.VOID);
    var primitiveIntersection = new JavaTypeStructure.Intersection(List.of(object, primitive));

    // Act
    var primitiveUpper = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.Captured(0, primitive, Optional.empty()));
    var primitiveLower = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.Captured(0, object, Optional.of(primitive)));
    var voidUpper = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.Captured(0, noValue, Optional.empty()));
    var voidLower = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.Captured(0, object, Optional.of(noValue)));
    var intersectedPrimitive = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.Captured(0, primitiveIntersection, Optional.empty()));

    // Assert
    assertEquals("Captured variable bounds must be reference-type structures", primitiveUpper.getMessage());
    assertEquals(primitiveUpper.getMessage(), primitiveLower.getMessage());
    assertEquals(primitiveUpper.getMessage(), voidUpper.getMessage());
    assertEquals(primitiveUpper.getMessage(), voidLower.getMessage());
    assertEquals(primitiveUpper.getMessage(), intersectedPrimitive.getMessage());
  }
}
