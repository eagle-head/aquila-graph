package org.aquilagraph.syntax;

import static org.aquilagraph.syntax.MethodDecisionFixtures.BOOLEAN;
import static org.aquilagraph.syntax.MethodDecisionFixtures.BODY_PATH;
import static org.aquilagraph.syntax.MethodDecisionFixtures.INT;
import static org.aquilagraph.syntax.MethodDecisionFixtures.LONG;
import static org.aquilagraph.syntax.MethodDecisionFixtures.METHOD;
import static org.aquilagraph.syntax.MethodDecisionFixtures.OWNER;
import static org.aquilagraph.syntax.MethodDecisionFixtures.STRING;
import static org.aquilagraph.syntax.MethodDecisionFixtures.access;
import static org.aquilagraph.syntax.MethodDecisionFixtures.binary;
import static org.aquilagraph.syntax.MethodDecisionFixtures.block;
import static org.aquilagraph.syntax.MethodDecisionFixtures.builder;
import static org.aquilagraph.syntax.MethodDecisionFixtures.creation;
import static org.aquilagraph.syntax.MethodDecisionFixtures.expression;
import static org.aquilagraph.syntax.MethodDecisionFixtures.expressionBody;
import static org.aquilagraph.syntax.MethodDecisionFixtures.field;
import static org.aquilagraph.syntax.MethodDecisionFixtures.fixed;
import static org.aquilagraph.syntax.MethodDecisionFixtures.invocation;
import static org.aquilagraph.syntax.MethodDecisionFixtures.literal;
import static org.aquilagraph.syntax.MethodDecisionFixtures.node;
import static org.aquilagraph.syntax.MethodDecisionFixtures.owner;
import static org.aquilagraph.syntax.MethodDecisionFixtures.reference;
import static org.aquilagraph.syntax.MethodDecisionFixtures.returnedBody;
import static org.aquilagraph.syntax.MethodDecisionFixtures.type;
import static org.aquilagraph.syntax.MethodDecisionFixtures.use;
import static org.aquilagraph.syntax.MethodDecisionFixtures.variable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.ReferenceKind;
import org.junit.jupiter.api.Test;

class MethodDecisionReadinessTest {
  @Test
  void givenLegacyRegistrationWithAnEligibleReturn_whenReadinessIsQueried_thenMissingDecisionRefusesAdmission() {
    // Arrange
    var body = returnedBody(new SyntaxType.Resolved(INT));

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
    assertTrue(view.useDecision(body.requiredChild(SyntaxRole.STATEMENT).nodeId(), SyntaxRole.EXPRESSION, 0).isEmpty());
  }

  @Test
  void givenAnEmptyBodyWithoutEligibleSlots_whenReadinessIsQueried_thenLegacyRegistrationRemainsLowerable() {
    // Arrange
    var body = block(BODY_PATH, List.of());

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertTrue(view.useDecision(body.nodeId(), SyntaxRole.STATEMENT, 0).isEmpty());
    assertTrue(view.invocationArity(body.nodeId()).isEmpty());
    assertTrue(view.staticAccess(body.nodeId()).isEmpty());
    assertTrue(view.stringConcatenation(body.nodeId()).isEmpty());
  }

  @Test
  void givenUnsupportedErroneousAndMissingEvidence_whenReadinessIsQueried_thenUnsupportedWinsAcrossTheWholeBody() {
    // Arrange
    var errorReturn = returnedBody(SyntaxType.Erroneous.INSTANCE).requiredChild(SyntaxRole.STATEMENT);
    var unsupported = node(BODY_PATH.child(SyntaxRole.STATEMENT, 1), JavaSourceKind.WHILE_LOOP, SyntaxKind.UNSUPPORTED,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported("WHILE_LOOP"), List.of(), Optional.empty());
    var body = block(BODY_PATH, List.of(errorReturn, unsupported));

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.UNSUPPORTED, view.readiness());
    assertTrue(view.useDecision(errorReturn.nodeId(), SyntaxRole.EXPRESSION, 0).isEmpty());
  }

  @Test
  void givenErroneousAndMissingEvidence_whenReadinessIsQueried_thenCompilerErrorPrecedesIncompleteDecisions() {
    // Arrange
    var body = returnedBody(SyntaxType.Erroneous.INSTANCE);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.ERRONEOUS_SEMANTICS, view.readiness());
  }

  @Test
  void givenRetainedUseOnErroneousOrUnsupportedBodies_whenQueried_thenReadinessDoesNotHideThePresentFact() {
    // Arrange
    var errorBody = returnedBody(SyntaxType.Erroneous.INSTANCE);
    var consumer = errorBody.requiredChild(SyntaxRole.STATEMENT);
    var decision = use(METHOD, consumer.nodeId(), SyntaxRole.EXPRESSION, 0, INT, INT);
    var unsupported = node(BODY_PATH.child(SyntaxRole.STATEMENT, 1), JavaSourceKind.WHILE_LOOP, SyntaxKind.UNSUPPORTED,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.Unsupported("WHILE_LOOP"), List.of(), Optional.empty());
    var unsupportedBody = block(BODY_PATH, List.of(consumer, unsupported));

    // Act
    var errorView = builder().addMethodBody(METHOD, errorBody, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(decision)).seal()
        .methodSyntax(METHOD).orElseThrow();
    var unsupportedView = builder().addMethodBody(METHOD, unsupportedBody, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(decision)).seal()
        .methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.ERRONEOUS_SEMANTICS, errorView.readiness());
    assertEquals(SyntaxReadiness.UNSUPPORTED, unsupportedView.readiness());
    assertEquals(Optional.of(decision), errorView.useDecision(consumer.nodeId(), SyntaxRole.EXPRESSION, 0));
    assertEquals(Optional.of(decision), unsupportedView.useDecision(consumer.nodeId(), SyntaxRole.EXPRESSION, 0));
  }

  @Test
  void givenAResolvedDecisionForAnUnresolvedChild_whenReadinessIsQueried_thenTheSuppliedFactDoesNotRepairAttribution() {
    // Arrange
    var body = returnedBody(SyntaxType.Unresolved.INSTANCE);
    var consumer = body.requiredChild(SyntaxRole.STATEMENT);
    var decision = use(METHOD, consumer.nodeId(), SyntaxRole.EXPRESSION, 0, INT, INT);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(decision)).seal()
        .methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
    assertEquals(Optional.of(decision), view.useDecision(consumer.nodeId(), SyntaxRole.EXPRESSION, 0));
  }

  @Test
  void givenACompleteCallShapeWithAnAbsentArgumentUse_whenReadinessIsQueried_thenInvocationDecisionAloneIsInsufficient() {
    // Arrange
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of(INT, LONG)));
    var call = expression(body);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION,
        List.of(use(call, SyntaxRole.ARGUMENT, 0, INT)), List.of(fixed(call, 2))).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
    assertTrue(view.invocationArity(call.nodeId()).isPresent());
    assertTrue(view.useDecision(call.nodeId(), SyntaxRole.ARGUMENT, 1).isEmpty());
  }

  @Test
  void givenCompleteArgumentUsesWithMissingCallShape_whenReadinessIsQueried_thenSelectedApplicabilityIsRequired() {
    // Arrange
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of(INT)));
    var call = expression(body);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION,
        List.of(use(call, SyntaxRole.ARGUMENT, 0, INT))).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
    assertTrue(view.invocationArity(call.nodeId()).isEmpty());
  }

  @Test
  void givenVariableApplicabilityWithoutTrailingArguments_whenReadinessIsQueried_thenTheRetainedArrayDecisionIsStillRequired() {
    // Arrange
    var body = expressionBody(path -> invocation(path, InvocationForm.INSTANCE_IMPLICIT, owner(Optional.of("owner")), List.of(INT)));
    var call = expression(body);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION,
        List.of(use(call, SyntaxRole.ARGUMENT, 0, INT)), List.of(variable(call, 2, STRING))).seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertEquals(InvocationArityMode.VARIABLE, view.invocationArity(call.nodeId()).orElseThrow().mode());
    assertEquals(2, view.invocationArity(call.nodeId()).orElseThrow().formalCount());
  }

  @Test
  void givenStaticFieldOrMethodWithMissingAccessFacts_whenReadinessIsQueried_thenEachAccessRefusesAdmission() {
    // Arrange
    var fieldBody = expressionBody(path -> field(path, SyntaxKind.FIELD_ACCESS, SyntaxValue.FieldReceiver.STATIC_TYPE, ReferenceKind.FIELD));
    var callBody = expressionBody(path -> invocation(path, InvocationForm.STATIC_TYPE, owner(Optional.of("qualifier")), List.of()));
    var call = expression(callBody);

    // Act
    var fieldView = builder().addMethodBody(METHOD, fieldBody, MethodBodyOrigin.EXECUTION_PROJECTION).seal().methodSyntax(METHOD).orElseThrow();
    var callView = builder().addMethodBody(METHOD, callBody, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(fixed(call, 0))).seal()
        .methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, fieldView.readiness());
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, callView.readiness());
    assertTrue(fieldView.staticAccess(expression(fieldBody).nodeId()).isEmpty());
    assertTrue(callView.staticAccess(call.nodeId()).isEmpty());
  }

  @Test
  void givenTriggeringStaticAccessWithoutOwnerIdentity_whenReadinessIsQueried_thenTheMissingInitializationTargetRefusesAdmission() {
    // Arrange
    var body = expressionBody(path -> field(path, SyntaxKind.FIELD_ACCESS, SyntaxValue.FieldReceiver.STATIC_TYPE, ReferenceKind.FIELD));
    var read = expression(body);
    var decision = access(read, StaticAccessKind.FIELD_READ, false, Optional.empty());

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(decision)).seal()
        .methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
    assertEquals(Optional.of(decision), view.staticAccess(read.nodeId()));
  }

  @Test
  void givenConstantStaticReadWithoutOwnerIdentity_whenReadinessIsQueried_thenNoInitializationIdentityIsInvented() {
    // Arrange
    var body = expressionBody(path -> field(path, SyntaxKind.FIELD_ACCESS, SyntaxValue.FieldReceiver.STATIC_TYPE, ReferenceKind.FIELD));
    var read = expression(body);
    var decision = access(read, StaticAccessKind.FIELD_READ, true, Optional.empty());

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(), List.of(decision)).seal()
        .methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertTrue(view.staticAccess(read.nodeId()).orElseThrow().declaringType().declarationKey().isEmpty());
  }

  @Test
  void givenCreationWithoutOwnerIdentity_whenReadinessIsQueried_thenMissingCanonicalTargetRefusesAdmission() {
    // Arrange
    var body = expressionBody(path -> creation(path, owner(Optional.empty()), OWNER));
    var created = expression(body);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(fixed(created, 0))).seal()
        .methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
    assertTrue(view.invocationArity(created.nodeId()).isPresent());
  }

  @Test
  void givenCreationWithCorrelatedOwnerErasureAndIdentity_whenReadinessIsQueried_thenConstructorShapeCanBeAdmitted() {
    // Arrange
    var body = expressionBody(path -> creation(path, owner(Optional.of("actual-owner")), OWNER));
    var created = expression(body);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(), List.of(fixed(created, 0))).seal()
        .methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertTrue(view.staticAccess(created.nodeId()).isEmpty());
  }

  @Test
  void givenStringPlusWithOrdinaryUsesButNoStringContext_whenReadinessIsQueried_thenSelectedConcatenationFactIsRequired() {
    // Arrange
    var body = expressionBody(path -> binary(path, SyntaxOperator.PLUS, STRING, STRING, INT));
    var plus = expression(body);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION,
        List.of(use(plus, SyntaxRole.LEFT_OPERAND, 0, STRING), use(plus, SyntaxRole.RIGHT_OPERAND, 0, INT))).seal()
        .methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
    assertTrue(view.stringConcatenation(plus.nodeId()).isEmpty());
  }

  @Test
  void givenNumericPlusWithCompleteOrdinaryUses_whenReadinessIsQueried_thenNoStringDecisionIsRequired() {
    // Arrange
    var body = expressionBody(path -> binary(path, SyntaxOperator.PLUS, INT, INT, INT));
    var plus = expression(body);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION,
        List.of(use(plus, SyntaxRole.LEFT_OPERAND, 0, INT), use(plus, SyntaxRole.RIGHT_OPERAND, 0, INT))).seal()
        .methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertTrue(view.stringConcatenation(plus.nodeId()).isEmpty());
  }

  @Test
  void givenTwoMethodsWithEqualNodeKeys_whenDecisionQueriesRun_thenEachViewRetainsOnlyItsOwnFacts() {
    // Arrange
    var firstBody = returnedBody(new SyntaxType.Resolved(INT));
    var secondBody = returnedBody(new SyntaxType.Resolved(INT));
    var firstConsumer = firstBody.requiredChild(SyntaxRole.STATEMENT);
    var secondConsumer = secondBody.requiredChild(SyntaxRole.STATEMENT);
    var firstDecision = use(firstConsumer, SyntaxRole.EXPRESSION, 0, INT);
    var secondDecision = use("second-method", secondConsumer.nodeId(), SyntaxRole.EXPRESSION, 0, INT, LONG);

    // Act
    var aggregate = builder().addMethodBody(METHOD, firstBody, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(firstDecision))
        .addMethodBody("second-method", secondBody, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(secondDecision)).seal();
    var firstView = aggregate.methodSyntax(METHOD).orElseThrow();
    var secondView = aggregate.methodSyntax("second-method").orElseThrow();

    // Assert
    assertEquals(firstConsumer.nodeId(), secondConsumer.nodeId());
    assertEquals(Optional.of(firstDecision), firstView.useDecision(firstConsumer.nodeId(), SyntaxRole.EXPRESSION, 0));
    assertEquals(Optional.of(secondDecision), secondView.useDecision(secondConsumer.nodeId(), SyntaxRole.EXPRESSION, 0));
    assertTrue(aggregate.node(firstConsumer.nodeId()).isEmpty());
  }

  @Test
  void givenInvalidQueryArgumentsOnAnUnresolvedView_whenQueried_thenAllFourFamiliesRejectDirectInvalidInput() {
    // Arrange
    var body = returnedBody(SyntaxType.Unresolved.INSTANCE);
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION).seal().methodSyntax(METHOD).orElseThrow();

    // Act and Assert
    for (var nodeId : new String[]{null, "", " "}) {
      assertThrows(IllegalArgumentException.class, () -> view.useDecision(nodeId, SyntaxRole.EXPRESSION, 0));
      assertThrows(IllegalArgumentException.class, () -> view.invocationArity(nodeId));
      assertThrows(IllegalArgumentException.class, () -> view.staticAccess(nodeId));
      assertThrows(IllegalArgumentException.class, () -> view.stringConcatenation(nodeId));
    }
    assertThrows(IllegalArgumentException.class, () -> view.useDecision(body.nodeId(), null, 0));
    assertThrows(IllegalArgumentException.class, () -> view.useDecision(body.nodeId(), SyntaxRole.EXPRESSION, -1));
    assertTrue(view.invocationArity("absent").isEmpty());
    assertTrue(view.staticAccess("absent").isEmpty());
    assertTrue(view.stringConcatenation("absent").isEmpty());
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
  }

  @Test
  void givenConditionalArrayAssignmentAndCastSlots_whenCompleteDecisionsAreSupplied_thenEachRequiredUseCanBeAdmitted() {
    // Arrange
    var bodies = List.of(conditionalBody(), arrayBody(), assignmentBody(), castBody());

    // Act and Assert
    for (var body : bodies) {
      var consumer = expression(body);
      var decisions = usesFor(consumer);
      var complete = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, decisions).seal()
          .methodSyntax(METHOD).orElseThrow();
      assertEquals(SyntaxReadiness.LOWERABLE, complete.readiness());
      for (var decision : decisions) {
        var incomplete = new ArrayList<>(decisions);
        incomplete.remove(decision);
        var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, incomplete).seal()
            .methodSyntax(METHOD).orElseThrow();
        assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
      }
    }
  }

  @Test
  void givenLocallyContradictoryArrayAssignmentOrCastTarget_whenSealed_thenEachSelectedUseIsRejected() {
    // Arrange
    var bodies = List.of(arrayBody(), assignmentBody(), castBody());
    var roles = List.of(SyntaxRole.INDEX, SyntaxRole.VALUE, SyntaxRole.EXPRESSION);

    // Act and Assert
    for (var index = 0; index < bodies.size(); index++) {
      var body = bodies.get(index);
      var decision = use(expression(body), roles.get(index), 0, LONG);
      assertThrows(IllegalArgumentException.class,
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(decision)).seal());
    }
  }

  @Test
  void givenUnaryBranchLogicalAndLocalSlots_whenOneEligibleUseIsMissing_thenEachFamilyRefusesReadiness() {
    // Arrange
    var bodies = List.of(unaryBody(), branchBody(), logicalBody(), localBody());

    // Act and Assert
    for (var body : bodies) {
      var consumer = body.requiredChild(SyntaxRole.STATEMENT);
      if (consumer.syntaxKind() == SyntaxKind.EXPRESSION_STATEMENT) {
        consumer = consumer.requiredChild(SyntaxRole.EXPRESSION);
      }
      var decisions = otherUsesFor(consumer);
      var complete = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, decisions).seal()
          .methodSyntax(METHOD).orElseThrow();
      assertEquals(SyntaxReadiness.LOWERABLE, complete.readiness());
      for (var decision : decisions) {
        var incomplete = new ArrayList<>(decisions);
        incomplete.remove(decision);
        var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, incomplete).seal()
            .methodSyntax(METHOD).orElseThrow();
        assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
      }
    }
  }

  @Test
  void givenNestedSwitchArrowAndYieldResults_whenDecisionsAreAdmitted_thenTargetsFollowTheirNearestEnclosingSwitch() {
    // Arrange
    var body = switchBody();
    var outer = expression(body);
    var arrow = outer.children(SyntaxRole.CASE).getFirst();
    var outerYield = outer.children(SyntaxRole.CASE).get(1).requiredChild(SyntaxRole.BODY).requiredChild(SyntaxRole.STATEMENT);
    var inner = outerYield.requiredChild(SyntaxRole.VALUE);
    var innerArrow = inner.children(SyntaxRole.CASE).getFirst();
    var decisions = List.of(use(outer, SyntaxRole.SELECTOR, 0, INT), use(arrow, SyntaxRole.BODY, 0, LONG),
        use(outerYield, SyntaxRole.VALUE, 0, LONG), use(inner, SyntaxRole.SELECTOR, 0, INT), use(innerArrow, SyntaxRole.BODY, 0, INT));

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, decisions).seal()
        .methodSyntax(METHOD).orElseThrow();
    var wrong = new ArrayList<>(decisions);
    wrong.set(4, use(innerArrow, SyntaxRole.BODY, 0, LONG));
    var error = assertThrows(IllegalArgumentException.class,
        () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, wrong).seal());

    // Assert
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertEquals(LONG, view.useDecision(outerYield.nodeId(), SyntaxRole.VALUE, 0).orElseThrow().selectedTarget());
    assertEquals(INT, view.useDecision(innerArrow.nodeId(), SyntaxRole.BODY, 0).orElseThrow().selectedTarget());
    assertTrue(view.useDecision(outer.children(SyntaxRole.CASE).get(1).nodeId(), SyntaxRole.BODY, 0).isEmpty());
    assertTrue(error.getMessage().contains("selected target"));
    for (var decision : decisions) {
      var incomplete = new ArrayList<>(decisions);
      incomplete.remove(decision);
      var incompleteView = builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, incomplete).seal()
          .methodSyntax(METHOD).orElseThrow();
      assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, incompleteView.readiness());
    }
  }

  @Test
  void givenArrayOperandWithAnotherCategoryOrComponent_whenSealed_thenEachLocallyVisibleArrayContradictionIsRejected() {
    // Arrange
    var bodies = List.of(arrayBody(INT), arrayBody(type(JavaTypeCategory.ARRAY, "long[]")));

    // Act and Assert
    for (var body : bodies) {
      var decision = use(expression(body), SyntaxRole.INDEX, 0, INT);
      assertThrows(IllegalArgumentException.class,
          () -> builder().addMethodBody(METHOD, body, MethodBodyOrigin.EXECUTION_PROJECTION, List.of(decision)).seal());
    }
  }

  private static SyntaxNode conditionalBody() {
    return expressionBody(path -> node(path, JavaSourceKind.CONDITIONAL_EXPRESSION, SyntaxKind.CONDITIONAL_EXPRESSION,
        new SyntaxType.Resolved(INT), SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.CONDITION, 0, literal(path.child(SyntaxRole.CONDITION, 0), BOOLEAN)),
            new SyntaxChild(SyntaxRole.TRUE_EXPRESSION, 0, literal(path.child(SyntaxRole.TRUE_EXPRESSION, 0), INT)),
            new SyntaxChild(SyntaxRole.FALSE_EXPRESSION, 0, literal(path.child(SyntaxRole.FALSE_EXPRESSION, 0), INT))), Optional.empty()));
  }

  private static SyntaxNode arrayBody() {
    return arrayBody(type(JavaTypeCategory.ARRAY, "int[]"));
  }

  private static SyntaxNode arrayBody(JavaTypeIdentity arrayType) {
    return expressionBody(path -> node(path, JavaSourceKind.ARRAY_ACCESS, SyntaxKind.ARRAY_ACCESS, new SyntaxType.Resolved(INT),
        SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.ARRAY, 0, literal(path.child(SyntaxRole.ARRAY, 0), arrayType)),
            new SyntaxChild(SyntaxRole.INDEX, 0, literal(path.child(SyntaxRole.INDEX, 0), INT))), Optional.empty()));
  }

  private static SyntaxNode assignmentBody() {
    return expressionBody(path -> {
      var location = node(path.child(SyntaxRole.LOCATION, 0), JavaSourceKind.IDENTIFIER, SyntaxKind.VARIABLE_LOCATION,
          new SyntaxType.Resolved(INT), new SyntaxValue.SourceSymbol("local"), List.of(), Optional.of(reference(ReferenceKind.LOCAL, "local")));
      return node(path, JavaSourceKind.ASSIGNMENT, SyntaxKind.ASSIGNMENT, new SyntaxType.Resolved(INT), SyntaxValue.None.INSTANCE,
          List.of(new SyntaxChild(SyntaxRole.LOCATION, 0, location),
              new SyntaxChild(SyntaxRole.VALUE, 0, literal(path.child(SyntaxRole.VALUE, 0), INT))), Optional.empty());
    });
  }

  private static SyntaxNode castBody() {
    return expressionBody(path -> node(path, JavaSourceKind.TYPE_CAST, SyntaxKind.TYPE_CAST, new SyntaxType.Resolved(INT),
        new SyntaxValue.TypeName("int"), List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, literal(path.child(SyntaxRole.EXPRESSION, 0), INT))),
        Optional.empty()));
  }

  private static SyntaxNode unaryBody() {
    return expressionBody(path -> node(path, JavaSourceKind.UNARY_MINUS, SyntaxKind.UNARY, new SyntaxType.Resolved(INT),
        new SyntaxValue.Operator(SyntaxOperator.UNARY_MINUS),
        List.of(new SyntaxChild(SyntaxRole.OPERAND, 0, literal(path.child(SyntaxRole.OPERAND, 0), INT))), Optional.empty()));
  }

  private static SyntaxNode branchBody() {
    var path = BODY_PATH.child(SyntaxRole.STATEMENT, 0);
    var branch = node(path, JavaSourceKind.IF, SyntaxKind.IF, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.CONDITION, 0, literal(path.child(SyntaxRole.CONDITION, 0), BOOLEAN)),
            new SyntaxChild(SyntaxRole.THEN_BRANCH, 0, block(path.child(SyntaxRole.THEN_BRANCH, 0), List.of()))), Optional.empty());
    return block(BODY_PATH, List.of(branch));
  }

  private static SyntaxNode logicalBody() {
    return expressionBody(path -> node(path, JavaSourceKind.CONDITIONAL_AND, SyntaxKind.CONDITIONAL_AND,
        new SyntaxType.Resolved(BOOLEAN), new SyntaxValue.Operator(SyntaxOperator.CONDITIONAL_AND),
        List.of(new SyntaxChild(SyntaxRole.LEFT_OPERAND, 0, literal(path.child(SyntaxRole.LEFT_OPERAND, 0), BOOLEAN)),
            new SyntaxChild(SyntaxRole.RIGHT_OPERAND, 0, literal(path.child(SyntaxRole.RIGHT_OPERAND, 0), BOOLEAN))), Optional.empty()));
  }

  private static SyntaxNode localBody() {
    var path = BODY_PATH.child(SyntaxRole.STATEMENT, 0);
    var local = node(path, JavaSourceKind.VARIABLE, SyntaxKind.LOCAL_DECLARATION, new SyntaxType.Resolved(INT),
        new SyntaxValue.LocalDeclaration("local"),
        List.of(new SyntaxChild(SyntaxRole.INITIALIZER, 0, literal(path.child(SyntaxRole.INITIALIZER, 0), INT))),
        Optional.of(reference(ReferenceKind.LOCAL, "local")));
    return block(BODY_PATH, List.of(local));
  }

  private static SyntaxNode switchBody() {
    return expressionBody(path -> {
      var firstPath = path.child(SyntaxRole.CASE, 0);
      var first = ruleCase(firstPath, literal(firstPath.child(SyntaxRole.BODY, 0), INT));
      var secondPath = path.child(SyntaxRole.CASE, 1);
      var blockPath = secondPath.child(SyntaxRole.BODY, 0);
      var yieldPath = blockPath.child(SyntaxRole.STATEMENT, 0);
      var innerPath = yieldPath.child(SyntaxRole.VALUE, 0);
      var innerCasePath = innerPath.child(SyntaxRole.CASE, 0);
      var innerCase = ruleCase(innerCasePath, literal(innerCasePath.child(SyntaxRole.BODY, 0), INT));
      var inner = node(innerPath, JavaSourceKind.SWITCH_EXPRESSION, SyntaxKind.SWITCH_EXPRESSION, new SyntaxType.Resolved(INT),
          SyntaxValue.None.INSTANCE,
          List.of(new SyntaxChild(SyntaxRole.SELECTOR, 0, literal(innerPath.child(SyntaxRole.SELECTOR, 0), INT)),
              new SyntaxChild(SyntaxRole.CASE, 0, innerCase)), Optional.empty());
      var yieldNode = node(yieldPath, JavaSourceKind.YIELD, SyntaxKind.YIELD, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
          List.of(new SyntaxChild(SyntaxRole.VALUE, 0, inner)), Optional.empty());
      var second = ruleCase(secondPath, block(blockPath, List.of(yieldNode)));
      return node(path, JavaSourceKind.SWITCH_EXPRESSION, SyntaxKind.SWITCH_EXPRESSION, new SyntaxType.Resolved(LONG), SyntaxValue.None.INSTANCE,
          List.of(new SyntaxChild(SyntaxRole.SELECTOR, 0, literal(path.child(SyntaxRole.SELECTOR, 0), INT)),
              new SyntaxChild(SyntaxRole.CASE, 0, first), new SyntaxChild(SyntaxRole.CASE, 1, second)), Optional.empty());
    });
  }

  private static SyntaxNode ruleCase(SyntaxStructuralPath path, SyntaxNode body) {
    return node(path, JavaSourceKind.CASE, SyntaxKind.CASE, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.RULE, List.of(SyntaxValue.SwitchLabel.Default.INSTANCE)),
        List.of(new SyntaxChild(SyntaxRole.BODY, 0, body)), Optional.empty());
  }

  private static List<TypedUseDecision> usesFor(SyntaxNode consumer) {
    return switch (consumer.syntaxKind()) {
      case CONDITIONAL_EXPRESSION -> List.of(use(consumer, SyntaxRole.CONDITION, 0, BOOLEAN),
          use(consumer, SyntaxRole.TRUE_EXPRESSION, 0, INT), use(consumer, SyntaxRole.FALSE_EXPRESSION, 0, INT));
      case ARRAY_ACCESS -> List.of(use(consumer, SyntaxRole.INDEX, 0, INT));
      case ASSIGNMENT -> List.of(use(consumer, SyntaxRole.VALUE, 0, INT));
      case TYPE_CAST -> List.of(use(consumer, SyntaxRole.EXPRESSION, 0, INT));
      default -> throw new IllegalArgumentException("Unexpected test consumer");
    };
  }

  private static List<TypedUseDecision> otherUsesFor(SyntaxNode consumer) {
    return switch (consumer.syntaxKind()) {
      case UNARY -> List.of(use(consumer, SyntaxRole.OPERAND, 0, INT));
      case IF -> List.of(use(consumer, SyntaxRole.CONDITION, 0, BOOLEAN));
      case CONDITIONAL_AND -> List.of(use(consumer, SyntaxRole.LEFT_OPERAND, 0, BOOLEAN), use(consumer, SyntaxRole.RIGHT_OPERAND, 0, BOOLEAN));
      case LOCAL_DECLARATION -> List.of(use(consumer, SyntaxRole.INITIALIZER, 0, INT));
      default -> throw new IllegalArgumentException("Unexpected test consumer");
    };
  }
}
