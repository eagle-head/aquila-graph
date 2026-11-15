package org.aquilagraph.source.api;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import java.util.Optional;
import java.util.List;
import org.aquilagraph.identity.api.SourceOccurrence;


import org.aquilagraph.source.admission.AnnotationAttachmentAdmission;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.cfg.internal.CfgReadiness;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.SyntaxSemanticTarget.Declaration;
import org.junit.jupiter.api.Test;

class JavaAstAggregateTest {
  private static final String CONTENT = "0123456789";

  @Test
  void givenReferencesDifferingOnlyInTypedTargetPresence_whenUnorderedEdgesAreAdmitted_thenOneCompleteCanonicalOrderSurvives() {
    // Arrange
    var snapshot = snapshot("a");
    var root = root("a", List.of());
    var base = contribution(root);
    var identity = new JavaTypeDeclarationIdentity(Optional.of("service.sample"), "api.Service");
    var plain = new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of(identity.declarationKey()), Optional.of("api.Service"), List.of("api.Service"), List.of("api.Service"), Optional.empty());
    var typed = new SemanticReference(plain.referenceKind(), plain.status(), plain.symbolKey(), plain.typeName(), plain.typeAlternativeNames(), plain.assignableToTypeNames(), Optional.of(identity));
    var first = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE, new Declaration(plain));
    var second = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE, new Declaration(typed));
    // Act
    var forward = AggregateFixture.builder(snapshot).add(new SyntaxSourceContribution("a", root, base.nodes(), base.structuralEdges(), List.of(first, second))).seal();
    var reversed = AggregateFixture.builder(snapshot).add(new SyntaxSourceContribution("a", root, base.nodes(), base.structuralEdges(), List.of(second, first))).seal();
    // Assert
    assertEquals(List.of(first, second), forward.semanticEdges());
    assertEquals(forward, reversed);
    assertNotEquals(first.target(), second.target());
  }

  @Test
  void givenUnresolvedOrIncompleteNestedValue_whenDefaultIsConstructed_thenTypedPublicationIsRejected() {
    // Arrange
    var methodPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0);
    var valuePath = methodPath.child(SyntaxRole.DEFAULT_VALUE, 0);
    var range = new SourceSpan("a", 2, 8);
    var unresolvedType = new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED,
        Optional.of("type:Inner"), Optional.empty(), List.of(), List.of(), Optional.empty());
    var unresolved = SyntaxNode.source("a", valuePath, JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION,
        SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.MARKER, List.of(), SyntaxValue.AnnotationLocation.VALUE),
        List.of(), Optional.of(unresolvedType), range);
    var incomplete = SyntaxNode.source("a", valuePath, JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION,
        SyntaxType.NotApplicable.INSTANCE,
        SyntaxValue.Annotation.withArgumentFailure(SyntaxValue.AnnotationForm.SINGLE_ELEMENT,
            List.of(new SyntaxValue.AnnotationArgumentHeader(Optional.empty())), SyntaxValue.AnnotationLocation.VALUE,
            Set.of(), "frontend.unavailable-annotation-attribution"),
        List.of(), Optional.of(reference("type:Inner")), range);
    var returnType = SyntaxNode.source("a", methodPath.child(SyntaxRole.RETURN_TYPE, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.TYPE_USE, new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Inner", "Inner"),
            new JavaTypeStructure.Declared("type:Inner", Optional.empty(), List.of())),
        new SyntaxValue.SourceSymbol("Inner"), List.of(), Optional.of(reference("type:Inner")), new SourceSpan("a", 0, 1));
    var defaultValue = new AnnotationElementValue.Nested("type:Inner");

    // Act
    var unresolvedFailure = assertThrows(IllegalArgumentException.class,
        () -> annotationElement(returnType, Optional.of(defaultValue), List.of(unresolved)));
    var incompleteFailure = assertThrows(IllegalArgumentException.class,
        () -> annotationElement(returnType, Optional.of(defaultValue), List.of(incomplete)));

    // Assert
    assertTrue(unresolvedFailure.getMessage().contains("does not match its written source form"));
    assertTrue(incompleteFailure.getMessage().contains("does not match its written source form"));
  }

  @Test
  void givenAnnotationSourceKinds_whenClassifiedAsCompilationUnits_thenConstructionIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DEFAULT_VALUE, 0);
    var sourceKinds = List.of(JavaSourceKind.ANNOTATION, JavaSourceKind.NEW_ARRAY, JavaSourceKind.INT_LITERAL);

    for (var sourceKind : sourceKinds) {
      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", path,
          sourceKind, SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
          List.of(), Optional.empty(), new SourceSpan("a", 2, 8)));

      // Assert
      assertTrue(failure.getMessage().contains("cannot represent Java source kind"));
    }
  }

  @Test
  void givenDefaultFactsAndSourceChildren_whenCardinalityOrTypeDisagrees_thenConstructionIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0);
    var intType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"),
        new JavaTypeStructure.Primitive(JavaTypeCategory.INT));
    var returnType = SyntaxNode.source("a", path.child(SyntaxRole.RETURN_TYPE, 0), JavaSourceKind.PRIMITIVE_TYPE,
        SyntaxKind.TYPE_USE, intType, new SyntaxValue.PrimitiveType(JavaTypeCategory.INT), List.of(), Optional.empty(),
        new SourceSpan("a", 0, 1));
    var expression = SyntaxNode.source("a", path.child(SyntaxRole.DEFAULT_VALUE, 0), JavaSourceKind.INT_LITERAL,
        SyntaxKind.LITERAL, intType, new SyntaxValue.Literal("7"), List.of(), Optional.empty(), new SourceSpan("a", 2, 3));

    // Act
    var missing = assertThrows(IllegalArgumentException.class,
        () -> annotationElement(returnType, Optional.of(new AnnotationScalar.IntValue(7)), List.of()));
    var unexpected = assertThrows(IllegalArgumentException.class,
        () -> annotationElement(returnType, Optional.empty(), List.of(expression)));
    var duplicate = assertThrows(IllegalArgumentException.class,
        () -> annotationElement(returnType, Optional.of(new AnnotationScalar.IntValue(7)), List.of(expression, expression)));
    var incompatible = assertThrows(IllegalArgumentException.class,
        () -> annotationElement(returnType, Optional.of(new AnnotationScalar.StringValue("seven")), List.of(expression)));

    // Assert
    assertTrue(missing.getMessage().contains("DEFAULT_VALUE"));
    assertTrue(unexpected.getMessage().contains("DEFAULT_VALUE"));
    assertTrue(duplicate.getMessage().contains("DEFAULT_VALUE"));
    assertEquals("Annotation scalar category conflicts with the selected element type", incompatible.getMessage());
  }

  @Test
  void givenValueAnnotationAsDeclarationAttachment_whenConstructed_thenLocationIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.PACKAGE_DECLARATION, 0);
    var annotation = SyntaxNode.source("a", path.child(SyntaxRole.ANNOTATION, 0), JavaSourceKind.ANNOTATION,
        SyntaxKind.ANNOTATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.MARKER, List.of(), SyntaxValue.AnnotationLocation.VALUE),
        List.of(), Optional.of(reference("type:Owned")), new SourceSpan("a", 0, 2));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", path,
        JavaSourceKind.PACKAGE, SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.PackageDeclaration("sample"), List.of(new SyntaxChild(SyntaxRole.ANNOTATION, 0, annotation)),
        Optional.empty(), new SourceSpan("a", 0, 9)));

    // Assert
    assertTrue(error.getMessage().contains("location must match its structural role"));
  }

  @Test
  void givenDefaultValueOnOrdinaryMethod_whenConstructed_thenWrongDeclarationFamilyIsRejected() {
    // Arrange
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, true, 0, false, false);
    var attributed = new SyntaxValue.ExecutableDeclaration.AttributedHeader(SyntaxType.NotApplicable.INSTANCE,
        Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.of(new AnnotationScalar.IntValue(7)));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> new SyntaxValue.ExecutableDeclaration("value",
        "type:Owned", "type:Owned#value()", SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD,
        Set.of(), Set.of(), shape, attributed));

    // Assert
    assertEquals("Only an annotation element can own a default value", error.getMessage());
  }

  @Test
  void givenArrayValueWithInconsistentWrittenCardinality_whenPairIsConstructed_thenMismatchIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0)
        .child(SyntaxRole.ANNOTATION_ARGUMENT, 0);
    var valuePath = path.child(SyntaxRole.VALUE, 0);
    var intType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"),
        new JavaTypeStructure.Primitive(JavaTypeCategory.INT));
    var arrayType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "int[]", "int[]"),
        new JavaTypeStructure.Array(new JavaTypeStructure.Primitive(JavaTypeCategory.INT)));
    var literal = SyntaxNode.source("a", valuePath.child(SyntaxRole.VALUE, 0), JavaSourceKind.INT_LITERAL,
        SyntaxKind.LITERAL, intType, new SyntaxValue.Literal("7"), List.of(), Optional.empty(), new SourceSpan("a", 3, 4));
    var array = SyntaxNode.source("a", valuePath, JavaSourceKind.NEW_ARRAY, SyntaxKind.ANNOTATION_ARRAY,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.VALUE, 0, literal)), Optional.empty(), new SourceSpan("a", 2, 5));
    var selectedKey = "type:Owned#value()";
    var selected = new SemanticReference(ReferenceKind.METHOD, ResolutionStatus.RESOLVED, Optional.of(selectedKey),
        Optional.empty(), List.of(), List.of(), Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", path,
        JavaSourceKind.NEW_ARRAY, SyntaxKind.ANNOTATION_ARGUMENT, arrayType,
        new SyntaxValue.AnnotationArgument(Optional.empty(), selectedKey,
            new AnnotationElementValue.Array(List.of(new AnnotationScalar.IntValue(7), new AnnotationScalar.IntValue(8)))),
        List.of(new SyntaxChild(SyntaxRole.VALUE, 0, array)), Optional.of(selected), new SourceSpan("a", 2, 5)));

    // Assert
    assertEquals("Annotation value does not match its written source form", error.getMessage());
  }

  @Test
  void givenScalarPairWithForeignElement_whenAnnotationIsConstructed_thenOwnershipIsRejected() {
    // Arrange
    var annotationPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0);
    var pair = scalarArgument(annotationPath, new AnnotationScalar.IntValue(7), "type:Other#value()", Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", annotationPath,
        JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.SINGLE_ELEMENT,
            List.of(new SyntaxValue.AnnotationArgumentHeader(Optional.empty())), SyntaxValue.AnnotationLocation.DECLARATION,
            Set.of(ElementType.PACKAGE)),
        List.of(new SyntaxChild(SyntaxRole.ANNOTATION_ARGUMENT, 0, pair)), Optional.of(reference("type:Owned")),
        new SourceSpan("a", 0, 7)));

    // Assert
    assertTrue(error.getMessage().contains("outside its annotation type"));
  }

  @Test
  void givenWrittenArgumentWithoutPair_whenAnnotationIsConstructed_thenMissingPairIsRejected() {
    // Arrange
    var annotationPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0);
    var header = List.of(new SyntaxValue.AnnotationArgumentHeader(Optional.empty()));

    // Act
    var missing = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", annotationPath,
        JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.SINGLE_ELEMENT, header, SyntaxValue.AnnotationLocation.DECLARATION,
            Set.of(ElementType.PACKAGE)),
        List.of(), Optional.of(reference("type:Owned")), new SourceSpan("a", 0, 7)));
    var diagnosed = SyntaxNode.source("a", annotationPath, JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.Annotation.withArgumentFailure(SyntaxValue.AnnotationForm.SINGLE_ELEMENT,
            header, SyntaxValue.AnnotationLocation.DECLARATION, Set.of(ElementType.PACKAGE),
            "frontend.unavailable-annotation-attribution"),
        List.of(), Optional.of(reference("type:Owned")), new SourceSpan("a", 0, 7));

    // Assert
    assertTrue(missing.getMessage().contains("captured source headers"));
    assertEquals(Optional.of("frontend.unavailable-annotation-attribution"),
        diagnosed.value(SyntaxValue.Annotation.class).argumentFailureCode());
  }

  @Test
  void givenUnresolvedConstantName_whenConstructed_thenReferenceIsRejected() {
    // Arrange
    var annotationPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0);
    var valuePath = annotationPath.child(SyntaxRole.ANNOTATION_ARGUMENT, 0).child(SyntaxRole.VALUE, 0);
    var intType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"),
        new JavaTypeStructure.Primitive(JavaTypeCategory.INT));
    var unresolved = new SemanticReference(ReferenceKind.FIELD, ResolutionStatus.UNRESOLVED, Optional.empty(),
        Optional.empty(), List.of(), List.of(), Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", valuePath,
        JavaSourceKind.IDENTIFIER, SyntaxKind.ANNOTATION_NAME_REFERENCE, intType, new SyntaxValue.SourceSymbol("COUNT"),
        List.of(), Optional.of(unresolved), new SourceSpan("a", 3, 8)));

    // Assert
    assertTrue(error.getMessage().contains("resolved constant-variable"));
  }

  @Test
  void givenScalarPairOutsideAnnotationRange_whenConstructed_thenRangeIsRejected() {
    // Arrange
    var annotationPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0);
    var pair = scalarArgument(annotationPath, new AnnotationScalar.IntValue(7), "type:Owned#value()", Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", annotationPath,
        JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.SINGLE_ELEMENT,
            List.of(new SyntaxValue.AnnotationArgumentHeader(Optional.empty())), SyntaxValue.AnnotationLocation.DECLARATION,
            Set.of(ElementType.PACKAGE)),
        List.of(new SyntaxChild(SyntaxRole.ANNOTATION_ARGUMENT, 0, pair)), Optional.of(reference("type:Owned")),
        new SourceSpan("a", 0, 3)));

    // Assert
    assertTrue(error.getMessage().contains("range"));
  }

  @Test
  void givenDuplicateNamedScalarPairs_whenAnnotationIsConstructed_thenSelectionIsRejected() {
    // Arrange
    var annotationPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0);
    var first = namedScalarArgument(annotationPath, 0, "x", "7", 2);
    var second = namedScalarArgument(annotationPath, 1, "x", "8", 6);
    var headers = List.of(new SyntaxValue.AnnotationArgumentHeader(Optional.of("x")),
        new SyntaxValue.AnnotationArgumentHeader(Optional.of("x")));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", annotationPath,
        JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.NAMED_NORMAL, headers, SyntaxValue.AnnotationLocation.DECLARATION,
            Set.of(ElementType.PACKAGE)),
        List.of(new SyntaxChild(SyntaxRole.ANNOTATION_ARGUMENT, 0, first),
            new SyntaxChild(SyntaxRole.ANNOTATION_ARGUMENT, 1, second)),
        Optional.of(reference("type:Owned")), new SourceSpan("a", 0, 10)));

    // Assert
    assertTrue(error.getMessage().contains("distinct elements"));
  }

  @Test
  void givenDeferredAnnotationWithScalarPair_whenConstructed_thenPairIsRejected() {
    // Arrange
    var annotationPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0);
    var pair = scalarArgument(annotationPath, new AnnotationScalar.IntValue(7), "type:Owned#value()", Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", annotationPath,
        JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION, SyntaxType.NotApplicable.INSTANCE,
        SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.ANNOTATION_ARGUMENT, 0, pair)),
        Optional.empty(), new SourceSpan("a", 0, 7)));

    // Assert
    assertTrue(error.getMessage().contains("requires its captured written form"));
  }

  @Test
  void givenNamedPairWithValueBeforeName_whenConstructed_thenSourceOrderIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0)
        .child(SyntaxRole.ANNOTATION_ARGUMENT, 0);
    var intType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"),
        new JavaTypeStructure.Primitive(JavaTypeCategory.INT));
    var literal = SyntaxNode.source("a", path.child(SyntaxRole.VALUE, 0), JavaSourceKind.INT_LITERAL,
        SyntaxKind.LITERAL, intType, new SyntaxValue.Literal("7"), List.of(), Optional.empty(),
        new SourceSpan("a", 3, 4));
    var name = SyntaxNode.source("a", path.child(SyntaxRole.ANNOTATION_ELEMENT_NAME, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.ANNOTATION_ELEMENT_NAME, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol("x"),
        List.of(), Optional.empty(), new SourceSpan("a", 5, 6));
    var selectedKey = "type:Owned#x()";
    var selected = new SemanticReference(ReferenceKind.METHOD, ResolutionStatus.RESOLVED, Optional.of(selectedKey),
        Optional.empty(), List.of(), List.of(), Optional.empty());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", path,
        JavaSourceKind.ASSIGNMENT, SyntaxKind.ANNOTATION_ARGUMENT, intType,
        new SyntaxValue.AnnotationArgument(Optional.of("x"), selectedKey, new AnnotationScalar.IntValue(7)),
        List.of(new SyntaxChild(SyntaxRole.VALUE, 0, literal), new SyntaxChild(SyntaxRole.ANNOTATION_ELEMENT_NAME, 0, name)),
        Optional.of(selected), new SourceSpan("a", 2, 7)));

    // Assert
    assertTrue(error.getMessage().contains("name must precede its value"));
  }

  @Test
  void givenAnnotationCastTypeOutsideExpression_whenConstructed_thenRangeIsRejected() {
    // Arrange
    var valuePath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0)
        .child(SyntaxRole.ANNOTATION_ARGUMENT, 0).child(SyntaxRole.VALUE, 0);
    var intType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"),
        new JavaTypeStructure.Primitive(JavaTypeCategory.INT));
    var operand = SyntaxNode.source("a", valuePath.child(SyntaxRole.TYPE_OPERAND, 0), JavaSourceKind.PRIMITIVE_TYPE,
        SyntaxKind.TYPE_USE, intType, new SyntaxValue.PrimitiveType(JavaTypeCategory.INT), List.of(), Optional.empty(),
        new SourceSpan("a", 8, 9));
    var literal = SyntaxNode.source("a", valuePath.child(SyntaxRole.EXPRESSION, 0), JavaSourceKind.INT_LITERAL,
        SyntaxKind.LITERAL, intType, new SyntaxValue.Literal("7"), List.of(), Optional.empty(), new SourceSpan("a", 5, 6));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", valuePath,
        JavaSourceKind.TYPE_CAST, SyntaxKind.ANNOTATION_CAST, intType, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.TYPE_OPERAND, 0, operand), new SyntaxChild(SyntaxRole.EXPRESSION, 0, literal)),
        Optional.empty(), new SourceSpan("a", 3, 7)));

    // Assert
    assertTrue(error.getMessage().contains("ranges must be contained"));
  }

  @Test
  void givenAnnotationArrayTypeComponentOutsideOperand_whenConstructed_thenRangeIsRejected() {
    // Arrange
    var operandPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0)
        .child(SyntaxRole.ANNOTATION_ARGUMENT, 0).child(SyntaxRole.VALUE, 0).child(SyntaxRole.TYPE_OPERAND, 0);
    var componentStructure = new JavaTypeStructure.Primitive(JavaTypeCategory.INT);
    var componentType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"),
        componentStructure);
    var component = SyntaxNode.source("a", operandPath.child(SyntaxRole.COMPONENT_TYPE, 0), JavaSourceKind.PRIMITIVE_TYPE,
        SyntaxKind.TYPE_USE, componentType, new SyntaxValue.PrimitiveType(JavaTypeCategory.INT), List.of(), Optional.empty(),
        new SourceSpan("a", 8, 9));
    var arrayType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "int[]", "int[]"),
        new JavaTypeStructure.Array(componentStructure));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", operandPath,
        JavaSourceKind.ARRAY_TYPE, SyntaxKind.TYPE_USE, arrayType, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.COMPONENT_TYPE, 0, component)), Optional.empty(), new SourceSpan("a", 4, 7)));

    // Assert
    assertTrue(error.getMessage().contains("ranges must be contained"));
  }

  @Test
  void givenPairSourceKindContradictsWrittenForm_whenConstructed_thenBothFormsAreRejected() {
    // Arrange
    var annotationPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0);

    // Act
    var named = assertThrows(IllegalArgumentException.class, () -> scalarArgument(annotationPath,
        new AnnotationScalar.IntValue(7), "type:Owned#value()", Optional.of("value"), JavaSourceKind.INT_LITERAL));
    var shorthand = assertThrows(IllegalArgumentException.class, () -> scalarArgument(annotationPath,
        new AnnotationScalar.IntValue(7), "type:Owned#value()", Optional.empty(), JavaSourceKind.ASSIGNMENT));

    // Assert
    assertTrue(named.getMessage().contains("assignment source kind"));
    assertTrue(shorthand.getMessage().contains("shorthand annotation pair"));
  }

  @Test
  void givenSameSnapshotWithChangedAnnotationFacts_whenAggregatesAreCompared_thenFactsDetermineEquality() {
    // Arrange
    var captured = snapshot("a");
    var original = AggregateFixture.builder(captured)
        .add(scalarPackageContribution(new AnnotationScalar.IntValue(7), "7")).seal();
    var changedScalar = AggregateFixture.builder(captured)
        .add(scalarPackageContribution(new AnnotationScalar.IntValue(8), "7")).seal();
    var changedExpression = AggregateFixture.builder(captured)
        .add(scalarPackageContribution(new AnnotationScalar.IntValue(7), "8")).seal();
    var repeated = AggregateFixture.builder(captured)
        .add(scalarPackageContribution(new AnnotationScalar.IntValue(7), "7")).seal();

    // Act
    var scalarEqual = original.equals(changedScalar);
    var expressionEqual = original.equals(changedExpression);

    // Assert
    assertSame(captured, original.snapshot());
    assertSame(captured, changedScalar.snapshot());
    assertSame(captured, changedExpression.snapshot());
    assertEquals(original, repeated);
    assertTrue(!scalarEqual);
    assertTrue(!expressionEqual);
  }

  @Test
  void givenScalarPairWithConflictingDeclarationEdge_whenAggregateIsSealed_thenReferenceIsRejected() {
    // Arrange
    var base = scalarPackageContribution(new AnnotationScalar.IntValue(7), "7");
    var pair = base.root().requiredChild(SyntaxRole.PACKAGE_DECLARATION).requiredChild(SyntaxRole.ANNOTATION)
        .requiredChild(SyntaxRole.ANNOTATION_ARGUMENT);
    var conflicting = new SemanticReference(ReferenceKind.METHOD, ResolutionStatus.RESOLVED,
        Optional.of("type:Owned#other()"), Optional.empty(), List.of(), List.of(), Optional.empty());
    var extra = new SyntaxSemanticEdge(pair.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(conflicting));

    // Act
    var error = failure(snapshot("a"), contribution(base.root(), base.structuralEdges(), append(base.semanticEdges(), extra)));

    // Assert
    assertTrue(error.getMessage().contains("annotation value reference must match"));
    assertTrue(error.getMessage().contains(pair.nodeId()));
  }

  @Test
  void givenScalarPairWithWrongCategory_whenConstructed_thenElementTypeConflictIsRejected() {
    // Arrange
    var annotationPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0);

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> scalarArgument(annotationPath, new AnnotationScalar.StringValue("seven"), "type:Owned#value()", Optional.empty()));

    // Assert
    assertTrue(error.getMessage().contains("scalar category conflicts"));
  }

  @Test
  void givenNamedScalarPairWithShorthandHeader_whenAnnotationIsConstructed_thenFormMismatchIsRejected() {
    // Arrange
    var annotationPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.ANNOTATION, 0);
    var pair = scalarArgument(annotationPath, new AnnotationScalar.IntValue(7), "type:Owned#value()", Optional.of("value"));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", annotationPath,
        JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.SINGLE_ELEMENT,
            List.of(new SyntaxValue.AnnotationArgumentHeader(Optional.empty())), SyntaxValue.AnnotationLocation.DECLARATION,
            Set.of(ElementType.PACKAGE)),
        List.of(new SyntaxChild(SyntaxRole.ANNOTATION_ARGUMENT, 0, pair)), Optional.of(reference("type:Owned")),
        new SourceSpan("a", 0, 8)));

    // Assert
    assertTrue(error.getMessage().contains("captured source header"));
  }

  @Test
  void givenUntypedCompilationUnitRoot_whenAggregateIsSealed_thenRootIsRejected() {
    // Arrange
    var untyped = SyntaxNode.source("a", SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT,
        SyntaxKind.UNSUPPORTED, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported("COMPILATION_UNIT"), List.of(),
        Optional.empty(), new SourceSpan("a", 0, CONTENT.length()));

    // Act
    var error = failure(snapshot("a"), contribution(untyped));

    // Assert
    assertTrue(error.getMessage().contains("AST source root must be a compilation unit"));
  }

  @Test
  void givenSynthesizedTypeOnSourceChildEdge_whenAggregateIsSealed_thenMissingSourceRangeIsRejected() {
    // Arrange
    var rootPath = SyntaxStructuralPath.sourceFileRoot();
    var type = SyntaxNode.withoutDirectSource("a", rootPath.child(SyntaxRole.TYPE_DECLARATION, 0), JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.TypeDeclaration("Generated", "type:Generated", Set.of(), Set.of(),
            new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 0)),
        List.of(), Optional.empty());
    var root = SyntaxNode.source("a", rootPath, JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE),
        List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, type)), Optional.empty(),
        new SourceSpan("a", 0, CONTENT.length()));

    // Act
    var error = failure(snapshot("a"), contribution(root));

    // Assert
    assertTrue(error.getMessage().contains("AST node lacks a required source range"));
  }

  @Test
  void givenCompactUnitWithoutMethod_whenConstructed_thenRootIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot();
    var field = SyntaxNode.source("a", path.child(SyntaxRole.COMPACT_MEMBER, 0), JavaSourceKind.CLASS,
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.TypeDeclaration("Example", "type:Example", Set.of(), Set.of(),
            new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 0)), List.of(),
        Optional.empty(), new SourceSpan("a", 1, 2));

    // Act
    var empty = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", path,
        JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.COMPACT, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE), List.of(), Optional.empty(),
        new SourceSpan("a", 0, CONTENT.length())));
    var fieldOnly = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", path,
        JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.COMPACT, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE),
        List.of(new SyntaxChild(SyntaxRole.COMPACT_MEMBER, 0, field)), Optional.empty(),
        new SourceSpan("a", 0, CONTENT.length())));

    // Assert
    assertTrue(empty.getMessage().contains("written method member"));
    assertTrue(fieldOnly.getMessage().contains("written method member"));
  }

  @Test
  void givenTwoWrittenPackages_whenCompilationUnitIsConstructed_thenCardinalityIsRejected() {
    // Arrange
    var rootPath = SyntaxStructuralPath.sourceFileRoot();
    var first = SyntaxNode.source("a", rootPath.child(SyntaxRole.PACKAGE_DECLARATION, 0), JavaSourceKind.PACKAGE,
        SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.PackageDeclaration("first"),
        List.of(), Optional.empty(), new SourceSpan("a", 0, 1));
    var second = SyntaxNode.source("a", rootPath.child(SyntaxRole.PACKAGE_DECLARATION, 1), JavaSourceKind.PACKAGE,
        SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.PackageDeclaration("second"),
        List.of(), Optional.empty(), new SourceSpan("a", 1, 2));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", rootPath,
        JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE),
        List.of(new SyntaxChild(SyntaxRole.PACKAGE_DECLARATION, 0, first),
            new SyntaxChild(SyntaxRole.PACKAGE_DECLARATION, 1, second)),
        Optional.empty(), new SourceSpan("a", 0, CONTENT.length())));

    // Assert
    assertTrue(error.getMessage().contains("PACKAGE_DECLARATION"));
  }

  @Test
  void givenPackageNodeInImportRole_whenCompilationUnitIsConstructed_thenRoleKindMismatchIsRejected() {
    // Arrange
    var rootPath = SyntaxStructuralPath.sourceFileRoot();
    var packageNode = SyntaxNode.source("a", rootPath.child(SyntaxRole.IMPORT_DECLARATION, 0), JavaSourceKind.PACKAGE,
        SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.PackageDeclaration("sample"),
        List.of(), Optional.empty(), new SourceSpan("a", 0, 1));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", rootPath,
        JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE),
        List.of(new SyntaxChild(SyntaxRole.IMPORT_DECLARATION, 0, packageNode)), Optional.empty(),
        new SourceSpan("a", 0, CONTENT.length())));

    // Assert
    assertEquals("Compilation-unit child kind does not match its role", error.getMessage());
  }

  @Test
  void givenModularRootWithoutModule_whenConstructed_thenRequiredModuleIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot();

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", path,
        JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.MODULAR, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE), List.of(), Optional.empty(),
        new SourceSpan("a", 0, CONTENT.length())));

    // Assert
    assertTrue(error.getMessage().contains("MODULE_DECLARATION"));
  }

  @Test
  void givenTwoModuleChildren_whenConstructed_thenModuleCardinalityIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot();
    var first = module("a", path.child(SyntaxRole.MODULE_DECLARATION, 0), List.of());
    var second = module("a", path.child(SyntaxRole.MODULE_DECLARATION, 1), List.of());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source("a", path,
        JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.MODULAR, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE),
        List.of(new SyntaxChild(SyntaxRole.MODULE_DECLARATION, 0, first),
            new SyntaxChild(SyntaxRole.MODULE_DECLARATION, 1, second)), Optional.empty(),
        new SourceSpan("a", 0, CONTENT.length())));

    // Assert
    assertTrue(error.getMessage().contains("MODULE_DECLARATION"));
  }

  @Test
  void givenDuplicateModuleDirectivePosition_whenConstructed_thenPositionIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.MODULE_DECLARATION, 0);
    var first = requires("a", path.child(SyntaxRole.MODULE_DIRECTIVE, 0));
    var second = requires("a", path.child(SyntaxRole.MODULE_DIRECTIVE, 1));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> module("a", path,
        List.of(new SyntaxChild(SyntaxRole.MODULE_DIRECTIVE, 0, first),
            new SyntaxChild(SyntaxRole.MODULE_DIRECTIVE, 0, second))));

    // Assert
    assertTrue(error.getMessage().contains("contiguous"));
  }

  @Test
  void givenCrossSourceModuleDirective_whenAggregateIsSealed_thenOwnershipIsRejected() {
    // Arrange
    var rootPath = SyntaxStructuralPath.sourceFileRoot();
    var modulePath = rootPath.child(SyntaxRole.MODULE_DECLARATION, 0);
    var foreign = requires("b", modulePath.child(SyntaxRole.MODULE_DIRECTIVE, 0));
    var declaredModule = module("a", modulePath, List.of(new SyntaxChild(SyntaxRole.MODULE_DIRECTIVE, 0, foreign)));
    var root = SyntaxNode.source("a", rootPath, JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.MODULAR, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE),
        List.of(new SyntaxChild(SyntaxRole.MODULE_DECLARATION, 0, declaredModule)), Optional.empty(),
        new SourceSpan("a", 0, CONTENT.length()));

    // Act
    var error = failure(snapshot("a", "b"), contribution(root));

    // Assert
    assertTrue(error.getMessage().contains("not owned by contribution"));
  }

  @Test
  void givenQualifiedExportWithoutTargets_whenConstructed_thenInvalidDirectiveIsRejected() {
    // Arrange
    var packageName = new SyntaxValue.QualifiedName(List.of("sample", "api"));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> new SyntaxValue.Exports(packageName, Optional.of(List.of())));

    // Assert
    assertTrue(error.getMessage().contains("at least one target"));
  }

  @Test
  void givenAggregateConstructionBoundary_whenConstructorsAreInspected_thenPublicReadersCannotConstructValues() {
    // Arrange
    var aggregateConstructors = JavaAstAggregate.class.getDeclaredConstructors();
    var builderConstructors = AggregateFixture.class.getDeclaredConstructors();

    // Act
    var aggregatePrivate = List.of(aggregateConstructors).stream()
        .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers()) || Modifier.isProtected(constructor.getModifiers()));
    var builderPrivate = List.of(builderConstructors).stream().allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));

    // Assert
    assertTrue(aggregatePrivate);
    assertTrue(builderPrivate);
    assertTrue(List.of(JavaAstAggregate.class.getDeclaredClasses()).stream()
        .noneMatch(type -> Modifier.isPublic(type.getModifiers())));
    assertTrue(List.of(JavaAstAggregate.class.getDeclaredMethods()).stream().noneMatch(method -> method.getName().equals("builder")));
  }

  @Test
  void givenEmptySnapshot_whenAggregateIsSealed_thenAllQueriesAreEmpty() {
    // Arrange
    var snapshot = snapshot();

    // Act
    var aggregate = AggregateFixture.builder(snapshot).seal();

    // Assert
    assertEquals(List.of(), aggregate.roots());
    assertEquals(List.of(), aggregate.nodes());
    assertEquals(List.of(), aggregate.nodeTuples());
    assertEquals(List.of(), aggregate.rangeTuples());
    assertEquals(List.of(), aggregate.structuralEdges());
    assertEquals(List.of(), aggregate.semanticEdges());
    assertThrows(UnsupportedOperationException.class, () -> aggregate.roots().add(root("a", List.of())));
  }

  @Test
  void givenTwoSourcesAndReversedArrival_whenAggregateIsSealed_thenRootsFollowSnapshotOrder() {
    // Arrange
    var snapshot = snapshot("a", "b");
    var first = root("a", List.of());
    var second = root("b", List.of());

    // Act
    var aggregate = AggregateFixture.builder(snapshot).add(contribution(second)).add(contribution(first)).seal();

    // Assert
    assertEquals(List.of(first.nodeId(), second.nodeId()), aggregate.orderedNodeKeys());
    assertEquals(List.of(first, second), aggregate.roots());
    assertEquals(List.of(first.nodeTuple(), second.nodeTuple()), aggregate.nodeTuples());
    assertEquals(List.of(first.rangeTuple().orElseThrow(), second.rangeTuple().orElseThrow()), aggregate.rangeTuples());
  }

  @Test
  void givenSameFactsInDifferentInsertionOrders_whenSealed_thenCanonicalTuplesAndValuesAreEqual() {
    // Arrange
    var snapshot = snapshot("a", "b");
    var path = SyntaxStructuralPath.sourceFileRoot();
    var statement = type("a", path.child(SyntaxRole.TYPE_DECLARATION, 1));
    var member = type("a", path.child(SyntaxRole.TYPE_DECLARATION, 0));
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, member),
        new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 1, statement)));
    var secondRoot = root("b", List.of());
    var sourceTarget = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.SourceOccurrence(member.nodeId()));
    var declarationTarget = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));
    var childTarget = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("other")));
    var memberDeclaration = declarationEdge(member);
    var statementDeclaration = declarationEdge(statement);
    var first = new SyntaxSourceContribution("a", root, root.descendantsAndSelf(), root.structuralEdges(),
        List.of(childTarget, statementDeclaration, declarationTarget, memberDeclaration, sourceTarget));
    var reversed = new SyntaxSourceContribution("a", root, List.of(member, statement, root),
        List.of(root.structuralEdges().get(1), root.structuralEdges().get(0)),
        List.of(sourceTarget, memberDeclaration, declarationTarget, statementDeclaration, childTarget));

    // Act
    var left = AggregateFixture.builder(snapshot).add(first).add(contribution(secondRoot)).seal();
    var right = AggregateFixture.builder(snapshot).add(contribution(secondRoot)).add(reversed).seal();

    // Assert
    assertEquals(left, right);
    assertEquals(left.hashCode(), right.hashCode());
    assertEquals(List.of(root.nodeId(), member.nodeId(), statement.nodeId(), secondRoot.nodeId()), left.orderedNodeKeys());
    assertEquals(List.of(root.nodeTuple(), member.nodeTuple(), statement.nodeTuple(), secondRoot.nodeTuple()), left.nodeTuples());
    assertEquals(List.of(root.rangeTuple().orElseThrow(), member.rangeTuple().orElseThrow(), statement.rangeTuple().orElseThrow(),
        secondRoot.rangeTuple().orElseThrow()), left.rangeTuples());
    assertEquals(root.structuralEdges(), left.structuralEdges());
    assertEquals(5, left.semanticEdges().size());
    assertTrue(left.semanticEdges().containsAll(List.of(sourceTarget, declarationTarget, childTarget)));
    assertEquals(left.nodeTuples(), right.nodeTuples());
    assertEquals(left.rangeTuples(), right.rangeTuples());
    assertEquals(left.structuralEdges(), right.structuralEdges());
    assertEquals(left.semanticEdges(), right.semanticEdges());
    assertSame(statement, right.node(statement.nodeId()).orElseThrow());
    assertTrue(right.node("absent").isEmpty());
  }

  @Test
  void givenExplicitSemanticTargets_whenEdgesAreAdded_thenStructuralTuplesRemainIdentical() {
    // Arrange
    var snapshot = snapshot("a");
    var child = type("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0));
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, child)));
    var basic = contribution(root);
    var local = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.SourceOccurrence(child.nodeId()));
    var external = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));
    var childDeclaration = declarationEdge(child);

    // Act
    var without = AggregateFixture.builder(snapshot).add(basic).seal();
    var with = AggregateFixture.builder(snapshot).add(new SyntaxSourceContribution("a", root, basic.nodes(),
        basic.structuralEdges(), List.of(external, childDeclaration, local))).seal();

    // Assert
    assertEquals(without.structuralEdges(), with.structuralEdges());
    assertEquals(without.nodeTuples(), with.nodeTuples());
    assertEquals(List.of(local, external, childDeclaration).stream().sorted(Comparator.comparing(SyntaxSemanticEdge::sourceKey)).toList(),
        with.semanticEdges());
    assertNotEquals(without, with);
  }

  @Test
  void givenMutableCallerCollections_whenAggregateIsSealed_thenMutationCannotChangeOwnedFacts() {
    // Arrange
    var sourceFiles = new ArrayList<>(List.of(new SourceFile("a", capturedPath(Path.of("a.java")), "digest-a", CONTENT)));
    var snapshot = new InputSnapshot(capturedAbsolute(Path.of("sources")), "SHA-256", "snapshot", sourceFiles);
    var child = type("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0));
    var children = new ArrayList<>(List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, child)));
    var root = root("a", children);
    var structuralEdge = root.structuralEdges().getFirst();
    var semanticEdge = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));
    var nodes = new ArrayList<>(List.of(root, child));
    var structural = new ArrayList<>(List.of(structuralEdge));
    var childDeclaration = declarationEdge(child);
    var semantic = new ArrayList<>(List.of(semanticEdge, childDeclaration));
    var contribution = new SyntaxSourceContribution("a", root, nodes, structural, semantic);
    var expectedChild = type("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0));
    var expectedRoot = root("a", List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, expectedChild)));
    var expected = AggregateFixture.builder(snapshot("a")).add(new SyntaxSourceContribution("a", expectedRoot,
        List.of(expectedRoot, expectedChild), expectedRoot.structuralEdges(), List.of(semanticEdge, childDeclaration))).seal();

    // Act
    var aggregate = AggregateFixture.builder(snapshot).add(contribution).seal();
    sourceFiles.clear();
    children.clear();
    nodes.clear();
    structural.clear();
    structural.add(new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.TYPE_DECLARATION, 0, "absent"));
    semantic.clear();
    semantic.add(new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.SourceOccurrence("absent")));

    // Assert
    assertEquals(expected, aggregate);
    assertEquals(expected.snapshot().sourceFiles(), aggregate.snapshot().sourceFiles());
    assertEquals(List.of(root), aggregate.roots());
    assertEquals(List.of(root, child), aggregate.nodes());
    assertEquals(List.of(root.nodeTuple(), child.nodeTuple()), aggregate.nodeTuples());
    assertEquals(List.of(root.rangeTuple().orElseThrow(), child.rangeTuple().orElseThrow()), aggregate.rangeTuples());
    assertEquals(List.of(structuralEdge), aggregate.structuralEdges());
    assertEquals(List.of(semanticEdge, childDeclaration).stream().sorted(Comparator.comparing(SyntaxSemanticEdge::sourceKey)).toList(),
        aggregate.semanticEdges());
    assertThrows(UnsupportedOperationException.class, () -> aggregate.structuralEdges().add(structural.getFirst()));
    assertThrows(UnsupportedOperationException.class, () -> aggregate.nodes().clear());
  }

  @Test
  void givenDuplicateNodeKey_whenAggregateIsSealed_thenDuplicateIsRejected() {
    // Arrange
    var snapshot = snapshot("a");
    var root = root("a", List.of());
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, root), List.of(), List.of());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> AggregateFixture.builder(snapshot).add(contribution).seal());

    // Assert
    assertTrue(error.getMessage().contains("Duplicate AST node key: " + root.nodeId()));
  }

  @Test
  void givenMissingStructuralParent_whenAggregateIsSealed_thenEndpointIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxStructuralEdge("absent", SyntaxRole.STATEMENT, 0, root.nodeId());

    // Act
    var error = failure(snapshot("a"), contribution(root, List.of(edge), List.of()));

    // Assert
    assertTrue(error.getMessage().contains("structural parent absent"));
  }

  @Test
  void givenMissingStructuralChild_whenAggregateIsSealed_thenEndpointIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, "absent");

    // Act
    var error = failure(snapshot("a"), contribution(root, List.of(edge), List.of()));

    // Assert
    assertTrue(error.getMessage().contains("structural child absent"));
  }

  @Test
  void givenMissingSemanticSource_whenAggregateIsSealed_thenEndpointIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxSemanticEdge("absent", SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));

    // Act
    var error = failure(snapshot("a"), contribution(root, List.of(), List.of(edge)));

    // Assert
    assertTrue(error.getMessage().contains("semantic source absent"));
  }

  @Test
  void givenDanglingSemanticOccurrence_whenAggregateIsSealed_thenTypedTargetIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.SourceOccurrence("absent"));

    // Act
    var error = failure(snapshot("a"), contribution(root, List.of(), List.of(edge)));

    // Assert
    assertTrue(error.getMessage().contains("semantic target absent"));
  }

  @Test
  void givenTwoStructuralParents_whenAggregateIsSealed_thenChildIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot();
    var first = type("a", path.child(SyntaxRole.TYPE_DECLARATION, 0));
    var second = type("a", path.child(SyntaxRole.TYPE_DECLARATION, 1));
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, first),
        new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 1, second)));
    var edges = new ArrayList<>(root.structuralEdges());
    edges.add(new SyntaxStructuralEdge(second.nodeId(), SyntaxRole.BODY, 0, first.nodeId()));

    // Act
    var error = failure(snapshot("a"), contribution(root, edges, List.of()));

    // Assert
    assertTrue(error.getMessage().contains("exactly one structural parent: " + first.nodeId() + " count 2"));
  }

  @Test
  void givenParentlessNonRoot_whenAggregateIsSealed_thenMissingParentIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var orphan = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, orphan), List.of(), List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("exactly one structural parent: " + orphan.nodeId() + " count 0"));
  }

  @Test
  void givenRootWithStructuralParent_whenAggregateIsSealed_thenRootParentIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var other = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var edge = new SyntaxStructuralEdge(other.nodeId(), SyntaxRole.BODY, 0, root.nodeId());
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, other), List.of(edge), List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("source root has a structural parent: " + root.nodeId()));
  }

  @Test
  void givenSelfCycle_whenAggregateIsSealed_thenCycleIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, root.nodeId());

    // Act
    var error = failure(snapshot("a"), contribution(root, List.of(edge), List.of()));

    // Assert
    assertTrue(error.getMessage().contains("structural cycle involves node " + root.nodeId()));
  }

  @Test
  void givenMultiNodeCycle_whenAggregateIsSealed_thenCycleIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var first = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var second = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 1));
    var edges = List.of(new SyntaxStructuralEdge(first.nodeId(), SyntaxRole.BODY, 0, second.nodeId()),
        new SyntaxStructuralEdge(second.nodeId(), SyntaxRole.BODY, 0, first.nodeId()));
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, first, second), edges, List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("AST structural cycle involves node"));
    assertTrue(error.getMessage().contains(first.nodeId()) || error.getMessage().contains(second.nodeId()));
  }

  @Test
  void givenNegativeRolePosition_whenEdgeIsConstructed_thenParentRoleAndPositionAreReported() {
    // Arrange
    var parent = "parent";

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxStructuralEdge(parent, SyntaxRole.STATEMENT, -1, "child"));

    // Assert
    assertTrue(error.getMessage().contains("parent"));
    assertTrue(error.getMessage().contains("STATEMENT"));
    assertTrue(error.getMessage().contains("-1"));
  }

  @Test
  void givenDuplicateRolePosition_whenAggregateIsSealed_thenParentAndRoleAreReported() {
    // Arrange
    var root = root("a", List.of());
    var first = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var second = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.MEMBER, 0));
    var edges = List.of(new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, first.nodeId()),
        new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, second.nodeId()));
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, first, second), edges, List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("parent " + root.nodeId() + " and role STATEMENT"));
  }

  @Test
  void givenGapInRolePositions_whenAggregateIsSealed_thenExpectedPositionIsReported() {
    // Arrange
    var root = root("a", List.of());
    var first = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var second = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 2));
    var edges = List.of(new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, first.nodeId()),
        new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 2, second.nodeId()));
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, first, second), edges, List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("parent " + root.nodeId() + " and role STATEMENT: expected 1"));
  }

  @Test
  void givenSourceOutsideSnapshot_whenContributionIsAdded_thenSourceIsRejected() {
    // Arrange
    var builder = AggregateFixture.builder(snapshot("a"));
    var unknown = contribution(root("b", List.of()));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder.add(unknown));

    // Assert
    assertTrue(error.getMessage().contains("captured snapshot: b"));
    assertTrue(error.getMessage().contains(unknown.root().nodeId()));
  }

  @Test
  void givenRootOwnedByAnotherSource_whenAggregateIsSealed_thenContributionAndNodeAreReported() {
    // Arrange
    var wrongRoot = root("b", List.of());
    var wrongContribution = new SyntaxSourceContribution("a", wrongRoot, List.of(wrongRoot), List.of(), List.of());

    // Act
    var error = failure(snapshot("a"), wrongContribution);

    // Assert
    assertTrue(error.getMessage().contains("source a"));
    assertTrue(error.getMessage().contains(wrongRoot.nodeId()));
  }

  @Test
  void givenNodeOwnedByAnotherSource_whenAggregateIsSealed_thenContributionAndNodeAreReported() {
    // Arrange
    var first = root("a", List.of());
    var foreign = leaf("b", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var contribution = new SyntaxSourceContribution("a", first, List.of(first, foreign), List.of(), List.of());

    // Act
    var error = failure(snapshot("a", "b"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("contribution a"));
    assertTrue(error.getMessage().contains(foreign.nodeId()));
    assertTrue(error.getMessage().contains("belongs to b"));
  }

  @Test
  void givenStructuralEdgeFiledUnderAnotherSource_whenAggregateIsSealed_thenContributionIsRejected() {
    // Arrange
    var first = root("a", List.of());
    var child = type("b", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0));
    var second = root("b", List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, child)));
    var edge = second.structuralEdges().getFirst();
    var misfiled = new SyntaxSourceContribution("a", first, List.of(first), List.of(edge), List.of());
    var missing = new SyntaxSourceContribution("b", second, List.of(second, child), List.of(), List.of());

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> AggregateFixture.builder(snapshot("a", "b")).add(misfiled).add(missing).seal());

    // Assert
    assertTrue(error.getMessage().contains("structural parent " + second.nodeId()));
    assertTrue(error.getMessage().contains("source b, not contribution a"));
  }

  @Test
  void givenSemanticEdgeFiledUnderAnotherSource_whenAggregateIsSealed_thenContributionIsRejected() {
    // Arrange
    var first = root("a", List.of());
    var second = root("b", List.of());
    var edge = new SyntaxSemanticEdge(second.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));
    var misfiled = new SyntaxSourceContribution("a", first, List.of(first), List.of(), List.of(edge));

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> AggregateFixture.builder(snapshot("a", "b")).add(misfiled).add(contribution(second)).seal());

    // Assert
    assertTrue(error.getMessage().contains("semantic source " + second.nodeId()));
    assertTrue(error.getMessage().contains("source b, not contribution a"));
  }

  @Test
  void givenCrossSourceStructuralEdge_whenAggregateIsSealed_thenBothOwnersAreReported() {
    // Arrange
    var first = root("a", List.of());
    var second = root("b", List.of());
    var edge = new SyntaxStructuralEdge(first.nodeId(), SyntaxRole.STATEMENT, 0, second.nodeId());
    var firstContribution = contribution(first, List.of(edge), List.of());

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> AggregateFixture.builder(snapshot("a", "b")).add(firstContribution).add(contribution(second)).seal());

    // Assert
    assertTrue(error.getMessage().contains("crosses source owners a and b"));
  }

  @Test
  void givenRangeEndBeyondContent_whenAggregateIsSealed_thenNodeAndOffsetsAreReported() {
    // Arrange
    var root = SyntaxNode.source("a", SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT,
        SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE), List.of(),
        Optional.empty(), new SourceSpan("a", 0, CONTENT.length() + 1));

    // Act
    var error = failure(snapshot("a"), contribution(root));

    // Assert
    assertTrue(error.getMessage().contains("source a for node " + root.nodeId()));
    assertTrue(error.getMessage().contains("[0, 11) of 10"));
  }

  @Test
  void givenNegativeStart_whenRangeIsConstructed_thenOffsetsAreRejected() {
    // Arrange
    var start = -1;

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> new SourceSpan("a", start, 0));

    // Assert
    assertTrue(error.getMessage().contains("a: [-1, 0)"));
  }

  @Test
  void givenEndBeforeStart_whenRangeIsConstructed_thenOffsetsAreRejected() {
    // Arrange
    var end = 1;

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> new SourceSpan("a", 2, end));

    // Assert
    assertTrue(error.getMessage().contains("a: [2, 1)"));
  }

  @Test
  void givenDuplicateStructuralTuple_whenAggregateIsSealed_thenTupleIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var child = leaf("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));
    var edge = new SyntaxStructuralEdge(root.nodeId(), SyntaxRole.STATEMENT, 0, child.nodeId());
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, child), List.of(edge, edge), List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("Duplicate AST structural edge tuple"));
    assertTrue(error.getMessage().contains(child.nodeId()));
  }

  @Test
  void givenDuplicateSemanticTuple_whenAggregateIsSealed_thenTupleIsRejected() {
    // Arrange
    var root = root("a", List.of());
    var edge = new SyntaxSemanticEdge(root.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("external")));
    var contribution = contribution(root, List.of(), List.of(edge, edge));

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("Duplicate AST semantic edge tuple"));
  }

  @Test
  void givenMissingSourceRoot_whenAggregateIsSealed_thenCapturedSourceIsReported() {
    // Arrange
    var builder = AggregateFixture.builder(snapshot("a", "b")).add(contribution(root("a", List.of())));

    // Act
    var error = assertThrows(IllegalArgumentException.class, builder::seal);

    // Assert
    assertTrue(error.getMessage().contains("Missing AST source root for captured source: b"));
  }

  @Test
  void givenDuplicateSourceContribution_whenSecondIsAdded_thenSourceIsRejected() {
    // Arrange
    var contribution = contribution(root("a", List.of()));
    var builder = AggregateFixture.builder(snapshot("a")).add(contribution);

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder.add(contribution));

    // Assert
    assertTrue(error.getMessage().contains("Duplicate AST source contribution: a"));
  }

  @Test
  void givenChildWithEqualKeyButDifferentInstance_whenAggregateIsSealed_thenUnownedInstanceIsRejected() {
    // Arrange
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var embedded = type("a", path);
    var registered = type("a", path);
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, embedded)));
    var contribution = new SyntaxSourceContribution("a", root, List.of(root, registered), root.structuralEdges(), List.of());

    // Act
    var error = failure(snapshot("a"), contribution);

    // Assert
    assertTrue(error.getMessage().contains("child is not the identical owned node: " + embedded.nodeId()));
  }

  @Test
  void givenDifferentEmbeddedReference_whenAggregatesAreCompared_thenNodeFactsRemainDistinct() {
    // Arrange
    var snapshot = snapshot("a");
    var typePath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var memberPath = typePath.child(SyntaxRole.MEMBER, 0);
    var bodyPath = memberPath.child(SyntaxRole.BODY, 0);
    var statementPath = bodyPath.child(SyntaxRole.STATEMENT, 0);
    var reference = reference("external");
    var plainStatement = SyntaxNode.source("a", statementPath, JavaSourceKind.WHILE_LOOP, SyntaxKind.UNSUPPORTED,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported("WHILE_LOOP"), List.of(), Optional.empty(),
        new SourceSpan("a", 1, 2));
    var linkedStatement = SyntaxNode.source("a", statementPath, JavaSourceKind.WHILE_LOOP, SyntaxKind.UNSUPPORTED,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported("WHILE_LOOP"), List.of(), Optional.of(reference),
        new SourceSpan("a", 1, 2));
    var plainBody = SyntaxNode.source("a", bodyPath, JavaSourceKind.BLOCK, SyntaxKind.BLOCK,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, plainStatement)), Optional.empty(), new SourceSpan("a", 1, 2));
    var linkedBody = SyntaxNode.source("a", bodyPath, JavaSourceKind.BLOCK, SyntaxKind.BLOCK,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, linkedStatement)), Optional.empty(), new SourceSpan("a", 1, 2));
    var plainMember = SyntaxNode.source("a", memberPath, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, constructorValue(),
        List.of(new SyntaxChild(SyntaxRole.BODY, 0, plainBody)), Optional.empty(), new SourceSpan("a", 1, 2));
    var linkedMember = SyntaxNode.source("a", memberPath, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, constructorValue(),
        List.of(new SyntaxChild(SyntaxRole.BODY, 0, linkedBody)), Optional.empty(), new SourceSpan("a", 1, 2));
    var plainType = SyntaxNode.source("a", typePath, JavaSourceKind.CLASS, SyntaxKind.TYPE_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, typeValue(1),
        List.of(new SyntaxChild(SyntaxRole.MEMBER, 0, plainMember)), Optional.empty(), new SourceSpan("a", 1, 2));
    var linkedType = SyntaxNode.source("a", typePath, JavaSourceKind.CLASS, SyntaxKind.TYPE_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, typeValue(1),
        List.of(new SyntaxChild(SyntaxRole.MEMBER, 0, linkedMember)), Optional.empty(), new SourceSpan("a", 1, 2));
    var plain = root("a", List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, plainType)));
    var linked = root("a", List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, linkedType)));
    var edge = linkedStatement.semanticEdges().getFirst();

    // Act
    var left = AggregateFixture.builder(snapshot).add(new SyntaxSourceContribution("a", plain, plain.descendantsAndSelf(),
        plain.descendantsAndSelf().stream().flatMap(node -> node.structuralEdges().stream()).toList(),
        List.of(edge, declarationEdge(plainMember), declarationEdge(plainType)))).seal();
    var right = AggregateFixture.builder(snapshot).add(contribution(linked)).seal();

    // Assert
    assertEquals(left.semanticEdges(), right.semanticEdges());
    assertNotEquals(left, right);
  }

  @Test
  void givenSourceBody_whenRegistered_thenMethodViewResolvesIdenticalOwnedOccurrence() {
    // Arrange
    var typePath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var methodPath = typePath.child(SyntaxRole.MEMBER, 0);
    var body = block("a", methodPath.child(SyntaxRole.BODY, 0));
    var method = SyntaxNode.source("a", methodPath, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, constructorValue(), List.of(new SyntaxChild(SyntaxRole.BODY, 0, body)),
        Optional.empty(), new SourceSpan("a", 1, 2));
    var type = SyntaxNode.source("a", typePath, JavaSourceKind.CLASS, SyntaxKind.TYPE_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, typeValue(1), List.of(new SyntaxChild(SyntaxRole.MEMBER, 0, method)),
        Optional.empty(), new SourceSpan("a", 1, 2));
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, type)));
    var methodKey = constructorValue().declarationKey();

    // Act
    var aggregate = AggregateFixture.builder(snapshot("a")).add(contribution(root))
        .addMethodBody(methodKey, body, MethodBodyOrigin.SOURCE_AST).seal();

    // Assert
    assertSame(body, aggregate.methodSyntax(methodKey).orElseThrow().root());
    assertSame(body, aggregate.node(body.nodeId()).orElseThrow());
    assertEquals(MethodBodyOrigin.SOURCE_AST, aggregate.methodBodyOrigin(methodKey).orElseThrow());
  }

  @Test
  void givenMethodBodyWithUnavailableCompositeStructure_whenReadinessIsDerived_thenBodyIsUnresolved() {
    // Arrange
    var typePath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var methodPath = typePath.child(SyntaxRole.MEMBER, 0);
    var bodyPath = methodPath.child(SyntaxRole.BODY, 0);
    var localPath = bodyPath.child(SyntaxRole.STATEMENT, 0);
    var localReference = new SemanticReference(ReferenceKind.LOCAL, ResolutionStatus.RESOLVED,
        Optional.of(constructorValue().declarationKey() + ":local_variable:0:0"), Optional.empty(), List.of(), List.of(), Optional.empty());
    var local = SyntaxNode.source("a", localPath, JavaSourceKind.VARIABLE, SyntaxKind.LOCAL_DECLARATION,
        new SyntaxType.StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.UNION, "First | Second", "First")),
        new SyntaxValue.LocalDeclaration("problem", Optional.empty(), List.of(), Optional.empty()), List.of(), Optional.of(localReference), new SourceSpan("a", 1, 2));
    var body = SyntaxNode.source("a", bodyPath, JavaSourceKind.BLOCK, SyntaxKind.BLOCK,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, local)), Optional.empty(), new SourceSpan("a", 1, 2));
    var method = SyntaxNode.source("a", methodPath, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, constructorValue(), List.of(new SyntaxChild(SyntaxRole.BODY, 0, body)),
        Optional.empty(), new SourceSpan("a", 1, 2));
    var owner = SyntaxNode.source("a", typePath, JavaSourceKind.CLASS, SyntaxKind.TYPE_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, typeValue(1), List.of(new SyntaxChild(SyntaxRole.MEMBER, 0, method)),
        Optional.empty(), new SourceSpan("a", 1, 2));
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, owner)));
    var methodKey = constructorValue().declarationKey();
    var aggregate = AggregateFixture.builder(snapshot("a")).add(contribution(root))
        .addMethodBody(methodKey, body, MethodBodyOrigin.SOURCE_AST).seal();

    // Act
    var readiness = CfgAdmission.classify(aggregate.methodSyntax(methodKey).orElseThrow());

    // Assert
    assertEquals(CfgReadiness.UNRESOLVED_SEMANTICS, readiness);
  }

  @Test
  void givenAuxiliaryBody_whenRegistered_thenSourceAstQueriesExcludeItsNodes() {
    // Arrange
    var root = root("a", List.of());
    var body = block("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));

    // Act
    var aggregate = AggregateFixture.builder(snapshot("a")).add(contribution(root))
        .addMethodBody("method", body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY).seal();

    // Assert
    assertSame(body, aggregate.methodSyntax("method").orElseThrow().root());
    assertTrue(aggregate.node(body.nodeId()).isEmpty());
    assertEquals(List.of(root), aggregate.nodes());
    assertEquals(MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, aggregate.methodBodyOrigin("method").orElseThrow());
  }

  @Test
  void givenUnownedSourceBody_whenRegisteredAsSource_thenSealRejectsIt() {
    // Arrange
    var root = root("a", List.of());
    var body = block("a", SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0));

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> AggregateFixture.builder(snapshot("a"))
        .add(contribution(root)).addMethodBody("method", body, MethodBodyOrigin.SOURCE_AST).seal());

    // Assert
    assertTrue(error.getMessage().contains("identical aggregate occurrence: method"));
  }

  @Test
  void givenAnnotationWithoutTarget_whenAggregateIsSealed_thenCardinalityIsRejected() {
    // Arrange
    var root = annotatedPackageRoot("a");

    // Act
    var error = failure(snapshot("a"), contribution(root));

    // Assert
    assertTrue(error.getMessage().contains("annotation requires exactly one typed application"));
    assertTrue(error.getMessage().contains(root.requiredChild(SyntaxRole.PACKAGE_DECLARATION)
        .requiredChild(SyntaxRole.ANNOTATION).nodeId()));
  }

  @Test
  void givenAnnotationWithWrongOrDuplicateTarget_whenAggregateIsSealed_thenOwnerInvariantIsRejected() {
    // Arrange
    var root = annotatedPackageRoot("a");
    var packageNode = root.requiredChild(SyntaxRole.PACKAGE_DECLARATION);
    var annotation = packageNode.requiredChild(SyntaxRole.ANNOTATION);
    var base = contribution(root);
    var correct = new SyntaxSemanticEdge(annotation.nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET,
        AnnotationAttachmentAdmission.forAttachment(packageNode, annotation, Optional.empty(), List.of()));
    var wrong = new SyntaxSemanticEdge(annotation.nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET,
        new SyntaxSemanticTarget.AnnotationApplication(root.nodeId(), Optional.of(root.nodeId()), Optional.empty(), List.of(), List.of()));
    var extraType = new SyntaxSemanticEdge(annotation.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference("type:Other")));

    // Act
    var wrongError = failure(snapshot("a"), contribution(root, base.structuralEdges(),
        append(base.semanticEdges(), wrong)));
    var duplicateError = failure(snapshot("a"), contribution(root, base.structuralEdges(),
        append(append(base.semanticEdges(), correct), wrong)));
    var typeError = failure(snapshot("a"), contribution(root, base.structuralEdges(),
        append(append(base.semanticEdges(), correct), extraType)));

    // Assert
    assertTrue(wrongError.getMessage().contains("does not match its exact source location"));
    assertTrue(duplicateError.getMessage().contains("exactly one typed application"));
    assertTrue(typeError.getMessage().contains("annotation type reference must match"));
    assertTrue(wrongError.getMessage().contains(annotation.nodeId()));
    assertTrue(duplicateError.getMessage().contains(annotation.nodeId()));
    assertTrue(typeError.getMessage().contains(annotation.nodeId()));
  }

  @Test
  void givenAnnotationTargetInForeignSource_whenAggregateIsSealed_thenSourceOwnershipIsRejected() {
    // Arrange
    var root = annotatedPackageRoot("a");
    var other = root("b", List.of());
    var annotation = root.requiredChild(SyntaxRole.PACKAGE_DECLARATION).requiredChild(SyntaxRole.ANNOTATION);
    var base = contribution(root);
    var foreign = new SyntaxSemanticEdge(annotation.nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET,
        new SyntaxSemanticTarget.AnnotationApplication(other.nodeId(), Optional.of(other.nodeId()), Optional.empty(), List.of(), List.of()));
    var linked = contribution(root, base.structuralEdges(), append(base.semanticEdges(), foreign));

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> AggregateFixture.builder(snapshot("a", "b")).add(linked).add(contribution(other)).seal());

    // Assert
    assertTrue(error.getMessage().contains("does not match its exact source location"));
    assertTrue(error.getMessage().contains(annotation.nodeId()));
  }

  @Test
  void givenInvalidAnnotationRanges_whenAggregateIsSealed_thenEachSourceViolationIsRejected() {
    // Arrange
    var empty = annotatedPackageRoot("a", List.of(new SourceSpan("a", 2, 2)), reference("type:Annotation"));
    var outside = annotatedPackageRoot("a", List.of(new SourceSpan("a", 0, 1)), reference("type:Annotation"));
    var unordered = annotatedPackageRoot("a", List.of(new SourceSpan("a", 5, 7), new SourceSpan("a", 2, 4)),
        reference("type:Annotation"));
    var duplicate = annotatedPackageRoot("a", List.of(new SourceSpan("a", 2, 4), new SourceSpan("a", 2, 4)),
        reference("type:Annotation"));

    // Act
    var emptyError = failure(snapshot("a"), contribution(empty));
    var outsideError = failure(snapshot("a"), contribution(outside));
    var unorderedError = failure(snapshot("a"), contribution(unordered));
    var duplicateError = failure(snapshot("a"), contribution(duplicate));

    // Assert
    assertTrue(emptyError.getMessage().contains("nonempty, contained, and ordered"));
    assertTrue(outsideError.getMessage().contains("nonempty, contained, and ordered"));
    assertTrue(unorderedError.getMessage().contains("nonempty, contained, and ordered"));
    assertTrue(duplicateError.getMessage().contains("Duplicate AST written annotation range"));
    assertTrue(emptyError.getMessage().contains(empty.requiredChild(SyntaxRole.PACKAGE_DECLARATION)
        .requiredChild(SyntaxRole.ANNOTATION).nodeId()));
    assertTrue(outsideError.getMessage().contains(outside.requiredChild(SyntaxRole.PACKAGE_DECLARATION)
        .requiredChild(SyntaxRole.ANNOTATION).nodeId()));
    assertTrue(unorderedError.getMessage().contains(unordered.requiredChild(SyntaxRole.PACKAGE_DECLARATION)
        .children(SyntaxRole.ANNOTATION).get(1).nodeId()));
    assertTrue(duplicateError.getMessage().contains(duplicate.requiredChild(SyntaxRole.PACKAGE_DECLARATION)
        .children(SyntaxRole.ANNOTATION).get(1).nodeId()));
  }

  @Test
  void givenUnresolvedAnnotationTypeWithDeclarationTarget_whenAggregateIsSealed_thenLocationIsRejected() {
    // Arrange
    var unresolved = new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(),
        List.of(), List.of(), Optional.empty());
    var root = annotatedPackageRoot("a", List.of(new SourceSpan("a", 2, 4)), unresolved);
    var packageNode = root.requiredChild(SyntaxRole.PACKAGE_DECLARATION);
    var annotation = packageNode.requiredChild(SyntaxRole.ANNOTATION);
    var base = contribution(root);
    var target = new SyntaxSemanticEdge(annotation.nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET,
        AnnotationAttachmentAdmission.forAttachment(packageNode, annotation, Optional.empty(), List.of()));

    // Act
    var error = failure(snapshot("a"), contribution(root, base.structuralEdges(), append(base.semanticEdges(), target)));

    // Assert
    assertTrue(error.getMessage().contains("requires a resolved annotation type"));
    assertTrue(error.getMessage().contains(annotation.nodeId()));
  }

  private static SyntaxNode annotatedPackageRoot(String sourceKey) {
    return annotatedPackageRoot(sourceKey, List.of(new SourceSpan(sourceKey, 2, 4)), reference("type:Annotation"));
  }

  private static SyntaxSourceContribution scalarPackageContribution(AnnotationScalar scalar, String literalText) {
    var rootPath = SyntaxStructuralPath.sourceFileRoot();
    var packagePath = rootPath.child(SyntaxRole.PACKAGE_DECLARATION, 0);
    var annotationPath = packagePath.child(SyntaxRole.ANNOTATION, 0);
    var pair = scalarArgument(annotationPath, scalar, "type:Owned#value()", Optional.empty(), JavaSourceKind.INT_LITERAL,
        literalText);
    var annotation = SyntaxNode.source("a", annotationPath, JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION,
        SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.SINGLE_ELEMENT,
            List.of(new SyntaxValue.AnnotationArgumentHeader(Optional.empty())), SyntaxValue.AnnotationLocation.DECLARATION,
            Set.of(ElementType.PACKAGE)),
        List.of(new SyntaxChild(SyntaxRole.ANNOTATION_ARGUMENT, 0, pair)), Optional.of(reference("type:Owned")),
        new SourceSpan("a", 2, 5));
    var declaration = SyntaxNode.source("a", packagePath, JavaSourceKind.PACKAGE, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.PackageDeclaration("sample"),
        List.of(new SyntaxChild(SyntaxRole.ANNOTATION, 0, annotation)), Optional.empty(), new SourceSpan("a", 1, 9));
    var root = root("a", List.of(new SyntaxChild(SyntaxRole.PACKAGE_DECLARATION, 0, declaration)));
    var base = contribution(root);
    var target = new SyntaxSemanticEdge(annotation.nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET,
        AnnotationAttachmentAdmission.forAttachment(declaration, annotation, Optional.empty(), List.of()));
    return contribution(root, base.structuralEdges(), append(base.semanticEdges(), target));
  }

  private static SyntaxNode annotatedPackageRoot(String sourceKey, List<SourceSpan> ranges, SemanticReference reference) {
    var path = SyntaxStructuralPath.sourceFileRoot();
    var packagePath = path.child(SyntaxRole.PACKAGE_DECLARATION, 0);
    var annotations = new ArrayList<SyntaxChild>();
    for (var index = 0; index < ranges.size(); index++) {
      var annotation = SyntaxNode.source(sourceKey, packagePath.child(SyntaxRole.ANNOTATION, index), JavaSourceKind.ANNOTATION,
          SyntaxKind.ANNOTATION, SyntaxType.NotApplicable.INSTANCE,
          new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.MARKER, List.of(), SyntaxValue.AnnotationLocation.DECLARATION,
              Set.of(ElementType.PACKAGE)),
          List.of(), Optional.of(reference), ranges.get(index));
      annotations.add(new SyntaxChild(SyntaxRole.ANNOTATION, index, annotation));
    }
    var declaration = SyntaxNode.source(sourceKey, packagePath, JavaSourceKind.PACKAGE, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.PackageDeclaration("sample"), annotations, Optional.empty(),
        new SourceSpan(sourceKey, 1, 9));
    return root(sourceKey, List.of(new SyntaxChild(SyntaxRole.PACKAGE_DECLARATION, 0, declaration)));
  }

  private static List<SyntaxSemanticEdge> append(List<SyntaxSemanticEdge> existing, SyntaxSemanticEdge added) {
    var result = new ArrayList<>(existing);
    result.add(added);
    return result;
  }

  private static IllegalArgumentException failure(InputSnapshot snapshot, SyntaxSourceContribution contribution) {
    return assertThrows(IllegalArgumentException.class, () -> AggregateFixture.builder(snapshot).add(contribution).seal());
  }

  private static InputSnapshot snapshot(String... sourceKeys) {
    var files = new ArrayList<SourceFile>();
    for (var key : sourceKeys) {
      files.add(new SourceFile(key, capturedPath(Path.of(key + ".java")), "digest-" + key, CONTENT));
    }
    return new InputSnapshot(capturedAbsolute(Path.of("sources")), "SHA-256", "snapshot", files);
  }

  private static SyntaxNode root(String sourceKey, List<SyntaxChild> children) {
    return SyntaxNode.source(sourceKey, SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT,
        SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE), children,
        Optional.empty(), new SourceSpan(sourceKey, 0, CONTENT.length()));
  }

  private static SyntaxNode module(String sourceKey, SyntaxStructuralPath path, List<SyntaxChild> children) {
    return SyntaxNode.source(sourceKey, path, JavaSourceKind.MODULE, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.ModuleDeclaration(new SyntaxValue.QualifiedName(List.of("sample")), false),
        children, Optional.empty(), new SourceSpan(sourceKey, 1, 9));
  }

  private static SyntaxNode requires(String sourceKey, SyntaxStructuralPath path) {
    return SyntaxNode.source(sourceKey, path, JavaSourceKind.REQUIRES, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Requires(new SyntaxValue.QualifiedName(List.of("java", "base")), false, false),
        List.of(), Optional.empty(), new SourceSpan(sourceKey, 2, 8));
  }

  private static SyntaxNode leaf(String sourceKey, SyntaxStructuralPath path) {
    return SyntaxNode.source(sourceKey, path, JavaSourceKind.EMPTY_STATEMENT, SyntaxKind.EMPTY,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(),
        new SourceSpan(sourceKey, 1, 2));
  }

  private static SyntaxNode annotationElement(SyntaxNode returnType, Optional<AnnotationElementValue> defaultValue,
      List<SyntaxNode> expressions) {
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, 0);
    var value = new SyntaxValue.ExecutableDeclaration("value", "type:Owned", "type:Owned#value()",
        SyntaxValue.ExecutableDeclaration.ExecutableKind.ANNOTATION_ELEMENT, Set.of(), Set.of(),
        new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, true, 0, false, false),
        SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), defaultValue));
    var children = new ArrayList<SyntaxChild>();
    children.add(new SyntaxChild(SyntaxRole.RETURN_TYPE, 0, returnType));
    for (var index = 0; index < expressions.size(); index++) {
      children.add(new SyntaxChild(SyntaxRole.DEFAULT_VALUE, index, expressions.get(index)));
    }
    return SyntaxNode.source("a", path, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        value, children, Optional.empty(), new SourceSpan("a", 0, 10));
  }

  private static SyntaxNode type(String sourceKey, SyntaxStructuralPath path) {
    return SyntaxNode.source(sourceKey, path, JavaSourceKind.CLASS, SyntaxKind.TYPE_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, typeValue(0), List.of(), Optional.empty(),
        new SourceSpan(sourceKey, 1, 2));
  }

  private static SyntaxValue.TypeDeclaration typeValue(int members) {
    return new SyntaxValue.TypeDeclaration("Type", "type:Type", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, members));
  }

  private static SyntaxValue.ExecutableDeclaration constructorValue() {
    return new SyntaxValue.ExecutableDeclaration("Type", "type:Type", "type:Type#Type()",
        SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR,
        Set.of(), Set.of(), new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 0, false, 0, false, 0, true, false),
        SyntaxValue.ExecutableDeclaration.AttributedHeader.withoutReceiver(List.of(), Optional.empty()));
  }

  private static SyntaxNode block(String sourceKey, SyntaxStructuralPath path) {
    return SyntaxNode.source(sourceKey, path, JavaSourceKind.BLOCK, SyntaxKind.BLOCK,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(),
        new SourceSpan(sourceKey, 1, 2));
  }

  private static SyntaxSourceContribution contribution(SyntaxNode root) {
    var nodes = root.descendantsAndSelf();
    var structural = nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList();
    var semantic = nodes.stream().flatMap(node -> switch (node.syntaxKind()) {
      case TYPE_DECLARATION, METHOD_DECLARATION -> List.of(declarationEdge(node)).stream();
      default -> node.semanticEdges().stream();
    }).toList();
    return new SyntaxSourceContribution(root.sourceFileKey(), root, nodes, structural, semantic);
  }

  private static SyntaxSemanticEdge declarationEdge(SyntaxNode node) {
    var kind = node.syntaxKind() == SyntaxKind.TYPE_DECLARATION ? ReferenceKind.TYPE : ReferenceKind.CONSTRUCTOR;
    var key = node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        ? node.value(SyntaxValue.TypeDeclaration.class).declarationKey()
        : node.value(SyntaxValue.ExecutableDeclaration.class).declarationKey();
    return new SyntaxSemanticEdge(node.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(new SemanticReference(kind, ResolutionStatus.RESOLVED, Optional.of(key), Optional.empty(),
            List.of(), List.of(), Optional.empty())));
  }

  private static SyntaxSourceContribution contribution(SyntaxNode root, List<SyntaxStructuralEdge> structural,
      List<SyntaxSemanticEdge> semantic) {
    return new SyntaxSourceContribution(root.sourceFileKey(), root, root.descendantsAndSelf(), structural, semantic);
  }

  private static SemanticReference reference(String key) {
    return new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of(key), Optional.empty(),
        List.of(), List.of(), Optional.empty());
  }

  private static SyntaxNode scalarArgument(SyntaxStructuralPath annotationPath, AnnotationScalar scalar, String selectedKey,
      Optional<String> writtenName) {
    return scalarArgument(annotationPath, scalar, selectedKey, writtenName,
        writtenName.isPresent() ? JavaSourceKind.ASSIGNMENT : JavaSourceKind.INT_LITERAL);
  }

  private static SyntaxNode scalarArgument(SyntaxStructuralPath annotationPath, AnnotationScalar scalar, String selectedKey,
      Optional<String> writtenName, JavaSourceKind sourceKind) {
    return scalarArgument(annotationPath, scalar, selectedKey, writtenName, sourceKind, "7");
  }

  private static SyntaxNode scalarArgument(SyntaxStructuralPath annotationPath, AnnotationScalar scalar, String selectedKey,
      Optional<String> writtenName, JavaSourceKind sourceKind, String literalText) {
    var path = annotationPath.child(SyntaxRole.ANNOTATION_ARGUMENT, 0);
    var valuePath = path.child(SyntaxRole.VALUE, 0);
    var intType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"),
        new JavaTypeStructure.Primitive(JavaTypeCategory.INT));
    var value = SyntaxNode.source("a", valuePath, JavaSourceKind.INT_LITERAL, SyntaxKind.LITERAL, intType,
        new SyntaxValue.Literal(literalText), List.of(), Optional.empty(), new SourceSpan("a", writtenName.isPresent() ? 7 : 3,
            writtenName.isPresent() ? 8 : 4));
    var children = new ArrayList<SyntaxChild>();
    writtenName.ifPresent(name -> children.add(new SyntaxChild(SyntaxRole.ANNOTATION_ELEMENT_NAME, 0,
        SyntaxNode.source("a", path.child(SyntaxRole.ANNOTATION_ELEMENT_NAME, 0), JavaSourceKind.IDENTIFIER,
            SyntaxKind.ANNOTATION_ELEMENT_NAME, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol(name),
            List.of(), Optional.empty(), new SourceSpan("a", 2, 7)))));
    children.add(new SyntaxChild(SyntaxRole.VALUE, 0, value));
    var selected = new SemanticReference(ReferenceKind.METHOD, ResolutionStatus.RESOLVED, Optional.of(selectedKey),
        Optional.empty(), List.of(), List.of(), Optional.empty());
    return SyntaxNode.source("a", path, sourceKind,
        SyntaxKind.ANNOTATION_ARGUMENT, intType, new SyntaxValue.AnnotationArgument(writtenName, selectedKey, scalar),
        children, Optional.of(selected), new SourceSpan("a", writtenName.isPresent() ? 2 : 3,
            writtenName.isPresent() ? 8 : 4));
  }

  private static SyntaxNode namedScalarArgument(SyntaxStructuralPath annotationPath, int ordinal, String name,
      String literalText, int start) {
    var path = annotationPath.child(SyntaxRole.ANNOTATION_ARGUMENT, ordinal);
    var intType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"),
        new JavaTypeStructure.Primitive(JavaTypeCategory.INT));
    var nameNode = SyntaxNode.source("a", path.child(SyntaxRole.ANNOTATION_ELEMENT_NAME, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.ANNOTATION_ELEMENT_NAME, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol(name),
        List.of(), Optional.empty(), new SourceSpan("a", start, start + 1));
    var literal = SyntaxNode.source("a", path.child(SyntaxRole.VALUE, 0), JavaSourceKind.INT_LITERAL,
        SyntaxKind.LITERAL, intType, new SyntaxValue.Literal(literalText), List.of(), Optional.empty(),
        new SourceSpan("a", start + 2, start + 3));
    var selectedKey = "type:Owned#" + name + "()";
    var selected = new SemanticReference(ReferenceKind.METHOD, ResolutionStatus.RESOLVED, Optional.of(selectedKey),
        Optional.empty(), List.of(), List.of(), Optional.empty());
    return SyntaxNode.source("a", path, JavaSourceKind.ASSIGNMENT, SyntaxKind.ANNOTATION_ARGUMENT, intType,
        new SyntaxValue.AnnotationArgument(Optional.of(name), selectedKey,
            new AnnotationScalar.IntValue(Integer.parseInt(literalText))),
        List.of(new SyntaxChild(SyntaxRole.ANNOTATION_ELEMENT_NAME, 0, nameNode), new SyntaxChild(SyntaxRole.VALUE, 0, literal)),
        Optional.of(selected), new SourceSpan("a", start, start + 3));
  }
}
