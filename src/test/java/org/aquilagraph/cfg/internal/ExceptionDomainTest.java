package org.aquilagraph.cfg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.InvocationMode;
import org.aquilagraph.source.api.InvocationSemantics;
import org.aquilagraph.source.api.InvocationSelection;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;

class ExceptionDomainTest {
  private static final String PROBLEM = "sample.Problem";
  private static final JavaTypeIdentity PROBLEM_TYPE = new JavaTypeIdentity(JavaTypeCategory.DECLARED, PROBLEM, PROBLEM);
  private static final SyntaxStructuralPath PATH = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.EXPRESSION, 0);

  @Test
  void givenExactTypeWithCopiedAncestors_whenCreated_thenQualifiedNonNullDomainAndImmutableHierarchyAreRetained() {
    // Arrange
    var ancestors = new ArrayList<>(List.of("sample.Parent", "java.lang.Throwable", "sample.Parent"));

    // Act
    var effect = ExceptionType.resolved(PROBLEM, ancestors);
    ancestors.clear();

    // Assert
    assertEquals(ExceptionDomainKind.EXACT, effect.domainKind());
    assertEquals(Optional.of(PROBLEM), effect.qualifiedTypeName());
    assertEquals(List.of(PROBLEM, "sample.Parent", "java.lang.Throwable"), effect.assignableToTypeNames());
    assertFalse(effect.mayBeNull());
    assertFalse(effect.unknown());
    assertEquals(List.of(effect), effect.outcomes());
    assertThrows(UnsupportedOperationException.class, () -> effect.assignableToTypeNames().clear());
  }

  @Test
  void givenNullableSubtypeBound_whenPartitioned_thenNonNullBoundPrecedesNullWithoutChangingItsCategory() {
    // Arrange
    var effect = ExceptionType.subtypeBound(PROBLEM, List.of("java.lang.Throwable"), true);

    // Act
    var outcomes = effect.outcomes();

    // Assert
    assertEquals(2, outcomes.size());
    assertEquals(ExceptionDomainKind.SUBTYPE_BOUND, outcomes.getFirst().domainKind());
    assertEquals(PROBLEM, outcomes.getFirst().typeName());
    assertEquals(List.of(PROBLEM, "java.lang.Throwable"), outcomes.getFirst().assignableToTypeNames());
    assertFalse(outcomes.getFirst().mayBeNull());
    assertEquals(ExceptionType.runtime("java.lang.NullPointerException"), outcomes.get(1));
    assertTrue(effect.mayBeNull());
    assertThrows(UnsupportedOperationException.class, () -> outcomes.clear());
  }

  @Test
  void givenNullableExactProvenance_whenPartitioned_thenExactnessAndNullPossibilityRemainIndependent() {
    // Arrange
    var effect = new ExceptionType(PROBLEM, List.of(PROBLEM), ExceptionDomainKind.EXACT, true);

    // Act
    var outcomes = effect.outcomes();

    // Assert
    assertEquals(PROBLEM, outcomes.getFirst().typeName());
    assertEquals(ExceptionDomainKind.EXACT, outcomes.getFirst().domainKind());
    assertFalse(outcomes.getFirst().mayBeNull());
    assertEquals(ExceptionType.runtime("java.lang.NullPointerException"), outcomes.get(1));
  }

  @Test
  void givenUnknownCategories_whenCreated_thenTheirDistinctPublicNamesHaveNoInventedQualifiedClassOrHierarchy() {
    // Arrange
    var categories = List.of("unchecked-invocation", "unchecked-constructor", "unchecked-class-initialization", "unchecked-string-conversion");

    // Act and Assert
    for (var category : categories) {
      var effect = ExceptionType.unknown(category);
      assertEquals(category, effect.typeName());
      assertEquals(ExceptionDomainKind.UNKNOWN, effect.domainKind());
      assertTrue(effect.qualifiedTypeName().isEmpty());
      assertTrue(effect.assignableToTypeNames().isEmpty());
      assertEquals(List.of(effect), effect.outcomes());
    }
  }

  @Test
  void givenUnknownDomainWithHierarchyOrBlankNames_whenCreated_thenContradictoryOrInvalidFactsReject() {
    // Arrange, Act and Assert
    assertThrows(IllegalArgumentException.class,
        () -> new ExceptionType("unchecked", List.of("java.lang.Exception"), ExceptionDomainKind.UNKNOWN, false));
    assertThrows(IllegalArgumentException.class, () -> ExceptionType.unknown(" "));
    assertThrows(IllegalArgumentException.class, () -> ExceptionType.resolved(PROBLEM, List.of(" ")));
    assertThrows(NullPointerException.class, () -> new ExceptionType(PROBLEM, List.of(), null, false));
  }

  @Test
  void givenKnownArrayBoundsAndAllocationEffects_whenCreated_thenTheirActualDistinctAncestorChainsAreRetained() {
    // Arrange
    var bounds = "java.lang.ArrayIndexOutOfBoundsException";
    var allocation = "java.lang.OutOfMemoryError";

    // Act
    var boundsEffect = ExceptionType.runtime(bounds);
    var allocationEffect = ExceptionType.runtime(allocation);

    // Assert
    assertEquals(List.of(bounds, "java.lang.IndexOutOfBoundsException", "java.lang.RuntimeException", "java.lang.Exception",
        "java.lang.Throwable", "java.lang.Object"), boundsEffect.assignableToTypeNames());
    assertEquals(List.of(allocation, "java.lang.VirtualMachineError", "java.lang.Error", "java.lang.Throwable", "java.lang.Object"),
        allocationEffect.assignableToTypeNames());
    assertFalse(allocationEffect.assignableToTypeNames().contains("java.lang.RuntimeException"));
  }

  @Test
  void givenUnrecognizedImplicitClass_whenCreated_thenItsNameDoesNotInventRuntimeExceptionAncestry() {
    // Arrange
    var name = "sample.CustomRuntime";

    // Act
    var effect = ExceptionType.runtime(name);

    // Assert
    assertEquals(List.of(name), effect.assignableToTypeNames());
    assertEquals(ExceptionDomainKind.EXACT, effect.domainKind());
  }

  @Test
  void givenResolvedDirectCreation_whenThrowDomainIsDerived_thenTheSuccessfulObjectIsExactAndNonNull() {
    // Arrange
    var expression = creation(PATH, ResolutionStatus.RESOLVED);

    // Act
    var effect = ExceptionType.fromSyntax(expression, PROBLEM);

    // Assert
    assertEquals(ExceptionDomainKind.EXACT, effect.domainKind());
    assertFalse(effect.mayBeNull());
    assertEquals(List.of(PROBLEM, "sample.Parent", "java.lang.Throwable"), effect.assignableToTypeNames());
  }

  @Test
  void givenParenthesizedDirectCreation_whenThrowDomainIsDerived_thenTransparentSyntaxRetainsExactNonNullProvenance() {
    // Arrange
    var child = creation(PATH.child(SyntaxRole.EXPRESSION, 0), ResolutionStatus.RESOLVED);
    var expression = SyntaxNode.withoutDirectSource("source", PATH, JavaSourceKind.PARENTHESIZED, SyntaxKind.PARENTHESIZED,
        new SyntaxType.Resolved(PROBLEM_TYPE), SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, child)),
        Optional.empty());

    // Act
    var effect = ExceptionType.fromSyntax(expression, PROBLEM);

    // Assert
    assertEquals(ExceptionType.fromSyntax(child, PROBLEM), effect);
    assertEquals(ExceptionDomainKind.EXACT, effect.domainKind());
    assertFalse(effect.mayBeNull());
  }

  @Test
  void givenResolvedReferenceExpression_whenThrowDomainIsDerived_thenStaticTypeBecomesNullableSubtypeBound() {
    // Arrange
    var expression = variable(new SyntaxType.Resolved(PROBLEM_TYPE), ResolutionStatus.RESOLVED, List.of(PROBLEM, "java.lang.Throwable"));

    // Act
    var effect = ExceptionType.fromSyntax(expression, PROBLEM);

    // Assert
    assertEquals(ExceptionDomainKind.SUBTYPE_BOUND, effect.domainKind());
    assertTrue(effect.mayBeNull());
    assertEquals(Optional.of(PROBLEM), effect.qualifiedTypeName());
  }

  @Test
  void givenReferenceWithoutHierarchy_whenThrowDomainIsDerived_thenQualifiedBoundSurvivesWithoutGuessedAncestors() {
    // Arrange
    var expression = variable(new SyntaxType.Resolved(PROBLEM_TYPE), ResolutionStatus.RESOLVED, List.of());

    // Act
    var effect = ExceptionType.fromSyntax(expression, PROBLEM);

    // Assert
    assertEquals(ExceptionDomainKind.SUBTYPE_BOUND, effect.domainKind());
    assertEquals(List.of(PROBLEM), effect.assignableToTypeNames());
    assertTrue(effect.mayBeNull());
  }

  @Test
  void givenNullTypedExpression_whenThrowDomainIsDerived_thenOnlyExactNonNullNpeOutcomeExists() {
    // Arrange
    var type = new JavaTypeIdentity(JavaTypeCategory.NULL, "<nulltype>", "<nulltype>");
    var expression = SyntaxNode.withoutDirectSource("source", PATH, JavaSourceKind.NULL_LITERAL, SyntaxKind.LITERAL,
        new SyntaxType.Resolved(type), new SyntaxValue.Literal("null"), List.of(), Optional.empty());

    // Act
    var effect = ExceptionType.fromSyntax(expression, "<nulltype>");

    // Assert
    assertEquals(ExceptionType.runtime("java.lang.NullPointerException"), effect);
    assertEquals(List.of(effect), effect.outcomes());
  }

  @Test
  void givenNullProvenanceBehindReferenceCastsAndParentheses_whenThrowDomainIsDerived_thenNoNonNullStaticBoundIsInvented() {
    // Arrange
    var target = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.Exception", "java.lang.Exception");
    var innerTarget = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.RuntimeException", "java.lang.RuntimeException");
    var innerPath = PATH.child(SyntaxRole.EXPRESSION, 0);
    var parenthesizedPath = innerPath.child(SyntaxRole.EXPRESSION, 0);
    var nullType = new JavaTypeIdentity(JavaTypeCategory.NULL, "<nulltype>", "<nulltype>");
    var nullNode = SyntaxNode.withoutDirectSource("source", parenthesizedPath.child(SyntaxRole.EXPRESSION, 0), JavaSourceKind.NULL_LITERAL,
        SyntaxKind.LITERAL, new SyntaxType.Resolved(nullType), new SyntaxValue.Literal("null"), List.of(), Optional.empty());
    var parenthesized = SyntaxNode.withoutDirectSource("source", parenthesizedPath, JavaSourceKind.PARENTHESIZED, SyntaxKind.PARENTHESIZED,
        new SyntaxType.Resolved(nullType), SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, nullNode)), Optional.empty());
    var expression = cast(PATH, target, cast(innerPath, innerTarget, parenthesized));

    // Act
    var effect = ExceptionType.fromSyntax(expression, target.erasedName());

    // Assert
    assertEquals(ExceptionType.runtime("java.lang.NullPointerException"), effect);
    assertEquals(List.of(effect), effect.outcomes());
  }

  @Test
  void givenProvenWideningReferenceCastOfDirectCreation_whenThrowDomainIsDerived_thenRuntimeClassAndNonNullnessSurviveTheStaticParentType() {
    // Arrange
    var target = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "sample.Parent", "sample.Parent");
    var child = creation(PATH.child(SyntaxRole.EXPRESSION, 0), ResolutionStatus.RESOLVED);
    var expression = cast(PATH, target, child);

    // Act
    var effect = ExceptionType.fromSyntax(expression, target.erasedName());

    // Assert
    assertEquals(ExceptionType.fromSyntax(child, PROBLEM), effect);
    assertEquals(Optional.of(PROBLEM), effect.qualifiedTypeName());
    assertEquals(ExceptionDomainKind.EXACT, effect.domainKind());
    assertFalse(effect.mayBeNull());
    assertEquals(List.of(PROBLEM), effect.outcomes().stream().map(ExceptionType::typeName).toList());
  }

  @Test
  void givenLiteralSpellingNullWithAnotherAttributedType_whenThrowDomainIsDerived_thenSpellingCannotProveNull() {
    // Arrange
    var expression = SyntaxNode.withoutDirectSource("source", PATH, JavaSourceKind.STRING_LITERAL, SyntaxKind.LITERAL,
        new SyntaxType.Resolved(PROBLEM_TYPE), new SyntaxValue.Literal("null"), List.of(), Optional.empty());

    // Act
    var effect = ExceptionType.fromSyntax(expression, PROBLEM);

    // Assert
    assertEquals(ExceptionDomainKind.SUBTYPE_BOUND, effect.domainKind());
    assertEquals(PROBLEM, effect.typeName());
    assertTrue(effect.mayBeNull());
  }

  @Test
  void givenMissingResolutionOrPrimitiveThrow_whenDomainIsDerived_thenAdmissionInvariantFailsWithoutFallbackType() {
    // Arrange
    var unresolved = variable(SyntaxType.Unresolved.INSTANCE, ResolutionStatus.RESOLVED, List.of());
    var unresolvedReference = variable(new SyntaxType.Resolved(PROBLEM_TYPE), ResolutionStatus.UNRESOLVED, List.of());
    var primitive = new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int");
    var primitiveExpression = variable(new SyntaxType.Resolved(primitive), ResolutionStatus.RESOLVED, List.of());

    // Act and Assert
    assertThrows(IllegalStateException.class, () -> ExceptionType.fromSyntax(unresolved, PROBLEM));
    assertThrows(IllegalStateException.class, () -> ExceptionType.fromSyntax(unresolvedReference, PROBLEM));
    assertThrows(IllegalStateException.class, () -> ExceptionType.fromSyntax(primitiveExpression, "int"));
    assertThrows(IllegalStateException.class, () -> ExceptionType.fromSyntax(creation(PATH, ResolutionStatus.UNRESOLVED), PROBLEM));
  }

  private static SyntaxNode creation(SyntaxStructuralPath path, ResolutionStatus status) {
    var semantics = new InvocationSemantics(InvocationMode.CONSTRUCTOR, new JavaTypeReference(PROBLEM_TYPE, Optional.of("problem")),
        InvocationForm.OBJECT_CREATION, InvocationSelection.NAMED_EXECUTABLE);
    var reference = new SemanticReference(ReferenceKind.CONSTRUCTOR, status,
        status == ResolutionStatus.RESOLVED ? Optional.of("constructor") : Optional.empty(), Optional.of(PROBLEM), List.of(PROBLEM),
        List.of(PROBLEM, "sample.Parent", "java.lang.Throwable"), Optional.empty());
    return SyntaxNode.withoutDirectSource("source", path, JavaSourceKind.NEW_CLASS, SyntaxKind.OBJECT_CREATION,
        new SyntaxType.Resolved(PROBLEM_TYPE), new SyntaxValue.Invocation("Problem", InvocationForm.OBJECT_CREATION, Optional.of(semantics)),
        List.of(), Optional.of(reference));
  }

  private static SyntaxNode cast(SyntaxStructuralPath path, JavaTypeIdentity target, SyntaxNode expression) {
    return SyntaxNode.withoutDirectSource("source", path, JavaSourceKind.TYPE_CAST, SyntaxKind.TYPE_CAST, new SyntaxType.Resolved(target),
        new SyntaxValue.TypeName(target.displayName()), List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, expression)), Optional.empty());
  }

  private static SyntaxNode variable(SyntaxType type, ResolutionStatus status, List<String> hierarchy) {
    var reference = new SemanticReference(ReferenceKind.LOCAL, status,
        status == ResolutionStatus.RESOLVED ? Optional.of("local") : Optional.empty(), Optional.of(PROBLEM), List.of(PROBLEM), hierarchy, Optional.empty());
    return SyntaxNode.withoutDirectSource("source", PATH, JavaSourceKind.IDENTIFIER, SyntaxKind.VARIABLE_ACCESS, type,
        new SyntaxValue.SourceSymbol("problem"), List.of(), Optional.of(reference));
  }
}
