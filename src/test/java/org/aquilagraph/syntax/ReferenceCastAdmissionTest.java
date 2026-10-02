package org.aquilagraph.syntax;

import static org.aquilagraph.syntax.MethodDecisionFixtures.METHOD;
import static org.aquilagraph.syntax.MethodDecisionFixtures.STRING;
import static org.aquilagraph.syntax.MethodDecisionFixtures.builder;
import static org.aquilagraph.syntax.MethodDecisionFixtures.expression;
import static org.aquilagraph.syntax.MethodDecisionFixtures.expressionBody;
import static org.aquilagraph.syntax.MethodDecisionFixtures.literal;
import static org.aquilagraph.syntax.MethodDecisionFixtures.node;
import static org.aquilagraph.syntax.MethodDecisionFixtures.returnedBody;
import static org.aquilagraph.syntax.MethodDecisionFixtures.type;
import static org.aquilagraph.syntax.MethodDecisionFixtures.use;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.junit.jupiter.api.Test;

class ReferenceCastAdmissionTest {
  @Test
  void givenAReferenceCastWithoutClassification_whenSealed_thenReadinessRefusesItWithoutChangingSyntaxIdentity() {
    // Arrange
    var target = type(JavaTypeCategory.DECLARED, "java.lang.Object");
    var body = castBody(target);
    var cast = expression(body);
    var legacy = use(cast, SyntaxRole.EXPRESSION, 0, target);
    var classified = classified(cast.nodeId(), STRING, target, Optional.of(false));

    // Act
    var missing = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(legacy)).seal();
    var present = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(classified)).seal();

    // Assert
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, missing.methodSyntax(METHOD).orElseThrow().readiness());
    assertEquals(SyntaxReadiness.LOWERABLE, present.methodSyntax(METHOD).orElseThrow().readiness());
    assertEquals(Optional.of(false), present.methodSyntax(METHOD).orElseThrow()
        .useDecision(cast.nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow().referenceCheckRequired());
    assertEquals(missing, present);
    assertEquals(missing.hashCode(), present.hashCode());
    assertEquals(missing.methodSyntax(METHOD), present.methodSyntax(METHOD));
  }

  @Test
  void givenAClassificationOnAReturnOperand_whenSealed_thenTheNonCastConsumerIsRejected() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(STRING));
    var returned = body.requiredChild(SyntaxRole.STATEMENT);
    var decision = classified(returned.nodeId(), STRING, STRING, Optional.of(false));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(decision)).seal());

    // Assert
    assertEquals("A reference-cast classification requires a cast operand", exception.getMessage());
  }

  @Test
  void givenConflictingClassificationsForTheSameCast_whenSealed_thenDuplicateUseEvidenceIsRejected() {
    // Arrange
    var target = type(JavaTypeCategory.DECLARED, "java.lang.Object");
    var body = castBody(target);
    var cast = expression(body);
    var first = classified(cast.nodeId(), STRING, target, Optional.of(false));
    var conflicting = classified(cast.nodeId(), STRING, target, Optional.of(true));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(first, conflicting)).seal());

    // Assert
    assertEquals("Duplicate typed-use decision key", exception.getMessage());
  }

  @Test
  void givenAnImpossibleNullCastFailure_whenTheDecisionIsConstructed_thenTheContradictionIsRejected() {
    // Arrange
    var source = type(JavaTypeCategory.NULL, "null");

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> classified("cast", source, STRING, Optional.of(true)));

    // Assert
    assertEquals("A null reference cast cannot fail its validity check", exception.getMessage());
  }

  private static SyntaxNode castBody(JavaTypeIdentity target) {
    return expressionBody(path -> node(path, JavaSourceKind.TYPE_CAST, SyntaxKind.TYPE_CAST, new SyntaxType.Resolved(target),
        new SyntaxValue.TypeName(target.displayName()), List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0,
            literal(path.child(SyntaxRole.EXPRESSION, 0), STRING))), Optional.empty()));
  }

  private static TypedUseDecision classified(String consumer, JavaTypeIdentity source, JavaTypeIdentity target, Optional<Boolean> check) {
    return new TypedUseDecision(METHOD, consumer, SyntaxRole.EXPRESSION, 0, source, target, Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty(), Optional.empty(), check);
  }
}
