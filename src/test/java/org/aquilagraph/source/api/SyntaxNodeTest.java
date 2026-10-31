package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.cfg.internal.CfgReadiness;
import org.aquilagraph.identity.api.SourceSpan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.aquilagraph.testsupport.StatementBranchFixture;

class SyntaxNodeTest {
  private static final SourceSpan ORIGIN = new SourceSpan("source", 0, 1);
  private static final JavaTypeIdentity INT_TYPE = new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int");
  private static final JavaTypeIdentity DECLARED_TYPE = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Example", "Example");

  @Test
  void givenResolvedPrimitiveWithoutRecursiveFacts_whenCreated_thenLeafTypeUseIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.PRIMITIVE_TYPE, SyntaxKind.TYPE_USE, new SyntaxType.Resolved(INT_TYPE),
        new SyntaxValue.PrimitiveType(JavaTypeCategory.INT), List.of(), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("recursive attributed structure"));
  }

  @Test
  void givenCompositeTypeWithoutRecursiveFacts_whenResolvedOutcomeIsCreated_thenConstructionIsRejected() {
    // Arrange
    var categories = List.of(JavaTypeCategory.WILDCARD, JavaTypeCategory.INTERSECTION, JavaTypeCategory.UNION);

    // Act
    var failures = categories.stream().map(category -> assertThrows(IllegalArgumentException.class,
        () -> new SyntaxType.Resolved(new JavaTypeIdentity(category, "compound", "Base")))).toList();

    // Assert
    assertTrue(failures.stream().allMatch(failure -> failure.getMessage().contains("requires recursive structure")));
  }

  @Test
  void givenResolvedTypeWithConflictingCategory_whenCreated_thenConstructionIsRejected() {
    // Arrange
    var identity = new JavaTypeIdentity(JavaTypeCategory.ARRAY, "Example[]", "Example[]");
    var structure = new JavaTypeStructure.Declared("type:Example", Optional.empty(), List.of());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxType.Resolved(identity, structure));

    // Assert
    assertTrue(failure.getMessage().contains("category conflicts"));
  }

  @Test
  void givenLocalDeclaredTypeWithoutLexicalOwner_whenCreated_thenConstructionIsRejected() {
    // Arrange
    var owner = "";

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.LocalDeclared("type:Local", owner, JavaTypeStructure.LocalDeclared.Kind.LOCAL,
            Optional.empty(), List.of()));

    // Assert
    assertTrue(failure.getMessage().contains("lexical owner keys"));
  }

  @Test
  void givenWildcardBoundCardinalityConflict_whenCreated_thenInvalidStateIsRejected() {
    // Arrange
    var number = new JavaTypeStructure.Declared("type:java.lang.Number", Optional.empty(), List.of());

    // Act
    var missing = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.EXTENDS, Optional.empty()));
    var invented = assertThrows(IllegalArgumentException.class,
        () -> new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED, Optional.of(number)));

    // Assert
    assertTrue(missing.getMessage().contains("match its written form"));
    assertTrue(invented.getMessage().contains("match its written form"));
  }

  @Test
  void givenWildcardWithDifferentWrittenAndAttributedBounds_whenCreated_thenMismatchIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var written = declaredUse(path.child(SyntaxRole.UPPER_BOUND, 0), "Number", "type:java.lang.Number");
    var other = new JavaTypeStructure.Declared("type:java.lang.Integer", Optional.empty(), List.of());
    var wildcard = new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.EXTENDS, Optional.of(other));
    var type = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.WILDCARD, "? extends Integer", "Number"), wildcard);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.EXTENDS_WILDCARD, SyntaxKind.TYPE_USE, type, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.UPPER_BOUND, 0, written)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("wildcard bound conflicts"));
  }

  @Test
  void givenWildcardBoundOutsideItsSourceRange_whenCreated_thenDisplacedOriginIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var bound = declaredUse(path.child(SyntaxRole.UPPER_BOUND, 0), "Number", "type:java.lang.Number",
        new SourceSpan("source", 20, 26));
    var wildcard = new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.EXTENDS,
        Optional.of(structure(bound)));
    var type = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.WILDCARD, "? extends Number", "Number"), wildcard);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.EXTENDS_WILDCARD, SyntaxKind.TYPE_USE, type, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.UPPER_BOUND, 0, bound)), Optional.empty(), new SourceSpan("source", 0, 18)));

    // Assert
    assertTrue(failure.getMessage().contains("contained and ordered"));
  }

  @Test
  void givenIntersectionBoundsInDifferentAttributedOrder_whenCreated_thenMismatchIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var first = declaredUse(path.child(SyntaxRole.INTERSECTION_BOUND, 0), "Number", "type:java.lang.Number",
        new SourceSpan("source", 0, 6));
    var second = declaredUse(path.child(SyntaxRole.INTERSECTION_BOUND, 1), "Serializable", "type:java.io.Serializable",
        new SourceSpan("source", 9, 21));
    var attributed = new JavaTypeStructure.Intersection(List.of(structure(second), structure(first)));
    var type = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INTERSECTION, "Number & Serializable", "Number"),
        attributed);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.INTERSECTION_TYPE, SyntaxKind.TYPE_USE, type, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.INTERSECTION_BOUND, 0, first),
            new SyntaxChild(SyntaxRole.INTERSECTION_BOUND, 1, second)), Optional.empty(), new SourceSpan("source", 0, 21)));

    // Assert
    assertTrue(failure.getMessage().contains("intersection bounds conflict"));
  }

  @Test
  void givenDuplicateUnionAlternatives_whenCreated_thenDuplicateIsRejected() {
    // Arrange
    var exception = new JavaTypeStructure.Declared("type:java.lang.IllegalArgumentException", Optional.empty(), List.of());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new JavaTypeStructure.Union(List.of(exception, exception)));

    // Assert
    assertTrue(failure.getMessage().contains("distinct alternatives"));
  }

  @Test
  void givenUnionAlternativesWithReversedSourcePositions_whenCreated_thenSourceOrderIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var first = declaredUse(path.child(SyntaxRole.UNION_ALTERNATIVE, 0), "First", "type:First",
        new SourceSpan("source", 18, 23));
    var second = declaredUse(path.child(SyntaxRole.UNION_ALTERNATIVE, 1), "Second", "type:Second",
        new SourceSpan("source", 2, 8));
    var type = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.UNION, "First | Second", "First"),
        new JavaTypeStructure.Union(List.of(structure(first), structure(second))));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.UNION_TYPE, SyntaxKind.TYPE_USE, type, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.UNION_ALTERNATIVE, 0, first),
            new SyntaxChild(SyntaxRole.UNION_ALTERNATIVE, 1, second)), Optional.empty(), new SourceSpan("source", 0, 25)));

    // Assert
    assertTrue(failure.getMessage().contains("contained and ordered"));
  }

  @Test
  void givenTypeParameterWithoutWrittenBoundButNonObjectUpperBound_whenCreated_thenFalseImplicitBoundIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.TYPE_PARAMETER, 0);
    var number = new JavaTypeStructure.Declared("type:java.lang.Number", Optional.empty(), List.of());
    var value = new SyntaxValue.TypeParameterDeclaration("T", "owner", 0, 0, 0, number);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.TYPE_PARAMETER, SyntaxKind.TYPE_PARAMETER_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        value, List.of(), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("implicit Object"));
  }

  @Test
  void givenWildcardWithForeignSourceRange_whenCreated_thenOwnerIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var wildcard = new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED, Optional.empty());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.UNBOUNDED_WILDCARD, SyntaxKind.TYPE_USE,
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.WILDCARD, "?", "java.lang.Object"), wildcard),
        SyntaxValue.None.INSTANCE, List.of(), Optional.empty(), new SourceSpan("other", 0, 1)));

    // Assert
    assertTrue(failure.getMessage().contains("belong to the node's source file"));
  }

  @Test
  void givenArrayChildWithDifferentAttributedComponent_whenCreated_thenMismatchIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var component = SyntaxNode.source("source", path.child(SyntaxRole.COMPONENT_TYPE, 0), JavaSourceKind.PRIMITIVE_TYPE,
        SyntaxKind.TYPE_USE, new SyntaxType.Resolved(INT_TYPE, new JavaTypeStructure.Primitive(JavaTypeCategory.INT)),
        new SyntaxValue.PrimitiveType(JavaTypeCategory.INT), List.of(), Optional.empty(), ORIGIN);
    var attributedArray = new JavaTypeIdentity(JavaTypeCategory.ARRAY, "long[]", "long[]");

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.ARRAY_TYPE, SyntaxKind.TYPE_USE,
        new SyntaxType.Resolved(attributedArray, new JavaTypeStructure.Array(new JavaTypeStructure.Primitive(JavaTypeCategory.LONG))),
        SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.COMPONENT_TYPE, 0, component)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("component conflicts"));
  }

  @Test
  void givenParameterizedArgumentsInWrongAttributedOrder_whenCreated_thenMismatchIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var name = declaredUse(path.child(SyntaxRole.TYPE_NAME, 0), "Map", "type:java.util.Map");
    var first = declaredUse(path.child(SyntaxRole.TYPE_ARGUMENT, 0), "String", "type:java.lang.String");
    var second = declaredUse(path.child(SyntaxRole.TYPE_ARGUMENT, 1), "Integer", "type:java.lang.Integer");
    var firstStructure = new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of());
    var secondStructure = new JavaTypeStructure.Declared("type:java.lang.Integer", Optional.empty(), List.of());
    var wrongOrder = new JavaTypeStructure.Declared("type:java.util.Map", Optional.empty(),
        List.of(secondStructure, firstStructure));
    var children = List.of(new SyntaxChild(SyntaxRole.TYPE_NAME, 0, name),
        new SyntaxChild(SyntaxRole.TYPE_ARGUMENT, 0, first), new SyntaxChild(SyntaxRole.TYPE_ARGUMENT, 1, second));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.PARAMETERIZED_TYPE, SyntaxKind.TYPE_USE,
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Map<String, Integer>", "Map"), wrongOrder),
        SyntaxValue.None.INSTANCE, children, Optional.of(reference(ReferenceKind.TYPE, "type:java.util.Map")), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("arguments conflict"));
  }

  @Test
  void givenRawIdentifierWithAttributedTypeArgument_whenCreated_thenFabricatedArgumentIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var key = "type:java.util.List";
    var argument = new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of());
    var type = new SyntaxType.Resolved(DECLARED_TYPE,
        new JavaTypeStructure.Declared(key, Optional.empty(), List.of(argument)));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.IDENTIFIER, SyntaxKind.TYPE_USE, type, new SyntaxValue.SourceSymbol("List"), List.of(),
        Optional.of(reference(ReferenceKind.TYPE, key)), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("raw type name"));
  }

  @Test
  void givenRawMemberSelectWithAttributedTypeArgument_whenCreated_thenFabricatedArgumentIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var qualifier = SyntaxNode.source("source", path.child(SyntaxRole.TYPE_QUALIFIER, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.TYPE_USE, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.PackageQualifier("pkg"), List.of(),
        Optional.empty(), ORIGIN);
    var key = "type:pkg.List";
    var argument = new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of());
    var type = new SyntaxType.Resolved(DECLARED_TYPE,
        new JavaTypeStructure.Declared(key, Optional.empty(), List.of(argument)));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.MEMBER_SELECT, SyntaxKind.TYPE_USE, type, new SyntaxValue.SourceSymbol("List"),
        List.of(new SyntaxChild(SyntaxRole.TYPE_QUALIFIER, 0, qualifier)), Optional.of(reference(ReferenceKind.TYPE, key)), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("raw type name"));
  }

  @Test
  void givenPackageQualifiedTypeWithAttributedEnclosingInstance_whenCreated_thenFabricatedOwnerIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var qualifier = SyntaxNode.source("source", path.child(SyntaxRole.TYPE_QUALIFIER, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.TYPE_USE, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.PackageQualifier("pkg"), List.of(),
        Optional.empty(), ORIGIN);
    var key = "type:pkg.List";
    var enclosing = new JavaTypeStructure.Declared("type:Outer", Optional.empty(), List.of());
    var type = new SyntaxType.Resolved(DECLARED_TYPE,
        new JavaTypeStructure.Declared(key, Optional.of(enclosing), List.of()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.MEMBER_SELECT, SyntaxKind.TYPE_USE, type, new SyntaxValue.SourceSymbol("List"),
        List.of(new SyntaxChild(SyntaxRole.TYPE_QUALIFIER, 0, qualifier)), Optional.of(reference(ReferenceKind.TYPE, key)), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("package-qualified type"));
  }

  @Test
  void givenPackageQualifierInFieldTypeSlot_whenCreated_thenUnattributedLeafIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.IDENTIFIER, SyntaxKind.TYPE_USE, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.PackageQualifier("pkg"), List.of(), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("only a type qualifier"));
  }

  @Test
  void givenInheritedMemberUse_whenCreated_thenWrittenQualifierAndDeclaringOwnerRemainDistinct() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var qualifier = declaredUse(path.child(SyntaxRole.TYPE_QUALIFIER, 0), "Derived", "type:Derived");
    var key = "type:Base.Member";
    var structure = new JavaTypeStructure.Declared(key, Optional.of("type:Base"), Optional.empty(), List.of());
    var type = new SyntaxType.Resolved(DECLARED_TYPE, structure);

    // Act
    var member = SyntaxNode.source("source", path, JavaSourceKind.MEMBER_SELECT, SyntaxKind.TYPE_USE, type,
        new SyntaxValue.SourceSymbol("Member"), List.of(new SyntaxChild(SyntaxRole.TYPE_QUALIFIER, 0, qualifier)),
        Optional.of(reference(ReferenceKind.TYPE, key)), ORIGIN);

    // Assert
    assertEquals(Optional.of("type:Base"), ((JavaTypeStructure.Declared) ((SyntaxType.Resolved) member.type()).structure()
        .orElseThrow()).lexicalOwnerKey());
    assertEquals(Optional.of("type:Derived"), member.requiredChild(SyntaxRole.TYPE_QUALIFIER).requiredReference().symbolKey());
  }

  @Test
  void givenMemberSelectionAttributedToTypeVariable_whenCreated_thenNonMemberTargetIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var qualifier = declaredUse(path.child(SyntaxRole.TYPE_QUALIFIER, 0), "Owner", "type:Owner");
    var parameterKey = "type:Owner#type-parameter:0";
    var variableType = new JavaTypeIdentity(JavaTypeCategory.TYPE_VARIABLE, "T", "T");

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.MEMBER_SELECT, SyntaxKind.TYPE_USE,
        new SyntaxType.Resolved(variableType, new JavaTypeStructure.Variable(parameterKey)),
        new SyntaxValue.SourceSymbol("T"), List.of(new SyntaxChild(SyntaxRole.TYPE_QUALIFIER, 0, qualifier)),
        Optional.of(reference(ReferenceKind.TYPE_PARAMETER, parameterKey)), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("kind conflicts"));
  }

  @Test
  void givenNestedParameterizedNameWithDifferentAttributedEnclosingArguments_whenCreated_thenMismatchIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var outerKey = "type:Outer";
    var innerKey = "type:Outer.Inner";
    var stringKey = "type:java.lang.String";
    var integerKey = "type:java.lang.Integer";
    var numberKey = "type:java.lang.Number";
    var stringType = new JavaTypeStructure.Declared(stringKey, Optional.empty(), List.of());
    var integerType = new JavaTypeStructure.Declared(integerKey, Optional.empty(), List.of());
    var outerName = declaredUse(path.child(SyntaxRole.TYPE_NAME, 0).child(SyntaxRole.TYPE_QUALIFIER, 0)
        .child(SyntaxRole.TYPE_NAME, 0), "Outer", outerKey);
    var outerArgument = declaredUse(path.child(SyntaxRole.TYPE_NAME, 0).child(SyntaxRole.TYPE_QUALIFIER, 0)
        .child(SyntaxRole.TYPE_ARGUMENT, 0), "String", stringKey);
    var outerPath = path.child(SyntaxRole.TYPE_NAME, 0).child(SyntaxRole.TYPE_QUALIFIER, 0);
    var writtenOuter = SyntaxNode.source("source", outerPath, JavaSourceKind.PARAMETERIZED_TYPE, SyntaxKind.TYPE_USE,
        new SyntaxType.Resolved(DECLARED_TYPE, new JavaTypeStructure.Declared(outerKey, Optional.empty(), List.of(stringType))),
        SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.TYPE_NAME, 0, outerName),
            new SyntaxChild(SyntaxRole.TYPE_ARGUMENT, 0, outerArgument)), Optional.of(reference(ReferenceKind.TYPE, outerKey)), ORIGIN);
    var selectedName = SyntaxNode.source("source", path.child(SyntaxRole.TYPE_NAME, 0), JavaSourceKind.MEMBER_SELECT,
        SyntaxKind.TYPE_USE, new SyntaxType.Resolved(DECLARED_TYPE,
            new JavaTypeStructure.Declared(innerKey,
                Optional.of(new JavaTypeStructure.Declared(outerKey, Optional.empty(), List.of())), List.of())),
        new SyntaxValue.SourceSymbol("Inner"), List.of(new SyntaxChild(SyntaxRole.TYPE_QUALIFIER, 0, writtenOuter)),
        Optional.of(reference(ReferenceKind.TYPE, innerKey)), ORIGIN);
    var innerArgument = declaredUse(path.child(SyntaxRole.TYPE_ARGUMENT, 0), "Integer", integerKey);
    var conflicting = new JavaTypeStructure.Declared(innerKey,
        Optional.of(new JavaTypeStructure.Declared(outerKey, Optional.empty(),
            List.of(new JavaTypeStructure.Declared(numberKey, Optional.empty(), List.of())))), List.of(integerType));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.PARAMETERIZED_TYPE, SyntaxKind.TYPE_USE, new SyntaxType.Resolved(DECLARED_TYPE, conflicting),
        SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.TYPE_NAME, 0, selectedName),
            new SyntaxChild(SyntaxRole.TYPE_ARGUMENT, 0, innerArgument)), Optional.of(reference(ReferenceKind.TYPE, innerKey)), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("qualifier conflicts"));
  }

  @Test
  void givenUnresolvedReferenceWithMatchingDeclarationKey_whenCreated_thenTargetIsRejected() {
    // Arrange
    var status = ResolutionStatus.UNRESOLVED;

    // Act
    var failure = matchingKeyWithInvalidStatusFailure(status);

    // Assert
    assertTrue(failure.getMessage().contains("compiler-selected declaration target"));
  }

  @Test
  void givenErroneousReferenceWithMatchingDeclarationKey_whenCreated_thenTargetIsRejected() {
    // Arrange
    var status = ResolutionStatus.ERROR;

    // Act
    var failure = matchingKeyWithInvalidStatusFailure(status);

    // Assert
    assertTrue(failure.getMessage().contains("compiler-selected declaration target"));
  }

  private static IllegalArgumentException matchingKeyWithInvalidStatusFailure(ResolutionStatus status) {
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var key = "type:Example";
    var type = new SyntaxType.Resolved(DECLARED_TYPE, new JavaTypeStructure.Declared(key, Optional.empty(), List.of()));
    var target = new SemanticReference(ReferenceKind.TYPE, status, Optional.of(key), Optional.empty(), List.of(), List.of());

    return assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.IDENTIFIER, SyntaxKind.TYPE_USE, type, new SyntaxValue.SourceSymbol("Example"), List.of(),
        Optional.of(target), ORIGIN));
  }

  @Test
  void givenTypeVariableWithAnotherParameterTarget_whenCreated_thenWrongOwnerIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.TYPE_PARAMETER, 0).child(SyntaxRole.TYPE_BOUND, 0);
    var type = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.TYPE_VARIABLE, "T", "java.lang.Object"),
        new JavaTypeStructure.Variable("parameter:T"));
    var wrongReference = new SemanticReference(ReferenceKind.TYPE_PARAMETER, ResolutionStatus.RESOLVED,
        Optional.of("parameter:U"), Optional.empty(), List.of(), List.of());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.IDENTIFIER, SyntaxKind.TYPE_USE, type, new SyntaxValue.SourceSymbol("T"), List.of(),
        Optional.of(wrongReference), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("compiler-selected declaration target"));
  }

  @Test
  void givenDeclaredUseWithAnotherDeclarationTarget_whenCreated_thenWrongKeyIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var type = new SyntaxType.Resolved(DECLARED_TYPE,
        new JavaTypeStructure.Declared("type:Example", Optional.empty(), List.of()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.IDENTIFIER, SyntaxKind.TYPE_USE, type, new SyntaxValue.SourceSymbol("Example"), List.of(),
        Optional.of(reference(ReferenceKind.TYPE, "type:Other")), ORIGIN));

    // Assert
    assertTrue(failure.getMessage().contains("compiler-selected declaration target"));
  }

  @Test
  void givenTypeHeaderShapeWithoutSuperclass_whenCreated_thenMissingAttachmentIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var value = new SyntaxValue.TypeDeclaration("Example", "owner", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, true, 0, 0, 0, 0));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value, List.of(), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("SUPERCLASS"));
  }

  @Test
  void givenFieldTypeAttachmentContradictingDeclaration_whenCreated_thenMismatchIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0);
    var type = declaredUse(path.child(SyntaxRole.DECLARED_TYPE, 0), "String", "type:java.lang.String", ORIGIN);
    var field = new SyntaxValue.VariableDeclaration("value", "type:Host", "type:Host#field:value",
        SyntaxValue.VariableDeclaration.Kind.FIELD, Set.of(), Set.of(), INT_TYPE, 0, true, false, false, 0, false, List.of());

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.VARIABLE,
        SyntaxKind.FIELD_DECLARATION, SyntaxType.NotApplicable.INSTANCE, field,
        List.of(new SyntaxChild(SyntaxRole.DECLARED_TYPE, 0, type)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("conflicts with its declaration type"));
  }

  @Test
  void givenRecordComponentTypeAttachmentContradictingDeclaration_whenCreated_thenMismatchIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.RECORD_COMPONENT, 0);
    var type = declaredUse(path.child(SyntaxRole.DECLARED_TYPE, 0), "String", "type:java.lang.String", ORIGIN);
    var component = new SyntaxValue.RecordComponentDeclaration("value", "type:Host", 0, Set.of(), Set.of(), INT_TYPE, 0, false,
        new SyntaxValue.RecordComponentDeclaration.Counterparts("type:Host#value", "type:Host#value()", "type:Host#<init>(int)#value",
            true, true));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.VARIABLE,
        SyntaxKind.RECORD_COMPONENT_DECLARATION, SyntaxType.NotApplicable.INSTANCE, component,
        List.of(new SyntaxChild(SyntaxRole.DECLARED_TYPE, 0, type)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("conflicts with its declaration type"));
  }

  @Test
  void givenThrownTypeAttachmentsSwappedAgainstAttribution_whenCreated_thenOrderIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0);
    var first = declaredUse(path.child(SyntaxRole.THROWN_TYPE, 0), "IOException", "type:java.io.IOException", ORIGIN);
    var second = declaredUse(path.child(SyntaxRole.THROWN_TYPE, 1), "SQLException", "type:java.sql.SQLException", ORIGIN);
    var returnType = SyntaxNode.source("source", path.child(SyntaxRole.RETURN_TYPE, 0), JavaSourceKind.PRIMITIVE_TYPE,
        SyntaxKind.TYPE_USE, SyntaxType.Unresolved.INSTANCE, new SyntaxValue.PrimitiveType(JavaTypeCategory.VOID), List.of(),
        Optional.empty(), ORIGIN);
    var declaration = new SyntaxValue.ExecutableDeclaration("run", "type:Host", "type:Host#run()",
        SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(),
        new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, true, 2, false, false),
        SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(
            receiverType("type:java.sql.SQLException", "SQLException"), receiverType("type:java.io.IOException", "IOException")), Optional.empty()));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.METHOD,
        SyntaxKind.METHOD_DECLARATION, SyntaxType.NotApplicable.INSTANCE, declaration,
        List.of(new SyntaxChild(SyntaxRole.RETURN_TYPE, 0, returnType), new SyntaxChild(SyntaxRole.THROWN_TYPE, 0, first),
            new SyntaxChild(SyntaxRole.THROWN_TYPE, 1, second)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenTypeHeaderRoleWithAnnotationChild_whenCreated_thenWrongAttachmentKindIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var annotation = SyntaxNode.source("source", path.child(SyntaxRole.INTERFACE, 0), JavaSourceKind.ANNOTATION,
        SyntaxKind.ANNOTATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.MARKER, List.of(), SyntaxValue.AnnotationLocation.UNRESOLVED),
        List.of(), Optional.of(new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED,
            Optional.empty(), Optional.empty(), List.of(), List.of())), ORIGIN);
    var value = new SyntaxValue.TypeDeclaration("Example", "owner", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 1, 0, 0, 0));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        List.of(new SyntaxChild(SyntaxRole.INTERFACE, 0, annotation)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenTypeHeaderRoleWithBlockBoundary_whenCreated_thenNonTypeAttachmentIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var block = SyntaxNode.source("source", path.child(SyntaxRole.SUPERCLASS, 0), JavaSourceKind.BLOCK,
        SyntaxKind.UNSUPPORTED, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported(JavaSourceKind.BLOCK.stableId()),
        List.of(), Optional.empty(), ORIGIN);
    var value = new SyntaxValue.TypeDeclaration("Example", "owner", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, true, 0, 0, 0, 0));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        List.of(new SyntaxChild(SyntaxRole.SUPERCLASS, 0, block)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenGenericUnsupportedTypeUse_whenAttachedToTypeHeader_thenAttachmentIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var superclass = SyntaxNode.source("source", path.child(SyntaxRole.SUPERCLASS, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.UNSUPPORTED, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported("IDENTIFIER"), List.of(),
        Optional.empty(), ORIGIN);
    var value = new SyntaxValue.TypeDeclaration("Example", "owner", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, true, 0, 0, 0, 0));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        List.of(new SyntaxChild(SyntaxRole.SUPERCLASS, 0, superclass)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenGenericSourceSymbolForTypeParameter_whenCreated_thenDeclarationValueIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.TYPE_PARAMETER, 0);

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.TYPE_PARAMETER, SyntaxKind.TYPE_PARAMETER_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.SourceSymbol("T"), List.of(), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("TypeParameterDeclaration"));
  }

  @Test
  void givenDuplicateTypeParameterOrdinal_whenAttachedToType_thenOwnerRejectsSecondPosition() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var first = SyntaxNode.source("source", path.child(SyntaxRole.TYPE_PARAMETER, 0), JavaSourceKind.TYPE_PARAMETER,
        SyntaxKind.TYPE_PARAMETER_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.TypeParameterDeclaration("T", "owner", 0, 0, 0, implicitObjectBound()), List.of(), Optional.empty(), ORIGIN);
    var second = SyntaxNode.source("source", path.child(SyntaxRole.TYPE_PARAMETER, 1), JavaSourceKind.TYPE_PARAMETER,
        SyntaxKind.TYPE_PARAMETER_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.TypeParameterDeclaration("U", "owner", 0, 0, 0, implicitObjectBound()), List.of(), Optional.empty(), ORIGIN);
    var value = new SyntaxValue.TypeDeclaration("Example", "owner", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 2, false, 0, 0, 0, 0));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        List.of(new SyntaxChild(SyntaxRole.TYPE_PARAMETER, 0, first), new SyntaxChild(SyntaxRole.TYPE_PARAMETER, 1, second)),
        Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenTypeParameterWithDifferentOwner_whenAttachedToType_thenOwnerMismatchIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var parameter = SyntaxNode.source("source", path.child(SyntaxRole.TYPE_PARAMETER, 0), JavaSourceKind.TYPE_PARAMETER,
        SyntaxKind.TYPE_PARAMETER_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.TypeParameterDeclaration("T", "other", 0, 0, 0, implicitObjectBound()), List.of(), Optional.empty(), ORIGIN);
    var value = new SyntaxValue.TypeDeclaration("Example", "owner", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 1, false, 0, 0, 0, 0));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        List.of(new SyntaxChild(SyntaxRole.TYPE_PARAMETER, 0, parameter)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenConstructorWithReturnAttachment_whenCreated_thenInvalidHeaderIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0);
    var returnType = SyntaxNode.source("source", path.child(SyntaxRole.RETURN_TYPE, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.TYPE_USE, SyntaxType.Unresolved.INSTANCE, new SyntaxValue.SourceSymbol("Example"), List.of(), Optional.empty(), ORIGIN);
    var value = new SyntaxValue.ExecutableDeclaration("Example", "owner", "owner#Example()",
        SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR, Set.of(), Set.of(),
        new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, false, 0, true, false),
        SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty()));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.METHOD,
        SyntaxKind.METHOD_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        List.of(new SyntaxChild(SyntaxRole.RETURN_TYPE, 0, returnType)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("RETURN_TYPE"));
  }

  @Test
  void givenMethodWithoutReturnType_whenCreatingDeclarationValue_thenInvalidHeaderIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, false, 0, true, false);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("method", "owner", "owner#method()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(), shape,
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty())));

    // Assert
    assertTrue(exception.getMessage().contains("return-type"));
  }

  @Test
  void givenConstructorWithoutBody_whenCreatingDeclarationValue_thenInvalidHeaderIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, false, 0, false, false);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("Example", "owner", "owner#Example()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR, Set.of(), Set.of(), shape,
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty())));

    // Assert
    assertTrue(exception.getMessage().contains("requires a body"));
  }

  @Test
  void givenOrdinaryMethodWithAnnotationDefault_whenCreatingDeclarationValue_thenInvalidHeaderIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, true, 0, false, false);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("method", "owner", "owner#method()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(), shape,
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.of(new AnnotationScalar.IntValue(7)))));

    // Assert
    assertTrue(exception.getMessage().contains("annotation element"));
  }

  @Test
  void givenCompactConstructorWithThrows_whenCreatingDeclarationValue_thenInvalidHeaderIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, false, 1, true, false);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("Record", "owner", "owner#Record()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR, Set.of(), Set.of(), shape,
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(
                new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Exception", "Exception"))), Optional.empty())));

    // Assert
    assertTrue(exception.getMessage().contains("compact constructor"));
  }

  @Test
  void givenCompactConstructorUnderClass_whenCreated_thenOwnerRejectsExecutableForm() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var memberPath = path.child(SyntaxRole.MEMBER, 0);
    var body = SyntaxNode.source("source", memberPath.child(SyntaxRole.BODY, 0), JavaSourceKind.BLOCK, SyntaxKind.BLOCK,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(), ORIGIN);
    var executable = SyntaxNode.source("source", memberPath, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.ExecutableDeclaration("Example", "owner", "owner#Example()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR, Set.of(), Set.of(),
            new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, false, 0, true, false),
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty())),
        List.of(new SyntaxChild(SyntaxRole.BODY, 0, body)), Optional.empty(), ORIGIN);
    var owner = new SyntaxValue.TypeDeclaration("Example", "owner", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 1));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, owner,
        List.of(new SyntaxChild(SyntaxRole.MEMBER, 0, executable)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenAnnotationElementUnderClass_whenCreated_thenOwnerRejectsExecutableForm() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var memberPath = path.child(SyntaxRole.MEMBER, 0);
    var returnType = SyntaxNode.source("source", memberPath.child(SyntaxRole.RETURN_TYPE, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.TYPE_USE, SyntaxType.Unresolved.INSTANCE, new SyntaxValue.SourceSymbol("Example"), List.of(), Optional.empty(), ORIGIN);
    var executable = SyntaxNode.source("source", memberPath, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.ExecutableDeclaration("value", "owner", "owner#value()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.ANNOTATION_ELEMENT, Set.of(), Set.of(),
            new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, true, 0, false, false),
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty())),
        List.of(new SyntaxChild(SyntaxRole.RETURN_TYPE, 0, returnType)), Optional.empty(), ORIGIN);
    var owner = new SyntaxValue.TypeDeclaration("Example", "owner", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 1));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, owner,
        List.of(new SyntaxChild(SyntaxRole.MEMBER, 0, executable)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenExecutableKeyOutsideDeclaredOwner_whenCreatingValue_thenCanonicalOwnerIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, true, 0, false, false);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("run", "type:First", "type:Second#run()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(), shape,
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty())));

    // Assert
    assertTrue(exception.getMessage().contains("type owner"));
  }

  @Test
  void givenMethodOwnedByAnotherType_whenAttachedToType_thenParentRejectsExecutable() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var memberPath = path.child(SyntaxRole.MEMBER, 0);
    var returnType = SyntaxNode.source("source", memberPath.child(SyntaxRole.RETURN_TYPE, 0), JavaSourceKind.PRIMITIVE_TYPE,
        SyntaxKind.TYPE_USE, SyntaxType.Unresolved.INSTANCE, new SyntaxValue.PrimitiveType(JavaTypeCategory.INT), List.of(), Optional.empty(), ORIGIN);
    var method = SyntaxNode.source("source", memberPath, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.ExecutableDeclaration("run", "type:Second", "type:Second#run()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(),
            new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, true, 0, false, false),
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty())),
        List.of(new SyntaxChild(SyntaxRole.RETURN_TYPE, 0, returnType)), Optional.empty(), ORIGIN);
    var owner = new SyntaxValue.TypeDeclaration("First", "type:First", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 1));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, owner,
        List.of(new SyntaxChild(SyntaxRole.MEMBER, 0, method)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenTypeParameterOnEnumOrAnnotation_whenCreated_thenOwnerRejectsHeader() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var parameter = SyntaxNode.source("source", path.child(SyntaxRole.TYPE_PARAMETER, 0), JavaSourceKind.TYPE_PARAMETER,
        SyntaxKind.TYPE_PARAMETER_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.TypeParameterDeclaration("T", "owner", 0, 0, 0, implicitObjectBound()), List.of(), Optional.empty(), ORIGIN);
    var owner = new SyntaxValue.TypeDeclaration("Example", "owner", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 1, false, 0, 0, 0, 0));

    // Act and assert
    for (var sourceKind : List.of(JavaSourceKind.ENUM, JavaSourceKind.ANNOTATION_TYPE)) {
      var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, sourceKind,
          SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, owner,
          List.of(new SyntaxChild(SyntaxRole.TYPE_PARAMETER, 0, parameter)), Optional.empty(), ORIGIN));
      assertTrue(exception.getMessage().contains("type parameters"));
    }
  }

  @Test
  void givenReceiverWithoutNameExpression_whenCreatingNode_thenMissingAttachmentIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0)
        .child(SyntaxRole.RECEIVER_PARAMETER, 0);
    var type = SyntaxNode.source("source", path.child(SyntaxRole.DECLARED_TYPE, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.TYPE_USE, SyntaxType.Unresolved.INSTANCE, new SyntaxValue.SourceSymbol("Example"), List.of(), Optional.empty(), ORIGIN);
    var receiver = new SyntaxValue.ReceiverParameter("owner#method()", Set.of(), Set.of(), 0);

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.VARIABLE,
        SyntaxKind.RECEIVER_PARAMETER, SyntaxType.NotApplicable.INSTANCE, receiver,
        List.of(new SyntaxChild(SyntaxRole.DECLARED_TYPE, 0, type)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("QUALIFIER"));
  }

  @Test
  void givenHeaderCategoryAndValueMismatch_whenCreatingNodes_thenConstructionIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0)
        .child(SyntaxRole.PARAMETER, 0);
    var formal = new SyntaxValue.ParameterDeclaration("owner#method():parameter:0", "value", Set.of(), Set.of(), 0, true, false);
    var receiver = new SyntaxValue.ReceiverParameter("owner#method()", Set.of(), Set.of(), 0);

    // Act
    var genericCategory = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.VARIABLE, SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE, formal, List.of(), Optional.empty(),
        ORIGIN));
    var wrongValue = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.VARIABLE, SyntaxKind.PARAMETER_DECLARATION, SyntaxType.NotApplicable.INSTANCE, receiver, List.of(),
        Optional.empty(), ORIGIN));
    var wrongSourceKind = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.VARIABLE, SyntaxKind.RECEIVER_NAME, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.SourceSymbol("this"), List.of(), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(genericCategory.getMessage().contains("cannot represent Java source kind VARIABLE"));
    assertTrue(wrongValue.getMessage().contains("requires syntax value ParameterDeclaration"));
    assertTrue(wrongSourceKind.getMessage().contains("cannot represent Java source kind VARIABLE"));
  }

  @Test
  void givenReceiverWithBlankOwner_whenCreatingValue_thenOwnerIsRejected() {
    // Arrange
    var ownerKey = " ";

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ReceiverParameter(ownerKey, Set.of(), Set.of(), 0));

    // Assert
    assertTrue(exception.getMessage().contains("ownerKey"));
  }

  @Test
  void givenExplicitReceiverWithoutAttributedType_whenCreatingExecutableValue_thenTypeIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, true, 0, true, 0, true, false);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("method", "owner", "owner#method()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(), shape,
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty())));

    // Assert
    assertTrue(exception.getMessage().contains("receiver-type"));
  }

  @Test
  void givenPrimitiveAttributedReceiverType_whenCreatingExecutableValue_thenTypeIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, true, 0, true, 0, true, false);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("method", "owner", "owner#method()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(), shape,
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withReceiver(new SyntaxType.Resolved(INT_TYPE), "owner", "Owner",
                Optional.empty(), List.of())));

    // Assert
    assertTrue(exception.getMessage().contains("declared type"));
  }

  @Test
  void givenStaticMethodWithAttributedReceiverType_whenCreatingExecutableValue_thenReceiverIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, true, 0, true, false);
    var receiverType = receiverType("owner", "Owner");

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("method", "owner", "owner#method()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(JavaDeclarationModifier.STATIC), shape,
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withReceiver(receiverType, "owner", "Owner", Optional.empty(), List.of())));

    // Assert
    assertTrue(exception.getMessage().contains("static executable"));
  }

  @Test
  void givenWrittenStaticMethodWithReceiverParameter_whenCreatingExecutableValue_thenReceiverIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, true, 0, true, 0, true, false);
    var receiverType = receiverType("owner", "Owner");

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("method", "owner", "owner#method()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(JavaDeclarationModifier.STATIC), Set.of(), shape,
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withReceiver(receiverType, "owner", "Owner", Optional.empty(), List.of())));

    // Assert
    assertTrue(exception.getMessage().contains("static executable"));
  }

  @Test
  void givenMethodReceiverOfAnotherType_whenCreatingExecutableValue_thenOwnerIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, true, 0, true, false);
    var receiver = SyntaxValue.ExecutableDeclaration.AttributedHeader.withReceiver(
        receiverType("type:Unrelated", "Unrelated"), "type:Unrelated", "Unrelated", Optional.empty(), List.of());

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("run", "type:Host", "type:Host#run()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(), shape, receiver));

    // Assert
    assertTrue(exception.getMessage().contains("method receiver"));
  }

  @Test
  void givenReceiverTypeContradictingItsCanonicalOwner_whenCreatingAttributedHeader_thenIdentityIsRejected() {
    // Arrange
    var receiverType = receiverType("type:Unrelated", "Unrelated");

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> SyntaxValue.ExecutableDeclaration.AttributedHeader.withReceiver(receiverType, "type:Host", "Unrelated",
            Optional.empty(), List.of()));

    // Assert
    assertTrue(exception.getMessage().contains("attributed owner"));
  }

  @Test
  void givenTopLevelConstructorWithReceiver_whenCreatingExecutableValue_thenEnclosingOwnerIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, false, 0, true, false);
    var receiver = SyntaxValue.ExecutableDeclaration.AttributedHeader.withReceiver(
        receiverType("type:Unrelated", "Unrelated"), "type:Unrelated", "Unrelated", Optional.empty(), List.of());

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("Host", "type:Host", "type:Host#Host()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR, Set.of(), Set.of(), shape, receiver));

    // Assert
    assertTrue(exception.getMessage().contains("immediately enclosing"));
  }

  @Test
  void givenLocalInnerConstructorWithEnclosingReceiver_whenCreatingExecutableValue_thenOwnerIsAccepted() {
    // Arrange
    var localKey = "type:Outer#method():type:10:30";
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, true, 0, false, 0, true, false);
    var receiver = SyntaxValue.ExecutableDeclaration.AttributedHeader.withReceiver(
        receiverType("type:Outer", "Outer"), "type:Outer", "Outer", Optional.of("type:Outer"), List.of());

    // Act
    var value = new SyntaxValue.ExecutableDeclaration("Local", localKey, localKey + "#Local()",
        SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR, Set.of(), Set.of(), shape, receiver);

    // Assert
    assertEquals("type:Outer", value.receiverOwnerKey().orElseThrow());
  }

  @Test
  void givenThrownAttachmentWithoutAttributedType_whenCreatingExecutableValue_thenCountIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, true, 1, true, false);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.ExecutableDeclaration("run", "type:Host", "type:Host#run()",
            SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(), shape,
            SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty())));

    // Assert
    assertTrue(exception.getMessage().contains("thrown-type counts"));
  }

  @Test
  void givenReceiverOwnedByAnotherExecutable_whenAttached_thenOwnerIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0);
    var receiverPath = path.child(SyntaxRole.RECEIVER_PARAMETER, 0);
    var receiverType = SyntaxNode.source("source", receiverPath.child(SyntaxRole.DECLARED_TYPE, 0),
        JavaSourceKind.IDENTIFIER, SyntaxKind.TYPE_USE, SyntaxType.Unresolved.INSTANCE, new SyntaxValue.SourceSymbol("Example"),
        List.of(), Optional.empty(), ORIGIN);
    var receiverName = SyntaxNode.source("source", receiverPath.child(SyntaxRole.QUALIFIER, 0),
        JavaSourceKind.IDENTIFIER, SyntaxKind.RECEIVER_NAME, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.SourceSymbol("this"), List.of(), Optional.empty(), ORIGIN);
    var receiver = SyntaxNode.source("source", receiverPath, JavaSourceKind.VARIABLE, SyntaxKind.RECEIVER_PARAMETER,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.ReceiverParameter("owner#other()", Set.of(), Set.of(), 0),
        List.of(new SyntaxChild(SyntaxRole.DECLARED_TYPE, 0, receiverType),
            new SyntaxChild(SyntaxRole.QUALIFIER, 0, receiverName)), Optional.empty(), ORIGIN);
    var returnType = SyntaxNode.source("source", path.child(SyntaxRole.RETURN_TYPE, 0), JavaSourceKind.PRIMITIVE_TYPE,
        SyntaxKind.TYPE_USE, SyntaxType.Unresolved.INSTANCE, new SyntaxValue.PrimitiveType(JavaTypeCategory.INT), List.of(), Optional.empty(), ORIGIN);
    var value = new SyntaxValue.ExecutableDeclaration("method", "owner", "owner#method()",
        SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(),
        new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, true, 0, true, 0, false, false),
        SyntaxValue.ExecutableDeclaration.AttributedHeader.withReceiver(
            receiverType("owner", "Owner"), "owner", "Owner", Optional.empty(), List.of()));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        List.of(new SyntaxChild(SyntaxRole.RECEIVER_PARAMETER, 0, receiver),
            new SyntaxChild(SyntaxRole.RETURN_TYPE, 0, returnType)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenInnerConstructorWithWrongReceiverQualifier_whenAttached_thenQualifierIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0)
        .child(SyntaxRole.MEMBER, 0);
    var receiverPath = path.child(SyntaxRole.RECEIVER_PARAMETER, 0);
    var declaredType = SyntaxNode.source("source", receiverPath.child(SyntaxRole.DECLARED_TYPE, 0),
        JavaSourceKind.IDENTIFIER, SyntaxKind.TYPE_USE, SyntaxType.Unresolved.INSTANCE, new SyntaxValue.SourceSymbol("Example"),
        List.of(), Optional.empty(), ORIGIN);
    var qualifier = SyntaxNode.source("source", receiverPath.child(SyntaxRole.QUALIFIER, 0),
        JavaSourceKind.MEMBER_SELECT, SyntaxKind.RECEIVER_NAME, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.SourceSymbol("Wrong.this"), List.of(), Optional.empty(), ORIGIN);
    var receiver = SyntaxNode.source("source", receiverPath, JavaSourceKind.VARIABLE, SyntaxKind.RECEIVER_PARAMETER,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.ReceiverParameter("type:Outer.Inner#Inner()", Set.of(), Set.of(), 0),
        List.of(new SyntaxChild(SyntaxRole.DECLARED_TYPE, 0, declaredType), new SyntaxChild(SyntaxRole.QUALIFIER, 0, qualifier)),
        Optional.empty(), ORIGIN);
    var body = SyntaxNode.source("source", path.child(SyntaxRole.BODY, 0), JavaSourceKind.BLOCK, SyntaxKind.BLOCK,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(), ORIGIN);
    var value = new SyntaxValue.ExecutableDeclaration("Inner", "type:Outer.Inner", "type:Outer.Inner#Inner()",
        SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR, Set.of(), Set.of(),
        new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, true, 0, false, 0, true, false),
        SyntaxValue.ExecutableDeclaration.AttributedHeader.withReceiver(
            receiverType("type:Outer", "Outer"), "type:Outer", "Outer", Optional.of("type:Outer"), List.of()));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path,
        JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        List.of(new SyntaxChild(SyntaxRole.RECEIVER_PARAMETER, 0, receiver), new SyntaxChild(SyntaxRole.BODY, 0, body)),
        Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenReceiverNameWithMismatchedSourceForm_whenCreatedThenInvalidNameIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0)
        .child(SyntaxRole.RECEIVER_PARAMETER, 0).child(SyntaxRole.QUALIFIER, 0);

    // Act
    var unrelatedIdentifier = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path, JavaSourceKind.IDENTIFIER, SyntaxKind.RECEIVER_NAME,
            SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol("other"), List.of(), Optional.empty(), ORIGIN));
    var unqualifiedMemberSelect = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path, JavaSourceKind.MEMBER_SELECT, SyntaxKind.RECEIVER_NAME,
            SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol("this"), List.of(), Optional.empty(), ORIGIN));
    var invalidQualifier = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path, JavaSourceKind.MEMBER_SELECT, SyntaxKind.RECEIVER_NAME,
            SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol("not a name.this"), List.of(), Optional.empty(), ORIGIN));
    var reservedQualifier = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path, JavaSourceKind.MEMBER_SELECT, SyntaxKind.RECEIVER_NAME,
            SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol("class.this"), List.of(), Optional.empty(), ORIGIN));
    var literalQualifier = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path, JavaSourceKind.MEMBER_SELECT, SyntaxKind.RECEIVER_NAME,
            SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol("null.this"), List.of(), Optional.empty(), ORIGIN));
    var restrictedTypeQualifier = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path, JavaSourceKind.MEMBER_SELECT, SyntaxKind.RECEIVER_NAME,
            SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol("record.this"), List.of(), Optional.empty(), ORIGIN));
    var contextualQualifier = SyntaxNode.source("source", path, JavaSourceKind.MEMBER_SELECT, SyntaxKind.RECEIVER_NAME,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol("module.this"), List.of(), Optional.empty(), ORIGIN);

    // Assert
    assertTrue(unrelatedIdentifier.getMessage().contains("Receiver name"));
    assertTrue(unqualifiedMemberSelect.getMessage().contains("Receiver name"));
    assertTrue(invalidQualifier.getMessage().contains("Receiver name"));
    assertTrue(reservedQualifier.getMessage().contains("Receiver name"));
    assertTrue(literalQualifier.getMessage().contains("Receiver name"));
    assertTrue(restrictedTypeQualifier.getMessage().contains("Receiver name"));
    assertEquals("module.this", contextualQualifier.value(SyntaxValue.SourceSymbol.class).name());
  }

  @Test
  void givenReceiverInFormalRole_whenCreated_thenExecutableRejectsReceiverSyntax() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0);
    var parameterPath = path.child(SyntaxRole.PARAMETER, 0);
    var type = SyntaxNode.source("source", parameterPath.child(SyntaxRole.DECLARED_TYPE, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.TYPE_USE, SyntaxType.Unresolved.INSTANCE, new SyntaxValue.SourceSymbol("Example"), List.of(), Optional.empty(), ORIGIN);
    var receiver = SyntaxNode.source("source", parameterPath, JavaSourceKind.VARIABLE, SyntaxKind.RECEIVER_PARAMETER,
        SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.ReceiverParameter("owner#method()", Set.of(), Set.of(), 0),
        List.of(new SyntaxChild(SyntaxRole.DECLARED_TYPE, 0, type),
            new SyntaxChild(SyntaxRole.QUALIFIER, 0,
                SyntaxNode.source("source", parameterPath.child(SyntaxRole.QUALIFIER, 0), JavaSourceKind.IDENTIFIER,
                    SyntaxKind.RECEIVER_NAME, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol("this"), List.of(),
                    Optional.empty(), ORIGIN))),
        Optional.empty(), ORIGIN);
    var value = new SyntaxValue.ExecutableDeclaration("method", "owner", "owner#method()",
        SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, Set.of(), Set.of(),
        new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 1, true, 0, false, false),
        SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty()));
    var returnType = SyntaxNode.source("source", path.child(SyntaxRole.RETURN_TYPE, 0), JavaSourceKind.PRIMITIVE_TYPE,
        SyntaxKind.TYPE_USE, SyntaxType.Unresolved.INSTANCE, new SyntaxValue.PrimitiveType(JavaTypeCategory.INT), List.of(), Optional.empty(), ORIGIN);

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.METHOD,
        SyntaxKind.METHOD_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        List.of(new SyntaxChild(SyntaxRole.PARAMETER, 0, receiver), new SyntaxChild(SyntaxRole.RETURN_TYPE, 0, returnType)),
        Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenGenericUnsupportedInitializer_whenAttachedToTypeMember_thenMemberIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var initializer = SyntaxNode.source("source", path.child(SyntaxRole.MEMBER, 0), JavaSourceKind.BLOCK,
        SyntaxKind.UNSUPPORTED, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported("BLOCK"), List.of(),
        Optional.empty(), ORIGIN);
    var value = new SyntaxValue.TypeDeclaration("Example", "owner", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 1));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path, JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        List.of(new SyntaxChild(SyntaxRole.MEMBER, 0, initializer)), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("child kind"));
  }

  @Test
  void givenIfNodeWithTypedRoles_whenQueried_thenStructureDoesNotDependOnChildPosition() {
    // Arrange
    var condition = leaf("condition", SyntaxKind.LITERAL, new SyntaxValue.Literal("true"));
    var thenBranch = leaf("then", SyntaxKind.BLOCK, SyntaxValue.None.INSTANCE);
    var elseBranch = leaf("else", SyntaxKind.BLOCK, SyntaxValue.None.INSTANCE);
    var node = source("if", SyntaxKind.IF, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.ELSE_BRANCH, 0, elseBranch), new SyntaxChild(SyntaxRole.CONDITION, 0, condition),
            new SyntaxChild(SyntaxRole.THEN_BRANCH, 0, thenBranch)), Optional.empty());

    // Act
    var actualCondition = node.requiredChild(SyntaxRole.CONDITION);
    var actualThenBranch = node.requiredChild(SyntaxRole.THEN_BRANCH);
    var actualElseBranch = node.optionalChild(SyntaxRole.ELSE_BRANCH).orElseThrow();

    // Assert
    assertEquals(condition, actualCondition);
    assertEquals(thenBranch, actualThenBranch);
    assertEquals(elseBranch, actualElseBranch);
  }

  @Test
  void givenStatementInConditionRole_whenCreatingConditional_thenChildCategoryIsRejected() {
    // Arrange
    var condition = leaf("condition", SyntaxKind.BLOCK, SyntaxValue.None.INSTANCE);
    var body = leaf("body", SyntaxKind.BLOCK, SyntaxValue.None.INSTANCE);

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> source("if", SyntaxKind.IF, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
            List.of(new SyntaxChild(SyntaxRole.CONDITION, 0, condition), new SyntaxChild(SyntaxRole.THEN_BRANCH, 0, body)), Optional.empty()));

    // Assert
    assertTrue(failure.getMessage().contains("child category"));
  }

  @TestFactory
  Stream<DynamicTest> givenDirectDeclarations_whenAttachedAsStatementBodies_thenEveryBodyOwnerRejectsTheCategory() {
    return StatementBranchFixture.DECLARATIONS.stream().flatMap(kind -> Stream.of(false, true).flatMap(unsupported ->
        Stream.of(SyntaxKind.IF, SyntaxKind.WHILE_LOOP, SyntaxKind.DO_WHILE_LOOP, SyntaxKind.FOR_LOOP,
            SyntaxKind.ENHANCED_FOR_LOOP, SyntaxKind.LABELED_STATEMENT).flatMap(owner ->
                (owner == SyntaxKind.IF ? Stream.of(SyntaxRole.THEN_BRANCH, SyntaxRole.ELSE_BRANCH) : Stream.of(SyntaxRole.BODY))
                    .map(role -> DynamicTest.dynamicTest("given" + kind + (unsupported ? "Unsupported" : "Represented")
                        + "_whenAttachedTo" + owner + role + "_thenChildCategoryIsRejected", () -> {
                      // Arrange
                      var declaration = StatementBranchFixture.declaration("source", path("declaration"), kind, unsupported, Optional.of(ORIGIN));
                      // Act
                      var failure = assertThrows(IllegalArgumentException.class, () -> bodyOwner(owner, role, declaration));
                      // Assert
                      assertTrue(failure.getMessage().contains("child category"), failure.toString());
                    })))));
  }

  @TestFactory
  Stream<DynamicTest> givenLocalDeclarationsInBlocks_whenUsedAsBranches_thenLegalCategoriesAndRoleOrderSurvive() {
    return StatementBranchFixture.DECLARATIONS.stream().filter(kind -> kind != JavaSourceKind.ANNOTATION_TYPE)
        .flatMap(kind -> Stream.of(false, true).flatMap(unsupported -> Stream.of(SyntaxRole.THEN_BRANCH, SyntaxRole.ELSE_BRANCH)
            .map(role -> DynamicTest.dynamicTest("given" + kind + (unsupported ? "Unsupported" : "Represented")
                + "InBlock_whenAttachedTo" + role + "_thenDeclarationAndOrderSurvive", () -> {
              // Arrange
              var declaration = StatementBranchFixture.declaration("source", path("declaration"), kind, unsupported, Optional.of(ORIGIN));
              var block = source("block", SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
                  List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, declaration)), Optional.empty());
              // Act
              var result = bodyOwner(SyntaxKind.IF, role, block);
              // Assert
              assertEquals(declaration, result.requiredChild(role).requiredChild(SyntaxRole.STATEMENT));
              assertEquals(List.of(SyntaxRole.CONDITION, SyntaxRole.THEN_BRANCH, SyntaxRole.ELSE_BRANCH),
                  result.children().stream().map(SyntaxChild::role).toList());
            }))));
  }

  @TestFactory
  Stream<DynamicTest> givenAnnotationInterface_whenPlacedInStatementList_thenLocalCategoryIsRejected() {
    return Stream.of(false, true).flatMap(unsupported -> Stream.of(SyntaxKind.BLOCK, SyntaxKind.STATIC_INITIALIZER_BLOCK,
        SyntaxKind.INSTANCE_INITIALIZER_BLOCK, SyntaxKind.CASE).map(owner -> DynamicTest.dynamicTest("givenAnnotationInterface"
        + (unsupported ? "Unsupported" : "Represented") + "_whenPlacedIn" + owner + "_thenLocalCategoryIsRejected", () -> {
      // Arrange
      var declaration = StatementBranchFixture.declaration("source", path("annotation"), JavaSourceKind.ANNOTATION_TYPE,
          unsupported, Optional.of(ORIGIN));
      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> statementList(owner, declaration));
      // Assert
      assertTrue(failure.getMessage().contains("child category"), failure.toString());
    })));
  }

  @Test
  void givenLegalLocalDeclarations_whenPlacedInInitializerOrSwitchStatementLists_thenChildrenRemainValid() {
    for (var owner : List.of(SyntaxKind.STATIC_INITIALIZER_BLOCK, SyntaxKind.INSTANCE_INITIALIZER_BLOCK, SyntaxKind.CASE)) {
      for (var kind : StatementBranchFixture.DECLARATIONS) {
        if (kind == JavaSourceKind.ANNOTATION_TYPE) continue;
        // Arrange
        var declaration = StatementBranchFixture.declaration("source", path("declaration"), kind, false, Optional.of(ORIGIN));
        // Act
        var result = statementList(owner, declaration);
        // Assert
        assertEquals(List.of(declaration), result.children(SyntaxRole.STATEMENT));
      }
    }
  }

  private static SyntaxNode statementList(SyntaxKind kind, SyntaxNode statement) {
    SyntaxValue value = kind == SyntaxKind.CASE ? new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.STATEMENT_GROUP,
        List.of(SyntaxValue.SwitchLabel.Default.INSTANCE)) : SyntaxValue.None.INSTANCE;
    return SyntaxNode.source("source", path("container"), kind == SyntaxKind.CASE ? JavaSourceKind.CASE : JavaSourceKind.BLOCK,
        kind, SyntaxType.NotApplicable.INSTANCE, value, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, statement)), Optional.empty(), ORIGIN);
  }

  @Test
  void givenDeclarationsWithoutDirectSource_whenUsedAsBranches_thenTheSameCategoryInvariantApplies() {
    for (var kind : StatementBranchFixture.DECLARATIONS) {
      // Arrange
      var declaration = StatementBranchFixture.declaration("source", path("declaration"), kind, false, Optional.empty());
      var condition = leaf("condition", SyntaxKind.LITERAL, new SyntaxValue.Literal("1"));
      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.withoutDirectSource("source", path("if"),
          JavaSourceKind.IF, SyntaxKind.IF, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
          List.of(new SyntaxChild(SyntaxRole.CONDITION, 0, condition), new SyntaxChild(SyntaxRole.THEN_BRANCH, 0, declaration)), Optional.empty()));
      // Assert
      assertTrue(failure.getMessage().contains("child category"), failure.toString());
    }
  }

  @Test
  void givenNestedLoopAndConditional_whenUsedAsBranches_thenNestedStatementsRemainQueryable() {
    // Arrange
    var empty = leaf("empty", SyntaxKind.EMPTY, SyntaxValue.None.INSTANCE);
    var loop = bodyOwner(SyntaxKind.WHILE_LOOP, SyntaxRole.BODY, empty);
    var nested = bodyOwner(SyntaxKind.IF, SyntaxRole.ELSE_BRANCH, loop);
    // Act
    var outer = bodyOwner(SyntaxKind.IF, SyntaxRole.THEN_BRANCH, nested);
    // Assert
    assertEquals(empty, outer.requiredChild(SyntaxRole.THEN_BRANCH).requiredChild(SyntaxRole.ELSE_BRANCH).requiredChild(SyntaxRole.BODY));
  }

  private static SyntaxNode bodyOwner(SyntaxKind owner, SyntaxRole role, SyntaxNode body) {
    var condition = leaf("condition", SyntaxKind.LITERAL, new SyntaxValue.Literal("1"));
    var children = new ArrayList<SyntaxChild>();
    if (owner == SyntaxKind.IF || owner == SyntaxKind.WHILE_LOOP || owner == SyntaxKind.DO_WHILE_LOOP) {
      children.add(new SyntaxChild(SyntaxRole.CONDITION, 0, condition));
    }
    if (owner == SyntaxKind.IF) {
      var empty = leaf("other", SyntaxKind.EMPTY, SyntaxValue.None.INSTANCE);
      children.add(new SyntaxChild(SyntaxRole.THEN_BRANCH, 0, role == SyntaxRole.THEN_BRANCH ? body : empty));
      children.add(new SyntaxChild(SyntaxRole.ELSE_BRANCH, 0, role == SyntaxRole.ELSE_BRANCH ? body : empty));
    } else {
      if (owner == SyntaxKind.ENHANCED_FOR_LOOP) {
        var parameter = StatementBranchFixture.declaration("source", path("parameter"), JavaSourceKind.VARIABLE, false, Optional.of(ORIGIN));
        children.add(new SyntaxChild(SyntaxRole.PARAMETER, 0, parameter));
        children.add(new SyntaxChild(SyntaxRole.ITERABLE, 0, condition));
      }
      children.add(new SyntaxChild(SyntaxRole.BODY, 0, body));
    }
    SyntaxValue value = owner == SyntaxKind.LABELED_STATEMENT ? new SyntaxValue.StatementLabel(Optional.of("label")) : SyntaxValue.None.INSTANCE;
    return SyntaxNode.source("source", path("owner"), JavaSourceKind.valueOf(owner.name()), owner,
        SyntaxType.NotApplicable.INSTANCE, value, children, Optional.empty(), ORIGIN);
  }

  @Test
  void givenExpressionInStatementRole_whenCreatingBlock_thenChildCategoryIsRejected() {
    // Arrange
    var expression = leaf("expression", SyntaxKind.LITERAL, new SyntaxValue.Literal("1"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> source("block", SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
            List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, expression)), Optional.empty()));

    // Assert
    assertTrue(failure.getMessage().contains("child category"));
  }

  @Test
  void givenNonblockTryBodyOrNonformalCatchParameter_whenCreatingNode_thenChildCategoryIsRejected() {
    // Arrange
    var expression = leaf("expression", SyntaxKind.LITERAL, new SyntaxValue.Literal("1"));
    var body = leaf("body", SyntaxKind.BLOCK, SyntaxValue.None.INSTANCE);

    // Act and Assert
    var tryFailure = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path("try"), JavaSourceKind.TRY, SyntaxKind.TRY, SyntaxType.NotApplicable.INSTANCE,
            SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.TRY_BLOCK, 0, expression)), Optional.empty(), ORIGIN));
    var catchFailure = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path("catch"), JavaSourceKind.CATCH, SyntaxKind.CATCH, SyntaxType.NotApplicable.INSTANCE,
            SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.PARAMETER, 0, expression),
                new SyntaxChild(SyntaxRole.BODY, 0, body)), Optional.empty(), ORIGIN));
    assertTrue(tryFailure.getMessage().contains("child category"));
    assertTrue(catchFailure.getMessage().contains("child category"));
  }

  @Test
  void givenOperatorContradictingExactSourceKind_whenCreatingExpression_thenContradictionIsRejected() {
    // Arrange
    var left = leaf("left", SyntaxKind.LITERAL, new SyntaxValue.Literal("1"));
    var right = leaf("right", SyntaxKind.LITERAL, new SyntaxValue.Literal("2"));

    // Act and Assert
    var binary = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path("binary"), JavaSourceKind.PLUS, SyntaxKind.BINARY, new SyntaxType.Resolved(INT_TYPE),
            new SyntaxValue.Operator(SyntaxOperator.DIVIDE), List.of(new SyntaxChild(SyntaxRole.LEFT_OPERAND, 0, left),
                new SyntaxChild(SyntaxRole.RIGHT_OPERAND, 0, right)), Optional.empty(), ORIGIN));
    var unary = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path("unary"), JavaSourceKind.UNARY_MINUS, SyntaxKind.UNARY, new SyntaxType.Resolved(INT_TYPE),
            new SyntaxValue.Operator(SyntaxOperator.UNARY_PLUS), List.of(new SyntaxChild(SyntaxRole.OPERAND, 0, left)), Optional.empty(), ORIGIN));
    assertTrue(binary.getMessage().contains("operator"));
    assertTrue(unary.getMessage().contains("operator"));
  }

  @Test
  void givenOppositeShortCircuitOperator_whenCreatingConditionalExpression_thenContradictionIsRejected() {
    // Arrange
    var type = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.BOOLEAN, "boolean", "boolean"));
    var left = SyntaxNode.source("source", path("left"), JavaSourceKind.BOOLEAN_LITERAL, SyntaxKind.LITERAL, type,
        new SyntaxValue.Literal("true"), List.of(), Optional.empty(), ORIGIN);
    var right = SyntaxNode.source("source", path("right"), JavaSourceKind.BOOLEAN_LITERAL, SyntaxKind.LITERAL, type,
        new SyntaxValue.Literal("false"), List.of(), Optional.empty(), ORIGIN);

    for (var kind : List.of(SyntaxKind.CONDITIONAL_AND, SyntaxKind.CONDITIONAL_OR)) {
      var operator = kind == SyntaxKind.CONDITIONAL_AND ? SyntaxOperator.CONDITIONAL_OR : SyntaxOperator.CONDITIONAL_AND;

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("source", path("conditional"),
          JavaSourceKind.valueOf(kind.name()), kind, type, new SyntaxValue.Operator(operator),
          List.of(new SyntaxChild(SyntaxRole.LEFT_OPERAND, 0, left), new SyntaxChild(SyntaxRole.RIGHT_OPERAND, 0, right)),
          Optional.empty(), ORIGIN));

      // Assert
      assertTrue(failure.getMessage().contains("operator"));
    }
  }

  @Test
  void givenThisQualifierOnSuperSelection_whenCreatingInvocationOrField_thenKnownSourceFormsCannotContradict() {
    // Arrange
    var receiver = SyntaxNode.source("source", path("receiver"), JavaSourceKind.IDENTIFIER, SyntaxKind.RECEIVER,
        new SyntaxType.Resolved(DECLARED_TYPE), new SyntaxValue.Receiver(SyntaxValue.ReceiverKind.THIS), List.of(), Optional.empty(), ORIGIN);
    var children = List.of(new SyntaxChild(SyntaxRole.QUALIFIER, 0, receiver));

    // Act
    var call = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path("call"), JavaSourceKind.METHOD_INVOCATION, SyntaxKind.INVOCATION,
            new SyntaxType.Resolved(INT_TYPE), new SyntaxValue.Invocation("call", InvocationForm.SUPER, Optional.empty()),
            children, Optional.of(reference(ReferenceKind.METHOD, "Example#call()")), ORIGIN));
    var field = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source("source", path("field"), JavaSourceKind.MEMBER_SELECT, SyntaxKind.FIELD_ACCESS,
            new SyntaxType.Resolved(INT_TYPE), new SyntaxValue.FieldAccess("field", SyntaxValue.FieldReceiver.SUPER),
            children, Optional.of(reference(ReferenceKind.FIELD, "Example#field")), ORIGIN));

    // Assert
    assertTrue(call.getMessage().contains("qualifier contradicts"), call.toString());
    assertTrue(field.getMessage().contains("qualifier contradicts"), field.toString());
  }

  @Test
  void givenIfNodeWithoutCondition_whenCreated_thenInvalidShapeIsRejected() {
    // Arrange
    var thenBranch = leaf("then", SyntaxKind.BLOCK, SyntaxValue.None.INSTANCE);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("if", SyntaxKind.IF, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
            List.of(new SyntaxChild(SyntaxRole.THEN_BRANCH, 0, thenBranch)), Optional.empty()));

    // Assert
    assertTrue(exception.getMessage().contains("CONDITION"));
  }

  @Test
  void givenLocalDeclarationWithoutTypedValue_whenCreated_thenInvalidValueIsRejected() {
    // Arrange
    var reference = Optional.of(reference(ReferenceKind.LOCAL, "local"));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("local", SyntaxKind.LOCAL_DECLARATION, new SyntaxType.Resolved(INT_TYPE), SyntaxValue.None.INSTANCE, List.of(),
            reference));

    // Assert
    assertTrue(exception.getMessage().contains("LocalDeclaration"));
  }

  @Test
  void givenValueExpressionWithoutTypeOutcome_whenCreated_thenInvalidTypeContractIsRejected() {
    // Arrange
    var value = new SyntaxValue.Literal("1");

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("literal", SyntaxKind.LITERAL, SyntaxType.NotApplicable.INSTANCE, value, List.of(), Optional.empty()));

    // Assert
    assertTrue(exception.getMessage().contains("compiler type"));
  }

  @Test
  void givenBlankSourceOwner_whenCreated_thenInvalidIdentityIsRejected() {
    // Arrange
    var value = new SyntaxValue.Literal("1");

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source(" ", path("literal"), JavaSourceKind.INT_LITERAL, SyntaxKind.LITERAL,
            new SyntaxType.Resolved(INT_TYPE), value, List.of(), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("Source-file key"));
  }

  @Test
  void givenCompilerGeneratedFactory_whenNodeIsCreated_thenDirectOriginIsAbsent() {
    // Arrange
    var value = new SyntaxValue.Literal("1");

    // Act
    var node = SyntaxNode.withoutDirectSource("source", path("literal"), JavaSourceKind.INT_LITERAL, SyntaxKind.LITERAL,
        new SyntaxType.Resolved(INT_TYPE), value, List.of(), Optional.empty());

    // Assert
    assertTrue(node.origin().isEmpty());
  }

  @Test
  void givenMethodTreeContainsUnsupportedDescendant_whenReadinessIsQueried_thenUnsupportedIsDerived() {
    // Arrange
    var unsupported = source("unsupported", SyntaxKind.UNSUPPORTED, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Unsupported("WHILE_LOOP"), List.of(), Optional.empty());
    var root = source("root", SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, unsupported)), Optional.empty());
    var methodSyntax = MethodSyntaxFixture.view("type:Example#run()", root);

    // Act
    var readiness = CfgAdmission.classify(methodSyntax);

    // Assert
    assertEquals(CfgReadiness.UNSUPPORTED, readiness);
  }

  @Test
  void givenTypeInitializerInStatementRole_whenConstructingBody_thenChildCategoryIsRejected() {
    // Arrange
    var initializer = SyntaxNode.source("source", path("initializer"), JavaSourceKind.BLOCK,
        SyntaxKind.INSTANCE_INITIALIZER_BLOCK, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(),
        Optional.empty(), ORIGIN);
    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> source("root", SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
            List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, initializer)), Optional.empty()));

    // Assert
    assertTrue(failure.getMessage().contains("child category"));
  }

  @Test
  void givenRuleCaseWithoutBody_whenCreated_thenInvalidCaseShapeIsRejected() {
    // Arrange
    var value = new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.RULE,
        List.of(SyntaxValue.SwitchLabel.Default.INSTANCE));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("case", SyntaxKind.CASE, SyntaxType.NotApplicable.INSTANCE, value, List.of(), Optional.empty()));

    // Assert
    assertTrue(exception.getMessage().contains("BODY"));
  }

  @Test
  void givenStatementGroupCaseWithRuleBody_whenCreated_thenConflictingCaseFormsAreRejected() {
    // Arrange
    var value = new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.STATEMENT_GROUP,
        List.of(SyntaxValue.SwitchLabel.Default.INSTANCE));
    var body = leaf("body", SyntaxKind.BLOCK, SyntaxValue.None.INSTANCE);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("case", SyntaxKind.CASE, SyntaxType.NotApplicable.INSTANCE, value,
            List.of(new SyntaxChild(SyntaxRole.BODY, 0, body)), Optional.empty()));

    // Assert
    assertTrue(exception.getMessage().contains("BODY"));
  }

  @Test
  void givenDefaultAndExpressionLabels_whenCreated_thenInvalidCaseLabelsAreRejected() {
    // Arrange
    var labels = List.of(SyntaxValue.SwitchLabel.Default.INSTANCE, new SyntaxValue.SwitchLabel.Expression(0));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.RULE, labels));

    // Assert
    assertTrue(exception.getMessage().contains("default switch label"));
  }

  @Test
  void givenFieldReferenceOnVariableAccess_whenCreated_thenReferenceKindIsRejected() {
    // Arrange
    var fieldReference = Optional.of(reference(ReferenceKind.FIELD, "field"));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("variable", SyntaxKind.VARIABLE_ACCESS, new SyntaxType.Resolved(INT_TYPE), new SyntaxValue.SourceSymbol("value"),
            List.of(), fieldReference));

    // Assert
    assertTrue(exception.getMessage().contains("semantic reference"));
  }

  @Test
  void givenSameSyntaxNodeOwnedTwice_whenMethodIsCreated_thenDuplicateOwnershipIsRejected() {
    // Arrange
    var statement = leaf("statement", SyntaxKind.EMPTY, SyntaxValue.None.INSTANCE);
    var root = source("root", SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, statement), new SyntaxChild(SyntaxRole.STATEMENT, 1, statement)),
        Optional.empty());

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> MethodSyntaxFixture.view("type:Example#run()", root));

    // Assert
    assertTrue(exception.getMessage().contains("one parent"));
  }

  private static SyntaxNode leaf(String id, SyntaxKind kind, SyntaxValue value) {
    var type = kind.requiresType() ? new SyntaxType.Resolved(INT_TYPE) : SyntaxType.NotApplicable.INSTANCE;
    return source(id, kind, type, value, List.of(), Optional.empty());
  }

  private static JavaTypeStructure implicitObjectBound() {
    return new JavaTypeStructure.Declared("type:java.lang.Object", Optional.empty(), List.of());
  }

  private static JavaTypeStructure structure(SyntaxNode node) {
    return ((SyntaxType.Resolved) node.type()).structure().orElseThrow();
  }

  private static SyntaxNode declaredUse(SyntaxStructuralPath path, String name, String key) {
    return declaredUse(path, name, key, ORIGIN);
  }

  private static SyntaxNode declaredUse(SyntaxStructuralPath path, String name, String key, SourceSpan origin) {
    return SyntaxNode.source("source", path, JavaSourceKind.IDENTIFIER, SyntaxKind.TYPE_USE,
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.DECLARED, name, name),
            new JavaTypeStructure.Declared(key, Optional.empty(), List.of())),
        new SyntaxValue.SourceSymbol(name), List.of(), Optional.of(reference(ReferenceKind.TYPE, key)), origin);
  }

  private static SyntaxType receiverType(String key, String name) {
    return new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.DECLARED, name, name),
        new JavaTypeStructure.Declared(key, Optional.empty(), List.of()));
  }

  private static SyntaxNode source(String id, SyntaxKind kind, SyntaxType type, SyntaxValue value, List<SyntaxChild> children,
      Optional<SemanticReference> reference) {
    return SyntaxNode.source("source", path(id), sourceKind(kind, value), kind, type, value, children, reference, ORIGIN);
  }

  private static SyntaxStructuralPath path(String identity) {
    return SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT,
        Math.floorMod(identity.hashCode(), Integer.MAX_VALUE));
  }

  private static JavaSourceKind sourceKind(SyntaxKind kind, SyntaxValue value) {
    return switch (kind) {
      case BLOCK -> JavaSourceKind.BLOCK;
      case LOCAL_DECLARATION -> JavaSourceKind.VARIABLE;
      case IF -> JavaSourceKind.IF;
      case VARIABLE_ACCESS -> JavaSourceKind.IDENTIFIER;
      case LITERAL -> JavaSourceKind.INT_LITERAL;
      case CASE -> JavaSourceKind.CASE;
      case EMPTY -> JavaSourceKind.EMPTY_STATEMENT;
      case UNSUPPORTED -> JavaSourceKind.valueOf(((SyntaxValue.Unsupported) value).compilerKind());
      default -> throw new IllegalArgumentException("Missing test source kind for " + kind);
    };
  }

  private static SemanticReference reference(ReferenceKind kind, String key) {
    return new SemanticReference(kind, ResolutionStatus.RESOLVED, Optional.of(key), Optional.empty(), List.of(), List.of());
  }
}
