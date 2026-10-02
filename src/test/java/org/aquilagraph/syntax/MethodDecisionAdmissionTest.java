package org.aquilagraph.syntax;

import static org.aquilagraph.syntax.MethodDecisionFixtures.INT;
import static org.aquilagraph.syntax.MethodDecisionFixtures.LONG;
import static org.aquilagraph.syntax.MethodDecisionFixtures.MEMBER;
import static org.aquilagraph.syntax.MethodDecisionFixtures.METHOD;
import static org.aquilagraph.syntax.MethodDecisionFixtures.OBJECT;
import static org.aquilagraph.syntax.MethodDecisionFixtures.SOURCE_FIELD;
import static org.aquilagraph.syntax.MethodDecisionFixtures.STRING;
import static org.aquilagraph.syntax.MethodDecisionFixtures.access;
import static org.aquilagraph.syntax.MethodDecisionFixtures.binary;
import static org.aquilagraph.syntax.MethodDecisionFixtures.builder;
import static org.aquilagraph.syntax.MethodDecisionFixtures.builderWithField;
import static org.aquilagraph.syntax.MethodDecisionFixtures.creation;
import static org.aquilagraph.syntax.MethodDecisionFixtures.expression;
import static org.aquilagraph.syntax.MethodDecisionFixtures.expressionBody;
import static org.aquilagraph.syntax.MethodDecisionFixtures.field;
import static org.aquilagraph.syntax.MethodDecisionFixtures.fixed;
import static org.aquilagraph.syntax.MethodDecisionFixtures.invocation;
import static org.aquilagraph.syntax.MethodDecisionFixtures.owner;
import static org.aquilagraph.syntax.MethodDecisionFixtures.node;
import static org.aquilagraph.syntax.MethodDecisionFixtures.reference;
import static org.aquilagraph.syntax.MethodDecisionFixtures.returnedBody;
import static org.aquilagraph.syntax.MethodDecisionFixtures.type;
import static org.aquilagraph.syntax.MethodDecisionFixtures.use;
import static org.aquilagraph.syntax.MethodDecisionFixtures.variable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.JavaTypeReference;
import org.aquilagraph.model.ReferenceKind;
import org.junit.jupiter.api.Test;

class MethodDecisionAdmissionTest {
  @Test
  void givenCopiedUsesWithExplicitNoConversion_whenSealed_thenPresentFactsRemainAfterCallerMutation() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));
    var consumer = body.requiredChild(SyntaxRole.STATEMENT);
    var decision = use(consumer, SyntaxRole.EXPRESSION, 0, INT);
    var decisions = new ArrayList<>(List.of(decision));
    var aggregateBuilder = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, decisions);
    decisions.clear();

    // Act
    var view = aggregateBuilder.seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertSame(decision, view.useDecision(consumer.nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow());
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertTrue(view.useDecision(consumer.nodeId(), SyntaxRole.STATEMENT, 0).isEmpty());
    assertTrue(view.useDecision("foreign", SyntaxRole.EXPRESSION, 0).isEmpty());
    assertThrows(IllegalStateException.class, aggregateBuilder::seal);
  }

  @Test
  void givenDifferentSelectedUsesForTheSameBody_whenSealed_thenAggregateAndMethodIdentityRemainUnchanged() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));
    var consumer = body.requiredChild(SyntaxRole.STATEMENT);
    var firstUse = use(consumer, SyntaxRole.EXPRESSION, 0, INT);
    var secondUse = use(consumer, SyntaxRole.EXPRESSION, 0, LONG);

    // Act
    var first = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(firstUse)).seal();
    var second = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(secondUse)).seal();
    var firstView = first.methodSyntax(METHOD).orElseThrow();
    var secondView = second.methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertEquals(firstView, secondView);
    assertEquals(firstView.hashCode(), secondView.hashCode());
    assertEquals(INT, firstView.useDecision(consumer.nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow().selectedTarget());
    assertEquals(LONG, secondView.useDecision(consumer.nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow().selectedTarget());
  }

  @Test
  void givenEqualRepeatedUseKeys_whenSealed_thenDuplicateEvidenceIsRejected() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));
    var decision = use(body.requiredChild(SyntaxRole.STATEMENT), SyntaxRole.EXPRESSION, 0, INT);

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(decision, decision)).seal());

    // Assert
    assertTrue(error.getMessage().contains("Duplicate typed-use"));
  }

  @Test
  void givenForeignMethodOrConsumerUseKeys_whenSealed_thenEachOwnershipMismatchIsRejected() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));
    var consumer = body.requiredChild(SyntaxRole.STATEMENT);
    var decisions = List.of(use("another-method", consumer.nodeId(), SyntaxRole.EXPRESSION, 0, INT, INT),
        use(METHOD, "foreign-consumer", SyntaxRole.EXPRESSION, 0, INT, INT));

    // Act and Assert
    for (var decision : decisions) {
      assertThrows(IllegalArgumentException.class,
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(decision)).seal());
    }
  }

  @Test
  void givenWrongRolePositionOrIneligibleConsumer_whenSealed_thenEachUseKeyIsRejected() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));
    var consumer = body.requiredChild(SyntaxRole.STATEMENT);
    var decisions = List.of(use(METHOD, consumer.nodeId(), SyntaxRole.VALUE, 0, INT, INT),
        use(METHOD, consumer.nodeId(), SyntaxRole.EXPRESSION, 1, INT, INT),
        use(METHOD, body.nodeId(), SyntaxRole.STATEMENT, 0, INT, INT));

    // Act and Assert
    for (var decision : decisions) {
      assertThrows(IllegalArgumentException.class,
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(decision)).seal());
    }
  }

  @Test
  void givenAUseSourceThatDiffersFromTheResolvedChild_whenSealed_thenTheContradictionIsRejected() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));
    var decision = use(METHOD, body.requiredChild(SyntaxRole.STATEMENT).nodeId(), SyntaxRole.EXPRESSION, 0, LONG, LONG);

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(decision)).seal());

    // Assert
    assertTrue(error.getMessage().contains("source type"));
  }

  @Test
  void givenFixedAndVariableCallDecisions_whenSealed_thenCopiedArityAndTrailingTargetsAreRetained() {
    // Arrange
    var fixedBody = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of(INT)));
    var variableBody = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")),
        List.of(INT, LONG, LONG)));
    var fixedCall = expression(fixedBody);
    var variableCall = expression(variableBody);
    var selectedFixed = fixed(fixedCall, 1);
    var selectedVariable = variable(variableCall, 2, LONG);
    var variableDecisions = new ArrayList<>(List.of(selectedVariable));
    var variableBuilder = builder().addMethodBody(METHOD, variableBody, MethodBodyOrigin.EXECUTION_PROJECTION,
        List.of(use(variableCall, SyntaxRole.ARGUMENT, 0, INT), use(variableCall, SyntaxRole.ARGUMENT, 1, LONG),
            use(variableCall, SyntaxRole.ARGUMENT, 2, LONG)), variableDecisions);
    variableDecisions.clear();

    // Act
    var fixedView = builder().addMethodBody(METHOD, fixedBody, MethodBodyOrigin.EXECUTION_PROJECTION,
        List.of(use(fixedCall, SyntaxRole.ARGUMENT, 0, INT)), List.of(selectedFixed)).seal().methodSyntax(METHOD).orElseThrow();
    var variableView = variableBuilder.seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertSame(selectedFixed, fixedView.invocationArity(fixedCall.nodeId()).orElseThrow());
    assertSame(selectedVariable, variableView.invocationArity(variableCall.nodeId()).orElseThrow());
    assertEquals(SyntaxReadiness.LOWERABLE, fixedView.readiness());
    assertEquals(SyntaxReadiness.LOWERABLE, variableView.readiness());
  }

  @Test
  void givenGenericArrayAndElementErasure_whenSealed_thenDisplayArgumentsDoNotRejectConsistentAllocation() {
    // Arrange
    var element = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.util.List<java.lang.String>", "java.util.List");
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of(element)));
    var call = expression(body);
    var decision = variable(call, 1, element);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION,
        List.of(use(call, SyntaxRole.ARGUMENT, 0, element)), List.of(decision)).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertEquals("java.util.List[]", view.invocationArity(call.nodeId()).orElseThrow().erasedArrayType().orElseThrow().erasedName());
  }

  @Test
  void givenImpossibleFixedOrVariableSourceCounts_whenSealed_thenBothApplicabilityModesReject() {
    // Arrange
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of()));
    var call = expression(body);
    var decisions = List.of(fixed(call, 1), variable(call, 2, INT));

    // Act and Assert
    for (var decision : decisions) {
      assertThrows(IllegalArgumentException.class,
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(decision)).seal());
    }
  }

  @Test
  void givenDuplicateForeignAndNonInvocationArityKeys_whenSealed_thenEachCallKeyIsRejected() {
    // Arrange
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of()));
    var call = expression(body);
    var valid = fixed(call, 0);
    var foreignMethod = new InvocationArityDecision("foreign", call.nodeId(), InvocationArityMode.FIXED, 0, Optional.empty(),
        Optional.empty(), Optional.empty());
    var foreignNode = new InvocationArityDecision(METHOD, "foreign", InvocationArityMode.FIXED, 0, Optional.empty(), Optional.empty(),
        Optional.empty());
    var nonInvocation = new InvocationArityDecision(METHOD, body.nodeId(), InvocationArityMode.FIXED, 0, Optional.empty(), Optional.empty(),
        Optional.empty());
    var groups = List.of(List.of(valid, valid), List.of(foreignMethod), List.of(foreignNode), List.of(nonInvocation));

    // Act and Assert
    for (var decisions : groups) {
      assertThrows(IllegalArgumentException.class,
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), decisions).seal());
    }
  }

  @Test
  void givenVariableTrailingUseWithAnotherTarget_whenSealed_thenCallAndUseDisagreementIsRejected() {
    // Arrange
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of(INT)));
    var call = expression(body);

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder().addMethodBody(METHOD, body,
        MethodBodyOrigin.EXECUTION_PROJECTION, List.of(use(call, SyntaxRole.ARGUMENT, 0, LONG)), List.of(variable(call, 1, INT))).seal());

    // Assert
    assertTrue(error.getMessage().contains("argument target"));
  }

  @Test
  void givenVariableArrayWithAnotherComponentErasure_whenSealed_thenSuppliedAllocationContradictionIsRejected() {
    // Arrange
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of()));
    var call = expression(body);
    var array = type(JavaTypeCategory.ARRAY, "int[]");
    var decision = new InvocationArityDecision(METHOD, call.nodeId(), InvocationArityMode.VARIABLE, 1, Optional.of(array),
        Optional.of(array), Optional.of(LONG));

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(decision)).seal());

    // Assert
    assertTrue(error.getMessage().contains("component erasure"));
  }

  @Test
  void givenVariableArrayWithNullTypeComponent_whenSealed_thenImpossibleComponentIsRejected() {
    // Arrange
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of()));
    var call = expression(body);
    var decision = variable(call, 1, type(JavaTypeCategory.NULL, "null"));

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(decision)).seal());

    // Assert
    assertTrue(error.getMessage().contains("null-type component"));
  }

  @Test
  void givenCopiedStaticReadWriteAndInvocationFacts_whenSealed_thenAllKindsRetainTheirSelectedMembers() {
    // Arrange
    var readBody = expressionBody(path -> field(path, SyntaxKind.FIELD_ACCESS, SyntaxValue.FieldReceiver.STATIC_EXPRESSION, ReferenceKind.FIELD));
    var writeBody = expressionBody(path -> field(path, SyntaxKind.FIELD_LOCATION, SyntaxValue.FieldReceiver.STATIC_TYPE, ReferenceKind.FIELD));
    var invokeBody = expressionBody(path -> invocation(path, InvocationForm.STATIC_TYPE, owner(Optional.of("qualifier")), List.of()));
    var read = expression(readBody);
    var write = expression(writeBody);
    var invoke = expression(invokeBody);
    var readDecision = access(read, StaticAccessKind.FIELD_READ, false, Optional.of("declaring-owner"));
    var writeDecision = access(write, StaticAccessKind.FIELD_WRITE, false, Optional.of("declaring-owner"));
    var invokeDecision = access(invoke, StaticAccessKind.METHOD_INVOKE, false, Optional.of("declaring-owner"));
    var reads = new ArrayList<>(List.of(readDecision));
    var readBuilder = builder().addMethodBody(METHOD, readBody, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), reads);
    reads.clear();

    // Act
    var readView = readBuilder.seal().methodSyntax(METHOD).orElseThrow();
    var writeView = builder().addMethodBody(METHOD, writeBody, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(writeDecision))
        .seal().methodSyntax(METHOD).orElseThrow();
    var invokeView = builder().addMethodBody(METHOD, invokeBody, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(fixed(invoke, 0)),
        List.of(invokeDecision)).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertSame(readDecision, readView.staticAccess(read.nodeId()).orElseThrow());
    assertSame(writeDecision, writeView.staticAccess(write.nodeId()).orElseThrow());
    assertSame(invokeDecision, invokeView.staticAccess(invoke.nodeId()).orElseThrow());
    assertEquals(SyntaxReadiness.LOWERABLE, readView.readiness());
    assertEquals(SyntaxReadiness.LOWERABLE, writeView.readiness());
    assertEquals(SyntaxReadiness.LOWERABLE, invokeView.readiness());
  }

  @Test
  void givenDuplicateForeignAndMismatchedStaticKeys_whenSealed_thenEachContradictionIsRejected() {
    // Arrange
    var body = expressionBody(path -> field(path, SyntaxKind.FIELD_ACCESS, SyntaxValue.FieldReceiver.STATIC_TYPE, ReferenceKind.FIELD));
    var read = expression(body);
    var valid = access(read, StaticAccessKind.FIELD_READ, false, Optional.of("owner"));
    var groups = List.of(List.of(valid, valid),
        List.of(new StaticAccessDecision("foreign", read.nodeId(), MEMBER, StaticAccessKind.FIELD_READ, owner(Optional.of("owner")), false)),
        List.of(new StaticAccessDecision(METHOD, "foreign", MEMBER, StaticAccessKind.FIELD_READ, owner(Optional.of("owner")), false)),
        List.of(new StaticAccessDecision(METHOD, read.nodeId(), "another-member", StaticAccessKind.FIELD_READ, owner(Optional.of("owner")), false)),
        List.of(access(read, StaticAccessKind.FIELD_WRITE, false, Optional.of("owner"))),
        List.of(access(body, StaticAccessKind.FIELD_READ, false, Optional.of("owner"))));

    // Act and Assert
    for (var decisions : groups) {
      assertThrows(IllegalArgumentException.class,
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), decisions).seal());
    }
  }

  @Test
  void givenAStaticDecisionForAnInstanceAccess_whenSealed_thenTheReceiverContradictionIsRejected() {
    // Arrange
    var body = expressionBody(path -> field(path, SyntaxKind.FIELD_ACCESS, SyntaxValue.FieldReceiver.INSTANCE_IMPLICIT, ReferenceKind.FIELD));
    var decision = access(expression(body), StaticAccessKind.FIELD_READ, false, Optional.of("owner"));

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(decision)).seal());

    // Assert
    assertTrue(error.getMessage().contains("receiver form"));
  }

  @Test
  void givenExactSourceFieldOwner_whenSealed_thenCorrectOwnerPassesAndAnotherOwnerIsRejected() {
    // Arrange
    var body = sourceFieldBody(ReferenceKind.FIELD);
    var read = expression(body);
    var correct = sourceFieldAccess(read, false, "owner");
    var wrong = sourceFieldAccess(read, false, "qualifier");

    // Act
    var view = builderWithField(Set.of(JavaDeclarationModifier.STATIC), false, INT)
        .addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(correct)).seal()
        .methodSyntax(METHOD).orElseThrow();
    var error = assertThrows(IllegalArgumentException.class, () -> builderWithField(Set.of(JavaDeclarationModifier.STATIC), false, INT)
        .addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(wrong)).seal());

    // Assert
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertTrue(error.getMessage().contains("declaring owner"));
  }

  @Test
  void givenSourceInstanceFieldOrWrongReferenceKind_whenSealed_thenStaticEvidenceCannotContradictTheOwnedDeclaration() {
    // Arrange
    var body = sourceFieldBody(ReferenceKind.FIELD);
    var wrongKindBody = sourceFieldBody(ReferenceKind.ENUM_CONSTANT);
    var decision = sourceFieldAccess(expression(body), false, "owner");
    var wrongKind = sourceFieldAccess(expression(wrongKindBody), false, "owner");

    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> builderWithField(Set.of(), false, INT)
        .addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(decision)).seal());
    assertThrows(IllegalArgumentException.class, () -> builderWithField(Set.of(JavaDeclarationModifier.STATIC), false, INT)
        .addMethodBody(METHOD, wrongKindBody, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(wrongKind)).seal());
  }

  @Test
  void givenSourceConstantReadWithoutFinalOrInitializer_whenSealed_thenEachLocallyImpossibleFlagIsRejected() {
    // Arrange
    var body = sourceFieldBody(ReferenceKind.FIELD);
    var decision = sourceFieldAccess(expression(body), true, "owner");

    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> builderWithField(Set.of(JavaDeclarationModifier.STATIC), true, INT)
        .addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(decision)).seal());
    assertThrows(IllegalArgumentException.class,
        () -> builderWithField(Set.of(JavaDeclarationModifier.STATIC, JavaDeclarationModifier.FINAL), false, INT)
            .addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(decision)).seal());
  }

  @Test
  void givenSourceFinalInitializedPrimitiveField_whenSealed_thenSuppliedConstantFlagCanBeRetained() {
    // Arrange
    var body = sourceFieldBody(ReferenceKind.FIELD);
    var decision = sourceFieldAccess(expression(body), true, "owner");

    // Act
    var view = builderWithField(Set.of(JavaDeclarationModifier.STATIC, JavaDeclarationModifier.FINAL), true, INT)
        .addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(decision)).seal()
        .methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertTrue(view.staticAccess(expression(body).nodeId()).orElseThrow().constantVariableRead());
  }

  @Test
  void givenCopiedStringDecisionWithOneConvertedSide_whenSealed_thenOrdinaryUsesAndStringContextRemainSeparate() {
    // Arrange
    var body = expressionBody(path -> binary(path, SyntaxOperator.PLUS, STRING, STRING, INT));
    var plus = expression(body);
    var decision = new StringConcatenationDecision(METHOD, plus.nodeId(), STRING, Optional.of(SyntaxRole.RIGHT_OPERAND), false);
    var strings = new ArrayList<>(List.of(decision));
    var aggregateBuilder = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION,
        List.of(use(plus, SyntaxRole.LEFT_OPERAND, 0, STRING), use(plus, SyntaxRole.RIGHT_OPERAND, 0, INT)), List.of(), List.of(), strings);
    strings.clear();

    // Act
    var view = aggregateBuilder.seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertSame(decision, view.stringConcatenation(plus.nodeId()).orElseThrow());
    assertEquals(INT, view.useDecision(plus.nodeId(), SyntaxRole.RIGHT_OPERAND, 0).orElseThrow().selectedTarget());
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
  }

  @Test
  void givenBothStringOperandsAndCompilerConstantFact_whenSealed_thenNoConvertedSideIsRequired() {
    // Arrange
    var body = expressionBody(path -> binary(path, SyntaxOperator.PLUS, STRING, STRING, STRING));
    var plus = expression(body);
    var decision = new StringConcatenationDecision(METHOD, plus.nodeId(), STRING, Optional.empty(), true);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION,
        List.of(use(plus, SyntaxRole.LEFT_OPERAND, 0, STRING), use(plus, SyntaxRole.RIGHT_OPERAND, 0, STRING)), List.of(), List.of(),
        List.of(decision)).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertTrue(view.stringConcatenation(plus.nodeId()).orElseThrow().compileTimeConstant());
  }

  @Test
  void givenDuplicateForeignAndWrongSideStringDecisions_whenSealed_thenEachContradictionIsRejected() {
    // Arrange
    var body = expressionBody(path -> binary(path, SyntaxOperator.PLUS, STRING, STRING, INT));
    var plus = expression(body);
    var valid = new StringConcatenationDecision(METHOD, plus.nodeId(), STRING, Optional.of(SyntaxRole.RIGHT_OPERAND), false);
    var groups = List.of(List.of(valid, valid),
        List.of(new StringConcatenationDecision("foreign", plus.nodeId(), STRING, Optional.of(SyntaxRole.RIGHT_OPERAND), false)),
        List.of(new StringConcatenationDecision(METHOD, "foreign", STRING, Optional.of(SyntaxRole.RIGHT_OPERAND), false)),
        List.of(new StringConcatenationDecision(METHOD, plus.nodeId(), STRING, Optional.of(SyntaxRole.LEFT_OPERAND), false)),
        List.of(new StringConcatenationDecision(METHOD, plus.nodeId(), STRING, Optional.empty(), false)));

    // Act and Assert
    for (var decisions : groups) {
      assertThrows(IllegalArgumentException.class, () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION,
          List.of(), List.of(), List.of(), decisions).seal());
    }
  }

  @Test
  void givenNumericPlusOtherOperatorOrWrongResultType_whenSealed_thenStringDecisionsCannotRepairSyntax() {
    // Arrange
    var bodies = List.of(expressionBody(path -> binary(path, SyntaxOperator.PLUS, INT, INT, INT)),
        expressionBody(path -> binary(path, SyntaxOperator.MINUS, STRING, STRING, INT)),
        expressionBody(path -> binary(path, SyntaxOperator.PLUS, OBJECT, STRING, INT)));

    // Act and Assert
    for (var body : bodies) {
      var decision = new StringConcatenationDecision(METHOD, expression(body).nodeId(), STRING, Optional.of(SyntaxRole.RIGHT_OPERAND), false);
      assertThrows(IllegalArgumentException.class, () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION,
          List.of(), List.of(), List.of(), List.of(decision)).seal());
    }
  }

  @Test
  void givenCreationOwnerOfAnotherCategoryOrErasure_whenSealed_thenInitializationContradictionsAreRejected() {
    // Arrange
    var bodies = List.of(expressionBody(path -> creation(path, new JavaTypeReference(INT, Optional.of("owner")), OBJECT)),
        expressionBody(path -> creation(path, owner(Optional.of("owner")), OBJECT)));

    // Act and Assert
    for (var body : bodies) {
      assertThrows(IllegalArgumentException.class,
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(fixed(expression(body), 0)))
              .seal());
    }
  }

  @Test
  void givenNullDecisionListsOrElements_whenRegistered_thenNoBodyIsPublished() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));
    var withNull = new ArrayList<TypedUseDecision>();
    withNull.add(null);

    // Act and Assert
    assertThrows(NullPointerException.class, () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, null));
    assertThrows(NullPointerException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, withNull));
    assertThrows(NullPointerException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), null));
    assertThrows(NullPointerException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), null));
    assertThrows(NullPointerException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(), null));
  }

  private static SyntaxNode sourceFieldBody(ReferenceKind referenceKind) {
    return expressionBody(path -> node(path, JavaSourceKind.IDENTIFIER, SyntaxKind.FIELD_ACCESS, new SyntaxType.Resolved(INT),
        new SyntaxValue.FieldAccess("field", SyntaxValue.FieldReceiver.STATIC_TYPE), List.of(),
        Optional.of(reference(referenceKind, SOURCE_FIELD))));
  }

  private static StaticAccessDecision sourceFieldAccess(SyntaxNode read, boolean constant, String ownerKey) {
    return new StaticAccessDecision(METHOD, read.nodeId(), SOURCE_FIELD, StaticAccessKind.FIELD_READ, owner(Optional.of(ownerKey)), constant);
  }
}
