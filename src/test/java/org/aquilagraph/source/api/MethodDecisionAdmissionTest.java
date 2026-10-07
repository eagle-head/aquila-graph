package org.aquilagraph.source.api;

import static org.aquilagraph.source.api.MethodDecisionFixtures.literal;

import static org.aquilagraph.source.api.MethodDecisionFixtures.access;
import static org.aquilagraph.source.api.MethodDecisionFixtures.binary;
import static org.aquilagraph.source.api.MethodDecisionFixtures.builder;
import static org.aquilagraph.source.api.MethodDecisionFixtures.builderWithField;
import static org.aquilagraph.source.api.MethodDecisionFixtures.creation;
import static org.aquilagraph.source.api.MethodDecisionFixtures.expression;
import static org.aquilagraph.source.api.MethodDecisionFixtures.expressionBody;
import static org.aquilagraph.source.api.MethodDecisionFixtures.field;
import static org.aquilagraph.source.api.MethodDecisionFixtures.fixed;
import static org.aquilagraph.source.api.MethodDecisionFixtures.INT;
import static org.aquilagraph.source.api.MethodDecisionFixtures.invocation;
import static org.aquilagraph.source.api.MethodDecisionFixtures.LONG;
import static org.aquilagraph.source.api.MethodDecisionFixtures.MEMBER;
import static org.aquilagraph.source.api.MethodDecisionFixtures.METHOD;
import static org.aquilagraph.source.api.MethodDecisionFixtures.node;
import static org.aquilagraph.source.api.MethodDecisionFixtures.OBJECT;
import static org.aquilagraph.source.api.MethodDecisionFixtures.owner;
import static org.aquilagraph.source.api.MethodDecisionFixtures.reference;
import static org.aquilagraph.source.api.MethodDecisionFixtures.returnedBody;
import static org.aquilagraph.source.api.MethodDecisionFixtures.SOURCE_FIELD;
import static org.aquilagraph.source.api.MethodDecisionFixtures.STRING;
import static org.aquilagraph.source.api.MethodDecisionFixtures.type;
import static org.aquilagraph.source.api.MethodDecisionFixtures.use;
import static org.aquilagraph.source.api.MethodDecisionFixtures.variable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.cfg.internal.CfgReadiness;
import org.junit.jupiter.api.Test;

class MethodDecisionAdmissionTest {
  @Test
  void givenCopiedUsesWithExplicitNoConversion_whenSealed_thenPresentFactsRemainAfterCallerMutation() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));
    var consumer = body.requiredChild(SyntaxRole.STATEMENT);
    var decision = use(consumer, SyntaxRole.EXPRESSION, 0, INT);
    var decisions = new ArrayList<>(List.of(decision));
    var aggregateBuilder = builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, decisions);
    decisions.clear();

    // Act
    var view = aggregateBuilder.seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertSame(decision, view.useDecision(consumer.nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow());
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(view));
    assertTrue(view.useDecision(consumer.nodeId(), SyntaxRole.STATEMENT, 0).isEmpty());
    assertTrue(view.useDecision("foreign", SyntaxRole.EXPRESSION, 0).isEmpty());
    assertThrows(IllegalStateException.class, aggregateBuilder::seal);
  }

  @Test
  void givenDifferentSelectedUsesForTheSameBody_whenSealed_thenFactOwnersDifferAndSyntaxOccurrenceIdentityRemainsUnchanged() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));
    var consumer = body.requiredChild(SyntaxRole.STATEMENT);
    var firstUse = use(consumer, SyntaxRole.EXPRESSION, 0, INT);
    var secondUse = use(consumer, SyntaxRole.EXPRESSION, 0, LONG);

    // Act
    var first = builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(firstUse)).seal();
    var second = builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(secondUse)).seal();
    var firstView = first.methodSyntax(METHOD).orElseThrow();
    var secondView = second.methodSyntax(METHOD).orElseThrow();

    // Assert
    assertNotEquals(first, second);
    assertNotEquals(firstView, secondView);
    assertEquals(first.nodes(), second.nodes());
    assertEquals(first.nodeTuples(), second.nodeTuples());
    assertEquals(first.rangeTuples(), second.rangeTuples());
    assertSame(body, firstView.root());
    assertSame(body, secondView.root());
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
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(decision, decision)).seal());

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
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(decision)).seal());
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
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(decision)).seal());
    }
  }

  @Test
  void givenAUseSourceThatDiffersFromTheResolvedChild_whenSealed_thenTheContradictionIsRejected() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));
    var decision = use(METHOD, body.requiredChild(SyntaxRole.STATEMENT).nodeId(), SyntaxRole.EXPRESSION, 0, LONG, LONG);

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(decision)).seal());

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
    var variableBuilder = builder().addMethodBody(METHOD, variableBody, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY,
        List.of(use(variableCall, SyntaxRole.ARGUMENT, 0, INT), use(variableCall, SyntaxRole.ARGUMENT, 1, LONG),
            use(variableCall, SyntaxRole.ARGUMENT, 2, LONG)), variableDecisions);
    variableDecisions.clear();

    // Act
    var fixedView = builder().addMethodBody(METHOD, fixedBody, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY,
        List.of(use(fixedCall, SyntaxRole.ARGUMENT, 0, INT)), List.of(selectedFixed)).seal().methodSyntax(METHOD).orElseThrow();
    var variableView = variableBuilder.seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertSame(selectedFixed, fixedView.invocationArity(fixedCall.nodeId()).orElseThrow());
    assertSame(selectedVariable, variableView.invocationArity(variableCall.nodeId()).orElseThrow());
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(fixedView));
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(variableView));
  }

  @Test
  void givenGenericArrayAndElementErasure_whenSealed_thenDisplayArgumentsDoNotRejectConsistentAllocation() {
    // Arrange
    var element = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.util.List<java.lang.String>", "java.util.List");
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of(element)));
    var call = expression(body);
    var decision = variable(call, 1, element);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY,
        List.of(use(call, SyntaxRole.ARGUMENT, 0, element)), List.of(decision)).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(view));
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
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(decision)).seal());
    }
  }

  @Test
  void givenDuplicateForeignAndNonInvocationArityKeys_whenSealed_thenEachCallKeyIsRejected() {
    // Arrange
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of()));
    var call = expression(body);
    var valid = fixed(call, 0);
    var foreignMethod = new InvocationApplicability("foreign", call.nodeId(), InvocationApplicabilityMode.FIXED, 0, Optional.empty(),
        Optional.empty(), Optional.empty());
    var foreignNode = new InvocationApplicability(METHOD, "foreign", InvocationApplicabilityMode.FIXED, 0, Optional.empty(), Optional.empty(),
        Optional.empty());
    var nonInvocation = new InvocationApplicability(METHOD, body.nodeId(), InvocationApplicabilityMode.FIXED, 0, Optional.empty(), Optional.empty(),
        Optional.empty());
    var groups = List.of(List.of(valid, valid), List.of(foreignMethod), List.of(foreignNode), List.of(nonInvocation));

    // Act and Assert
    for (var decisions : groups) {
      assertThrows(IllegalArgumentException.class,
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), decisions).seal());
    }
  }

  @Test
  void givenVariableTrailingUseWithAnotherTarget_whenSealed_thenCallAndUseDisagreementIsRejected() {
    // Arrange
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of(INT)));
    var call = expression(body);

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> builder().addMethodBody(METHOD, body,
        MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(use(call, SyntaxRole.ARGUMENT, 0, LONG)), List.of(variable(call, 1, INT))).seal());

    // Assert
    assertTrue(error.getMessage().contains("argument target"));
  }

  @Test
  void givenVariableArrayWithAnotherComponentErasure_whenSealed_thenSuppliedAllocationContradictionIsRejected() {
    // Arrange
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of()));
    var call = expression(body);
    var array = type(JavaTypeCategory.ARRAY, "int[]");
    var decision = new InvocationApplicability(METHOD, call.nodeId(), InvocationApplicabilityMode.VARIABLE, 1, Optional.of(array),
        Optional.of(array), Optional.of(LONG));

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(decision)).seal());

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
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(decision)).seal());

    // Assert
    assertTrue(error.getMessage().contains("null-type component"));
  }

  @Test
  void givenCopiedStaticReadWriteAndInvocationFacts_whenSealed_thenAllKindsRetainTheirSelectedMembers() {
    // Arrange
    var readBody = expressionBody(path -> field(path, SyntaxKind.FIELD_ACCESS, SyntaxValue.FieldReceiver.STATIC_EXPRESSION, ReferenceKind.FIELD));
    var writeBody = expressionBody(path -> node(path, JavaSourceKind.ASSIGNMENT, SyntaxKind.ASSIGNMENT, new SyntaxType.Resolved(INT),
        SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.LOCATION, 0,
            field(path.child(SyntaxRole.LOCATION, 0), SyntaxKind.FIELD_ACCESS, SyntaxValue.FieldReceiver.STATIC_TYPE, ReferenceKind.FIELD)),
            new SyntaxChild(SyntaxRole.VALUE, 0, literal(path.child(SyntaxRole.VALUE, 0), INT))), Optional.empty()));
    var invokeBody = expressionBody(path -> invocation(path, InvocationForm.STATIC_TYPE, owner(Optional.of("qualifier")), List.of()));
    var read = expression(readBody);
    var write = expression(writeBody).requiredChild(SyntaxRole.LOCATION);
    var invoke = expression(invokeBody);
    var readDecision = access(read, StaticMemberKind.FIELD_READ, false, Optional.of("declaring-owner"));
    var writeDecision = access(write, StaticMemberKind.FIELD_WRITE, false, Optional.of("declaring-owner"));
    var invokeDecision = access(invoke, StaticMemberKind.METHOD_INVOKE, false, Optional.of("declaring-owner"));
    var reads = new ArrayList<>(List.of(readDecision));
    var readBuilder = builder().addMethodBody(METHOD, readBody, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), reads);
    reads.clear();

    // Act
    var readView = readBuilder.seal().methodSyntax(METHOD).orElseThrow();
    var writeView = builder().addMethodBody(METHOD, writeBody, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY,
        List.of(use(expression(writeBody), SyntaxRole.VALUE, 0, INT)), List.of(), List.of(writeDecision))
        .seal().methodSyntax(METHOD).orElseThrow();
    var invokeView = builder().addMethodBody(METHOD, invokeBody, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(fixed(invoke, 0)),
        List.of(invokeDecision)).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertSame(readDecision, readView.staticAccess(read.nodeId()).orElseThrow());
    assertSame(writeDecision, writeView.staticAccess(write.nodeId()).orElseThrow());
    assertSame(invokeDecision, invokeView.staticAccess(invoke.nodeId()).orElseThrow());
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(readView));
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(writeView));
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(invokeView));
  }

  @Test
  void givenDuplicateForeignAndMismatchedStaticKeys_whenSealed_thenEachContradictionIsRejected() {
    // Arrange
    var body = expressionBody(path -> field(path, SyntaxKind.FIELD_ACCESS, SyntaxValue.FieldReceiver.STATIC_TYPE, ReferenceKind.FIELD));
    var read = expression(body);
    var valid = access(read, StaticMemberKind.FIELD_READ, false, Optional.of("owner"));
    var groups = List.of(List.of(valid, valid),
        List.of(StaticMemberEvidence.known("foreign", read.nodeId(), MEMBER, StaticMemberKind.FIELD_READ, owner(Optional.of("owner")), false, Optional.empty())),
        List.of(StaticMemberEvidence.known(METHOD, "foreign", MEMBER, StaticMemberKind.FIELD_READ, owner(Optional.of("owner")), false, Optional.empty())),
        List.of(StaticMemberEvidence.known(METHOD, read.nodeId(), "another-member", StaticMemberKind.FIELD_READ, owner(Optional.of("owner")), false, Optional.empty())),
        List.of(access(read, StaticMemberKind.FIELD_WRITE, false, Optional.of("owner"))),
        List.of(access(body, StaticMemberKind.FIELD_READ, false, Optional.of("owner"))));

    // Act and Assert
    for (var decisions : groups) {
      assertThrows(IllegalArgumentException.class,
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), decisions).seal());
    }
  }

  @Test
  void givenAStaticDecisionForAnInstanceAccess_whenSealed_thenTheReceiverContradictionIsRejected() {
    // Arrange
    var body = expressionBody(path -> field(path, SyntaxKind.FIELD_ACCESS, SyntaxValue.FieldReceiver.INSTANCE_IMPLICIT, ReferenceKind.FIELD));
    var decision = access(expression(body), StaticMemberKind.FIELD_READ, false, Optional.of("owner"));

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), List.of(decision)).seal());

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
        .addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), List.of(correct)).seal()
        .methodSyntax(METHOD).orElseThrow();
    var error = assertThrows(IllegalArgumentException.class, () -> builderWithField(Set.of(JavaDeclarationModifier.STATIC), false, INT)
        .addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), List.of(wrong)).seal());

    // Assert
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(view));
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
        .addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), List.of(decision)).seal());
    assertThrows(IllegalArgumentException.class, () -> builderWithField(Set.of(JavaDeclarationModifier.STATIC), false, INT)
        .addMethodBody(METHOD, wrongKindBody, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), List.of(wrongKind)).seal());
  }

  @Test
  void givenSourceConstantReadWithoutFinalOrInitializer_whenSealed_thenEachLocallyImpossibleFlagIsRejected() {
    // Arrange
    var body = sourceFieldBody(ReferenceKind.FIELD);
    var decision = sourceFieldAccess(expression(body), true, "owner");

    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> builderWithField(Set.of(JavaDeclarationModifier.STATIC), true, INT)
        .addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), List.of(decision)).seal());
    assertThrows(IllegalArgumentException.class,
        () -> builderWithField(Set.of(JavaDeclarationModifier.STATIC, JavaDeclarationModifier.FINAL), false, INT)
            .addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), List.of(decision)).seal());
  }

  @Test
  void givenSourceFinalInitializedPrimitiveField_whenSealed_thenSuppliedConstantFlagCanBeRetained() {
    // Arrange
    var body = sourceFieldBody(ReferenceKind.FIELD);
    var decision = sourceFieldAccess(expression(body), true, "owner");

    // Act
    var view = builderWithField(Set.of(JavaDeclarationModifier.STATIC, JavaDeclarationModifier.FINAL), true, INT)
        .addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), List.of(decision)).seal()
        .methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(view));
    assertTrue(view.staticAccess(expression(body).nodeId()).orElseThrow().constantVariable().knownValue().orElseThrow());
  }

  @Test
  void givenCopiedStringDecisionWithOneConvertedSide_whenSealed_thenOrdinaryUsesAndStringContextRemainSeparate() {
    // Arrange
    var body = expressionBody(path -> binary(path, SyntaxOperator.PLUS, STRING, STRING, INT));
    var plus = expression(body);
    var decision = StringEvidenceFixture.known(METHOD, plus.nodeId(), STRING, Optional.of(SyntaxRole.RIGHT_OPERAND), EvidenceValue.known(false, new EvidenceOrigin(METHOD, plus.nodeId(), "string-expression.constant", Optional.empty())));
    var strings = new ArrayList<>(List.of(decision));
    var aggregateBuilder = builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY,
        List.of(use(plus, SyntaxRole.LEFT_OPERAND, 0, STRING), use(plus, SyntaxRole.RIGHT_OPERAND, 0, INT)), List.of(), List.of(), strings);
    strings.clear();

    // Act
    var view = aggregateBuilder.seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertSame(decision, view.stringConcatenation(plus.nodeId()).orElseThrow());
    assertEquals(INT, view.useDecision(plus.nodeId(), SyntaxRole.RIGHT_OPERAND, 0).orElseThrow().selectedTarget());
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(view));
  }

  @Test
  void givenUnavailableStringConstantClassification_whenSourceIsSealed_thenKnownNeighborsRemainAvailableAndCfgRefusesSeparately() {
    // Arrange
    var body = expressionBody(path -> binary(path, SyntaxOperator.PLUS, STRING, STRING, INT));
    var plus = expression(body);
    var origin = new EvidenceOrigin(METHOD, plus.nodeId(), "string-expression.constant", plus.origin());

    for (var state : List.of(EvidenceState.MISSING, EvidenceState.ERRONEOUS)) {
      var classification = new EvidenceValue<Boolean>(state, Optional.empty(), origin, Optional.of("Supplied classification gap"));
      var decision = StringEvidenceFixture.known(METHOD, plus.nodeId(), STRING, Optional.of(SyntaxRole.RIGHT_OPERAND), classification);

      // Act
      var source = builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY,
          List.of(use(plus, SyntaxRole.LEFT_OPERAND, 0, STRING), use(plus, SyntaxRole.RIGHT_OPERAND, 0, INT)), List.of(), List.of(),
          List.of(decision)).seal();
      var view = source.methodSyntax(METHOD).orElseThrow();

      // Assert
      var retained = view.stringConcatenation(plus.nodeId()).orElseThrow();
      assertEquals(STRING, retained.stringType());
      assertEquals(Optional.of(true), retained.leftIsString().knownValue());
      assertEquals(Optional.of(false), retained.rightIsString().knownValue());
      assertEquals(classification, retained.compileTimeConstant());
      assertEquals(List.of(decision), view.evidence().stringExpressions());
      assertEquals(List.of(SyntaxRole.LEFT_OPERAND, SyntaxRole.RIGHT_OPERAND),
          view.evidence().uses().stream().map(ChildUseEvidence::role).toList());
      assertThrows(UnsupportedOperationException.class, () -> view.evidence().stringExpressions().clear());
      assertEquals(INT, view.useDecision(plus.nodeId(), SyntaxRole.RIGHT_OPERAND, 0).orElseThrow().selectedTarget());
      assertEquals(state == EvidenceState.MISSING ? CfgReadiness.UNRESOLVED_SEMANTICS : CfgReadiness.ERRONEOUS_SEMANTICS,
          CfgAdmission.classify(view));
      assertSame(source, view.source());
    }
  }

  @Test
  void givenBothStringOperandsAndCompilerConstantFact_whenSealed_thenNoConvertedSideIsRequired() {
    // Arrange
    var body = expressionBody(path -> binary(path, SyntaxOperator.PLUS, STRING, STRING, STRING));
    var plus = expression(body);
    var decision = StringEvidenceFixture.known(METHOD, plus.nodeId(), STRING, Optional.empty(), EvidenceValue.known(true, new EvidenceOrigin(METHOD, plus.nodeId(), "string-expression.constant", Optional.empty())));

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY,
        List.of(use(plus, SyntaxRole.LEFT_OPERAND, 0, STRING), use(plus, SyntaxRole.RIGHT_OPERAND, 0, STRING)), List.of(), List.of(),
        List.of(decision)).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(view));
    assertTrue(view.stringConcatenation(plus.nodeId()).orElseThrow().compileTimeConstant().knownValue().orElseThrow());
  }

  @Test
  void givenDuplicateForeignAndWrongSideStringDecisions_whenSealed_thenEachContradictionIsRejected() {
    // Arrange
    var body = expressionBody(path -> binary(path, SyntaxOperator.PLUS, STRING, STRING, INT));
    var plus = expression(body);
    var valid = StringEvidenceFixture.known(METHOD, plus.nodeId(), STRING, Optional.of(SyntaxRole.RIGHT_OPERAND), EvidenceValue.known(false, new EvidenceOrigin(METHOD, plus.nodeId(), "string-expression.constant", Optional.empty())));
    var groups = List.of(List.of(valid, valid),
        List.of(StringEvidenceFixture.known("foreign", plus.nodeId(), STRING, Optional.of(SyntaxRole.RIGHT_OPERAND), EvidenceValue.known(false, new EvidenceOrigin("foreign", plus.nodeId(), "string-expression.constant", Optional.empty())))),
        List.of(StringEvidenceFixture.known(METHOD, "foreign", STRING, Optional.of(SyntaxRole.RIGHT_OPERAND), EvidenceValue.known(false, new EvidenceOrigin(METHOD, "foreign", "string-expression.constant", Optional.empty())))),
        List.of(StringEvidenceFixture.known(METHOD, plus.nodeId(), STRING, Optional.of(SyntaxRole.LEFT_OPERAND), EvidenceValue.known(false, new EvidenceOrigin(METHOD, plus.nodeId(), "string-expression.constant", Optional.empty())))),
        List.of(StringEvidenceFixture.known(METHOD, plus.nodeId(), STRING, Optional.empty(), EvidenceValue.known(false, new EvidenceOrigin(METHOD, plus.nodeId(), "string-expression.constant", Optional.empty())))));

    // Act and Assert
    for (var decisions : groups) {
      assertThrows(IllegalArgumentException.class, () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY,
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
      var decision = StringEvidenceFixture.known(METHOD, expression(body).nodeId(), STRING, Optional.of(SyntaxRole.RIGHT_OPERAND), EvidenceValue.known(false, new EvidenceOrigin(METHOD, expression(body).nodeId(), "string-expression.constant", Optional.empty())));
      assertThrows(IllegalArgumentException.class, () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY,
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
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(fixed(expression(body), 0)))
              .seal());
    }
  }

  @Test
  void givenNullDecisionListsOrElements_whenRegistered_thenNoBodyIsPublished() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));
    var withNull = new ArrayList<ConversionEvidence>();
    withNull.add(null);

    // Act and Assert
    assertThrows(NullPointerException.class, () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, null));
    assertThrows(NullPointerException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, withNull));
    assertThrows(NullPointerException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), null));
    assertThrows(NullPointerException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), null));
    assertThrows(NullPointerException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), List.of(), null));
  }

  private static SyntaxNode sourceFieldBody(ReferenceKind referenceKind) {
    return expressionBody(path -> node(path, JavaSourceKind.IDENTIFIER, SyntaxKind.FIELD_ACCESS, new SyntaxType.Resolved(INT),
        new SyntaxValue.FieldAccess("field", SyntaxValue.FieldReceiver.STATIC_TYPE), List.of(),
        Optional.of(reference(referenceKind, SOURCE_FIELD))));
  }

  private static StaticMemberEvidence sourceFieldAccess(SyntaxNode read, boolean constant, String ownerKey) {
    return StaticMemberEvidence.known(METHOD, read.nodeId(), SOURCE_FIELD, StaticMemberKind.FIELD_READ, owner(Optional.of(ownerKey)), constant, Optional.empty());
  }
}
