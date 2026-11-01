package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EnclosingTypeStructureTest {
  private static final JavaTypeStructure.Declared OWNER = new JavaTypeStructure.Declared("Outer", Optional.empty(), List.of());
  private static final JavaTypeStructure.Variable INSTANCE = new JavaTypeStructure.Variable("Probe#O");

  @Test
  void givenIndirectInstance_whenItsOwnerViewIsPresent_thenBothStructuresRemainDistinct() {
    // Arrange
    var expected = new JavaTypeStructure.Declared("Outer.Box", Optional.of("Outer"), Optional.of(INSTANCE), List.of(), Optional.of(OWNER));

    // Act
    var resolved = resolve(expected);

    // Assert
    assertEquals(Optional.of(expected), resolved.structure());
    assertEquals(Optional.of(INSTANCE), expected.enclosingType());
    assertEquals(Optional.of(OWNER), expected.enclosingOwnerType());
  }

  @Test
  void givenIndirectInstanceWithoutOwnerView_whenConstructed_thenTheMissingOwnerIsRejected() {
    // Arrange
    var instance = Optional.<JavaTypeStructure>of(INSTANCE);

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.Declared("Outer.Box", Optional.of("Outer"), instance, List.of()));

    // Assert
    assertEquals("A declared enclosing type must itself be declared", failure.getMessage());
  }

  @Test
  void givenOwnerViewWithoutInstance_whenConstructed_thenTheDetachedViewIsRejected() {
    // Arrange
    var view = Optional.<JavaTypeStructure>of(OWNER);

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.Declared("Outer.Box", Optional.of("Outer"), Optional.empty(), List.of(), view));

    // Assert
    assertEquals("An enclosing owner view requires its actual instance type", failure.getMessage());
  }

  @Test
  void givenViewForAnotherOwner_whenConstructed_thenItsDeclarationIdentityIsRejected() {
    // Arrange
    var other = new JavaTypeStructure.Declared("Other", Optional.empty(), List.of());

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.Declared("Outer.Box", Optional.of("Outer"), Optional.of(INSTANCE), List.of(), Optional.of(other)));

    // Assert
    assertEquals("An enclosing instance type must match the lexical owner declaration", failure.getMessage());
  }

  @Test
  void givenDirectInstanceWithConflictingView_whenConstructed_thenTheViewCannotOverrideTheInstance() {
    // Arrange
    var other = new JavaTypeStructure.Declared("Other", Optional.empty(), List.of());

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.Declared("Outer.Box", Optional.of("Outer"), Optional.of(other), List.of(), Optional.of(OWNER)));

    // Assert
    assertEquals("Only an indirect enclosing instance requires a separate owner view", failure.getMessage());
  }

  @Test
  void givenDuplicateOriginalCaptureBinders_whenResolved_thenTheInvalidInstanceIsRejected() {
    // Arrange
    var upper = new JavaTypeStructure.Declared("Object", Optional.empty(), List.of());
    var capturedArgument = new JavaTypeStructure.Captured(0, upper, Optional.empty());
    var view = new JavaTypeStructure.Declared("Outer", Optional.empty(), List.of(capturedArgument));
    var instance = new JavaTypeStructure.Captured(0, view, Optional.empty());
    var structure = new JavaTypeStructure.Declared("Outer.Box", Optional.of("Outer"), Optional.of(instance), List.of(), Optional.of(view));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> resolve(structure));

    // Assert
    assertEquals("Captured variable binders must have distinct first-encounter ordinals", failure.getMessage());
  }

  @Test
  void givenPrimitiveOrArrayBounds_whenUsedAsAnIndirectEnclosingInstance_thenTheNonclassBoundsAreRejected() {
    // Arrange
    var primitive = new JavaTypeStructure.Primitive(JavaTypeCategory.INT);
    var array = new JavaTypeStructure.Array(primitive);
    var invalid = List.of(new JavaTypeStructure.Intersection(List.of(OWNER, primitive)),
        new JavaTypeStructure.Intersection(List.of(OWNER, array)), new JavaTypeStructure.Captured(0, array, Optional.empty()));

    for (var instance : invalid) {
      // Act
      var failure = assertThrows(IllegalArgumentException.class,
          () -> new JavaTypeStructure.Declared("Outer.Box", Optional.of("Outer"), Optional.of(instance), List.of(), Optional.of(OWNER)));

      // Assert
      assertEquals("An indirect enclosing instance requires class-type bounds", failure.getMessage());
    }
  }

  @Test
  void givenIndependentCapturedOwnerView_whenResolved_thenItsCaptureScopeStartsAtItsOwnBinder() {
    // Arrange
    var upper = new JavaTypeStructure.Declared("Object", Optional.empty(), List.of());
    var actualOwner = new JavaTypeStructure.Declared("Outer", Optional.empty(),
        List.of(new JavaTypeStructure.Captured(1, upper, Optional.empty())));
    var view = new JavaTypeStructure.Declared("Outer", Optional.empty(),
        List.of(new JavaTypeStructure.Captured(0, upper, Optional.empty())));
    var instance = new JavaTypeStructure.Captured(0, actualOwner, Optional.empty());
    var structure = new JavaTypeStructure.Declared("Outer.Box", Optional.of("Outer"), Optional.of(instance), List.of(), Optional.of(view));

    // Act
    var resolved = resolve(structure);

    // Assert
    assertEquals(Optional.of(structure), resolved.structure());
  }

  @Test
  void givenOwnerViewWithExternalCaptureReference_whenResolved_thenTheUnboundViewIsRejected() {
    // Arrange
    var instance = new JavaTypeStructure.Captured(0, OWNER, Optional.empty());
    var view = new JavaTypeStructure.Declared("Outer", Optional.empty(), List.of(new JavaTypeStructure.CaptureReference(0)));
    var structure = new JavaTypeStructure.Declared("Outer.Box", Optional.of("Outer"), Optional.of(instance), List.of(), Optional.of(view));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> resolve(structure));

    // Assert
    assertEquals("A captured variable reference requires a preceding binder in the same type structure", failure.getMessage());
  }

  private static SyntaxType.Resolved resolve(JavaTypeStructure structure) {
    return new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "O.Box", "Outer.Box"), structure);
  }
}
