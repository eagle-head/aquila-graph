package org.aquilagraph.syntax;

import static org.aquilagraph.syntax.MethodDecisionFixtures.BODY_PATH;
import static org.aquilagraph.syntax.MethodDecisionFixtures.METHOD;
import static org.aquilagraph.syntax.MethodDecisionFixtures.block;
import static org.aquilagraph.syntax.MethodDecisionFixtures.builder;
import static org.aquilagraph.syntax.MethodDecisionFixtures.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.junit.jupiter.api.Test;

class MethodSyntaxCatchReadinessTest {
  private static final JavaTypeIdentity EXCEPTION = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.Exception", "java.lang.Exception");

  @Test
  void givenResolvedQualifiedCatchWithoutSelectedValueSlots_whenReadinessIsDerived_thenItsBodyIsLowerable() {
    // Arrange
    var body = catchBody(new SyntaxType.Resolved(EXCEPTION), Optional.of(reference(ResolutionStatus.RESOLVED, List.of("java.lang.Exception"))), false);

    // Act
    var readiness = readiness(body);

    // Assert
    assertEquals(SyntaxReadiness.LOWERABLE, readiness);
  }

  @Test
  void givenResolvedCatchWithAbsentOrBlankQualifiedAlternatives_whenReadinessIsDerived_thenRelationshipUnknownCannotStandInForTypeResolution() {
    // Arrange
    var alternatives = List.of(List.<String>of(), List.of(" "));

    // Act and Assert
    for (var names : alternatives) {
      var body = catchBody(new SyntaxType.Resolved(EXCEPTION), Optional.of(reference(ResolutionStatus.RESOLVED, names)), false);
      assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, readiness(body));
    }
  }

  @Test
  void givenCatchParameterWithoutDeclarationReference_whenSyntaxIsBuilt_thenQualifiedStaticTypeAloneCannotEnterTheBody() {
    // Arrange, Act and Assert
    assertThrows(IllegalArgumentException.class, () -> catchBody(new SyntaxType.Resolved(EXCEPTION), Optional.empty(), false));
  }

  @Test
  void givenCatchWithoutResolvedParameterType_whenReadinessIsDerived_thenQualifiedReferenceAloneCannotAdmitAHandler() {
    // Arrange
    var body = catchBody(SyntaxType.Unresolved.INSTANCE, Optional.of(reference(ResolutionStatus.RESOLVED, List.of("java.lang.Exception"))), false);

    // Act
    var readiness = readiness(body);

    // Assert
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, readiness);
  }

  @Test
  void givenErroneousCatchAndMissingAlternatives_whenReadinessIsDerived_thenErrorPrecedesMissingResolution() {
    // Arrange
    var body = catchBody(new SyntaxType.Resolved(EXCEPTION), Optional.of(reference(ResolutionStatus.ERROR, List.of())), false);

    // Act
    var readiness = readiness(body);

    // Assert
    assertEquals(SyntaxReadiness.ERRONEOUS_SEMANTICS, readiness);
  }

  @Test
  void givenUnsupportedBodyAndErroneousCatchWithoutAlternatives_whenReadinessIsDerived_thenUnsupportedPrecedesBothSemanticRefusals() {
    // Arrange
    var body = catchBody(new SyntaxType.Resolved(EXCEPTION), Optional.of(reference(ResolutionStatus.ERROR, List.of())), true);

    // Act
    var readiness = readiness(body);

    // Assert
    assertEquals(SyntaxReadiness.UNSUPPORTED, readiness);
  }

  private static SyntaxReadiness readiness(SyntaxNode body) {
    return builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION).seal().methodSyntax(METHOD).orElseThrow().readiness();
  }

  private static SyntaxNode catchBody(SyntaxType type, Optional<SemanticReference> reference, boolean unsupported) {
    var tryPath = BODY_PATH.child(SyntaxRole.STATEMENT, 0);
    var catchPath = tryPath.child(SyntaxRole.CATCH_CLAUSE, 0);
    var parameter = node(catchPath.child(SyntaxRole.PARAMETER, 0), JavaSourceKind.VARIABLE, SyntaxKind.LOCAL_DECLARATION, type,
        new SyntaxValue.LocalDeclaration("failure"), List.of(), reference);
    var handler = node(catchPath, JavaSourceKind.CATCH, SyntaxKind.CATCH, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.PARAMETER, 0, parameter),
            new SyntaxChild(SyntaxRole.BODY, 0, block(catchPath.child(SyntaxRole.BODY, 0), List.of()))), Optional.empty());
    var tryNode = node(tryPath, JavaSourceKind.TRY, SyntaxKind.TRY, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.TRY_BLOCK, 0, block(tryPath.child(SyntaxRole.TRY_BLOCK, 0), List.of())), new SyntaxChild(SyntaxRole.CATCH_CLAUSE, 0, handler)),
        Optional.empty());
    if (!unsupported) {
      return block(BODY_PATH, List.of(tryNode));
    }
    var unsupportedNode = node(BODY_PATH.child(SyntaxRole.STATEMENT, 1), JavaSourceKind.WHILE_LOOP, SyntaxKind.UNSUPPORTED,
        SyntaxType.Unsupported.INSTANCE, new SyntaxValue.Unsupported("WHILE_LOOP"), List.of(), Optional.empty());
    return block(BODY_PATH, List.of(tryNode, unsupportedNode));
  }

  private static SemanticReference reference(ResolutionStatus status, List<String> alternatives) {
    return new SemanticReference(ReferenceKind.LOCAL, status, status == ResolutionStatus.RESOLVED ? Optional.of("failure") : Optional.empty(),
        Optional.of(EXCEPTION.erasedName()), alternatives, List.of(EXCEPTION.erasedName(), "java.lang.Throwable", "java.lang.Object"));
  }
}
