package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JavaProgramTest {
  @Test
  void givenMutableTypeList_whenProgramIsCreated_thenLaterChangesAreNotObserved() {
    // Arrange
    var types = new ArrayList<JavaType>();
    types.add(new JavaType("type-1", "Example", JavaTypeKind.CLASS, true, List.of(), List.of()));

    // Act
    var program = new JavaProgram(types);
    types.add(new JavaType("type-2", "Later", JavaTypeKind.CLASS, true, List.of(), List.of()));

    // Assert
    assertEquals(1, program.types().size());
  }

  @Test
  void givenDuplicateTypeKeys_whenProgramIsCreated_thenInputIsRejected() {
    // Arrange
    var first = new JavaType("same", "First", JavaTypeKind.CLASS, true, List.of(), List.of());
    var second = new JavaType("same", "Second", JavaTypeKind.CLASS, true, List.of(), List.of());

    // Act
    var exception =
        assertThrows(
            IllegalArgumentException.class, () -> new JavaProgram(List.of(first, second)));

    // Assert
    assertEquals("Java type keys must be unique", exception.getMessage());
  }

  @Test
  void givenDuplicateMethodKeysAcrossTypes_whenProgramIsCreated_thenInputIsRejected() {
    // Arrange
    var firstMethod = method("same-method", "type-1");
    var secondMethod = method("same-method", "type-2");
    var first =
        new JavaType(
            "type-1", "First", JavaTypeKind.CLASS, true, List.of(firstMethod), List.of());
    var second =
        new JavaType(
            "type-2", "Second", JavaTypeKind.CLASS, true, List.of(secondMethod), List.of());

    // Act
    var exception =
        assertThrows(
            IllegalArgumentException.class, () -> new JavaProgram(List.of(first, second)));

    // Assert
    assertEquals("Java method keys must be unique across the program", exception.getMessage());
  }

  private static JavaMethod method(String methodKey, String ownerTypeKey) {
    return new JavaMethod(
        methodKey,
        ownerTypeKey,
        "calculate",
        "calculate()",
        "int",
        List.of(),
        Set.of(),
        false,
        true,
        Optional.empty(),
        List.of());
  }
}
