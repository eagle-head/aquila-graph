package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;
import org.junit.jupiter.api.Test;

class ParameterizedTypeAdmissionTest {
  @Test
  void givenInstantiatedNameInParameterizedType_whenConstructed_thenItsActualArgumentsMatchItsParent() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_OPERAND, 0);
    var argument = declared("java.lang.String", List.of());
    var type = declared("Example", List.of(argument.structure().orElseThrow()));
    var name = name(path.child(SyntaxRole.TYPE_NAME, 0), "Example", type, 0, 7);
    var writtenArgument = name(path.child(SyntaxRole.TYPE_ARGUMENT, 0), "String", argument, 8, 14);

    // Act
    var parameterized = parameterized(path, type, name, writtenArgument);

    // Assert
    assertEquals(type, parameterized.type());
    assertEquals(type, parameterized.requiredChild(SyntaxRole.TYPE_NAME).type());
    assertEquals(argument, parameterized.requiredChild(SyntaxRole.TYPE_ARGUMENT).type());
  }

  @Test
  void givenNameFromDifferentDeclaration_whenConstructed_thenTheMismatchIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_OPERAND, 0);
    var argument = declared("java.lang.String", List.of());
    var type = declared("Example", List.of(argument.structure().orElseThrow()));
    var conflicting = declared("Other", List.of(declared("java.lang.Number", List.of()).structure().orElseThrow()));
    var name = name(path.child(SyntaxRole.TYPE_NAME, 0), "Example", conflicting, 0, 7);
    var writtenArgument = name(path.child(SyntaxRole.TYPE_ARGUMENT, 0), "String", argument, 8, 14);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> parameterized(path, type, name, writtenArgument));

    // Assert
    assertEquals("Written type name conflicts with selected declaration", failure.getMessage());
  }

  @Test
  void givenNameWithDeclarationParameters_whenParameterized_thenTheParentRetainsItsOwnActualArguments() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_OPERAND, 0);
    var argument = declared("java.lang.String", List.of());
    var type = declared("Example", List.of(argument.structure().orElseThrow()));
    var nominal = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Example<T>", "Example"),
        new JavaTypeStructure.Declared("type:Example", Optional.empty(), List.of(new JavaTypeStructure.Variable("Example#T"))));
    var name = name(path.child(SyntaxRole.TYPE_NAME, 0), "Example", nominal, 0, 7);
    var writtenArgument = name(path.child(SyntaxRole.TYPE_ARGUMENT, 0), "String", argument, 8, 14);

    // Act
    var parameterized = parameterized(path, type, name, writtenArgument);

    // Assert
    assertEquals(type, parameterized.type());
    assertEquals(nominal, parameterized.requiredChild(SyntaxRole.TYPE_NAME).type());
    assertEquals(argument, parameterized.requiredChild(SyntaxRole.TYPE_ARGUMENT).type());
  }

  @Test
  void givenBareWrittenNameWithAttributedArguments_whenConstructed_thenRawTypeAdmissionStillRejectsIt() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.DECLARED_TYPE, 0);
    var type = declared("Example", List.of(declared("java.lang.String", List.of()).structure().orElseThrow()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> name(path, "Example", type, 0, 7));

    // Assert
    assertEquals("A written raw type name cannot carry attributed type arguments", failure.getMessage());
  }

  private static SyntaxNode parameterized(SyntaxStructuralPath path, SyntaxType.Resolved type, SyntaxNode name, SyntaxNode argument) {
    return SyntaxNode.source("source", path, JavaSourceKind.PARAMETERIZED_TYPE, SyntaxKind.TYPE_USE, type, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.TYPE_NAME, 0, name), new SyntaxChild(SyntaxRole.TYPE_ARGUMENT, 0, argument)),
        reference(type), new SourceSpan("source", 0, 15));
  }

  private static SyntaxNode name(SyntaxStructuralPath path, String name, SyntaxType.Resolved type, int start, int end) {
    return SyntaxNode.source("source", path, JavaSourceKind.IDENTIFIER, SyntaxKind.TYPE_USE, type, new SyntaxValue.SourceSymbol(name),
        List.of(), reference(type), new SourceSpan("source", start, end));
  }

  private static Optional<SemanticReference> reference(SyntaxType.Resolved type) {
    var declared = (JavaTypeStructure.Declared) type.structure().orElseThrow();
    return Optional.of(new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of(declared.declarationKey()),
        Optional.of(type.identity().displayName()), List.of(), List.of()));
  }

  private static SyntaxType.Resolved declared(String name, List<JavaTypeStructure> arguments) {
    var display = arguments.isEmpty() ? name : name + "<" + ((JavaTypeStructure.Declared) arguments.getFirst()).declarationKey()
        .substring("type:".length()) + ">";
    return new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.DECLARED, display, name),
        new JavaTypeStructure.Declared("type:" + name, Optional.empty(), arguments));
  }
}
