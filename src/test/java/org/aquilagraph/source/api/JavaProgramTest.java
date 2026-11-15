package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.SourceSpan;
import org.junit.jupiter.api.Test;

class JavaProgramTest {
  @Test
  void givenMutableTypeList_whenProgramIsCreated_thenLaterChangesAreNotObserved() {
    // Arrange
    var types = new ArrayList<JavaType>();
    types.add(type("type-1", "Example", List.of(), List.of()));

    // Act
    var program = new JavaProgram(types);
    types.add(type("type-2", "Later", List.of(), List.of()));

    // Assert
    assertEquals(1, program.types().size());
  }

  @Test
  void givenDuplicateTypeKeys_whenProgramIsCreated_thenInputIsRejected() {
    // Arrange
    var first = type("same", "First", List.of(), List.of());
    var second = type("same", "Second", List.of(), List.of());

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
    var first = type("type-1", "First", List.of(), List.of(firstMethod));
    var second = type("type-2", "Second", List.of(), List.of(secondMethod));

    // Act
    var exception =
        assertThrows(
            IllegalArgumentException.class, () -> new JavaProgram(List.of(first, second)));

    // Assert
    assertEquals("Java method keys must be unique across the program", exception.getMessage());
  }

  @Test
  void givenDuplicateFieldKeysAcrossTypes_whenProgramIsCreated_thenInputIsRejected() {
    // Arrange
    var firstField = field("same-field", "type-1");
    var secondField = field("same-field", "type-2");
    var first = type("type-1", "First", List.of(firstField), List.of());
    var second = type("type-2", "Second", List.of(secondField), List.of());

    // Act
    var exception =
        assertThrows(
            IllegalArgumentException.class, () -> new JavaProgram(List.of(first, second)));

    // Assert
    assertEquals("Java field keys must be unique across the program", exception.getMessage());
  }

  @Test
  void givenNestedTypeReferencesMissingProgramOwner_whenProgramIsCreated_thenInputIsRejected() {
    // Arrange
    var ownerReference = new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Missing", "Missing"),
        Optional.of("type:Missing"));
    var nested = new JavaType("type:Nested", "Nested", JavaTypeKind.CLASS, Set.of(), Optional.of(ownerReference), Optional.empty(),
        List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of(),
    new JavaTypeNesting(JavaTypeNesting.Kind.MEMBER, Optional.of("type:Missing"), Optional.empty()), new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Nested", "Nested"), "Nested");

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> new JavaProgram(List.of(nested)));

    // Assert
    assertEquals("An enclosing type must identify another declaration in the same program", exception.getMessage());
  }

  @Test
  void givenTypeParameterOwnedByAnotherType_whenTypeIsCreated_thenInputIsRejected() {
    // Arrange
    var objectBound = new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.Object", "java.lang.Object"),
        Optional.of("type:java.lang.Object"));
    var parameter = new JavaTypeParameter("type:Other#type-parameter:0", "type:Other", "T", List.of(objectBound), false,
        List.of());

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new JavaType("type:Owner", "Owner", JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
            List.of(parameter), false, List.of(), List.of(), List.of(), List.of(), List.of(),
    new JavaTypeNesting(JavaTypeNesting.Kind.TOP_LEVEL, Optional.empty(), Optional.empty()), new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Owner", "Owner"), "Owner"));

    // Assert
    assertEquals("Type parameters must be uniquely owned by their declaring type", exception.getMessage());
  }

  @Test
  void givenDuplicateOrMisownedRecordComponents_whenTypeIsCreated_thenInputIsRejected() {
    // Arrange
    var component = component("type:Record", 0);
    var foreign = component("type:Other", 0);

    // Act and assert
    assertEquals("Record components must be ordered and uniquely owned by their record",
        assertThrows(IllegalArgumentException.class, () -> recordType(List.of(component, component))).getMessage());
    assertEquals("Record components must be ordered and uniquely owned by their record",
        assertThrows(IllegalArgumentException.class, () -> recordType(List.of(foreign))).getMessage());
  }

  @Test
  void givenMissingRecordComponentRange_whenComponentIsCreated_thenInputIsRejected() {
    // Arrange and act
    var exception = assertThrows(NullPointerException.class,
        () -> new JavaRecordComponent("type:Record#record-component:0", "type:Record", 0, "value",
            new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"), Set.of(), Set.of(), false, Optional.empty(),
            Optional.empty(), Optional.empty(), null));

    // Assert
    assertEquals("origin", exception.getMessage());
  }

  @Test
  void givenFieldAndComponentShareKey_whenTypeOrProgramIsCreated_thenCollisionIsRejected() {
    // Arrange
    var component = component("type:Record", 0);
    var collidingField = field(component.componentKey(), "type:Record");
    var otherOwnerField = field(component.componentKey(), "type:Other");
    var otherType = type("type:Other", "Other", List.of(otherOwnerField), List.of());

    // Act and assert
    assertEquals("Record components must be ordered and uniquely owned by their record",
        assertThrows(IllegalArgumentException.class,
            () -> new JavaType("type:Record", "Record", JavaTypeKind.RECORD, Set.of(), Optional.empty(), Optional.empty(),
                List.of(), List.of(), List.of(), false, List.of(collidingField), List.of(component), List.of(), List.of(), List.of(),
    new JavaTypeNesting(JavaTypeNesting.Kind.TOP_LEVEL, Optional.empty(), Optional.empty()), new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Record", "Record"), "Record"))
            .getMessage());
    assertEquals("Java declaration keys must be unique across kinds and types",
        assertThrows(IllegalArgumentException.class, () -> new JavaProgram(List.of(otherType, recordType(List.of(component)))))
            .getMessage());
  }

  private static JavaType recordType(List<JavaRecordComponent> components) {
    return new JavaType("type:Record", "Record", JavaTypeKind.RECORD, Set.of(), Optional.empty(), Optional.empty(), List.of(),
        List.of(), List.of(), false, List.of(), components, List.of(), List.of(), List.of(),
    new JavaTypeNesting(JavaTypeNesting.Kind.TOP_LEVEL, Optional.empty(), Optional.empty()), new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Record", "Record"), "Record");
  }

  private static JavaRecordComponent component(String owner, int ordinal) {
    return new JavaRecordComponent(owner + "#record-component:" + ordinal, owner, ordinal, "value",
        new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"), Set.of(), Set.of(), false, Optional.empty(), Optional.empty(),
        Optional.empty(), new SourceSpan("source", 0, 1));
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
        false,
        List.of());
  }

  private static JavaType type(String typeKey, String name, List<JavaField> fields, List<JavaMethod> methods) {
    return new JavaType(typeKey, name, JavaTypeKind.CLASS, Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(),
        false, fields, List.of(), methods, List.of(), List.of(),
    new JavaTypeNesting(JavaTypeNesting.Kind.TOP_LEVEL, Optional.empty(), Optional.empty()), new JavaTypeIdentity(JavaTypeCategory.DECLARED, name, name), name);
  }

  private static JavaField field(String fieldKey, String ownerTypeKey) {
    return new JavaField(
        fieldKey,
        ownerTypeKey,
        "value",
        new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"),
        Set.of(),
        false,
        false,
        List.of());
  }
}
