package org.aquilagraph.source.api;

import static org.aquilagraph.source.api.MethodDecisionFixtures.METHOD;
import static org.aquilagraph.source.api.MethodDecisionFixtures.SOURCE;
import static org.aquilagraph.source.api.MethodDecisionFixtures.builder;
import static org.aquilagraph.source.api.MethodDecisionFixtures.expression;
import static org.aquilagraph.source.api.MethodDecisionFixtures.expressionBody;
import static org.aquilagraph.source.api.MethodDecisionFixtures.fixed;
import static org.aquilagraph.source.api.MethodDecisionFixtures.reference;
import static org.aquilagraph.source.api.MethodDecisionFixtures.variable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.cfg.internal.CfgReadiness;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class InvocationSelectionTest {
  private static final SyntaxStructuralPath CALL_PATH = SyntaxStructuralPath.sourceFileRoot()
      .child(SyntaxRole.BODY, 0).child(SyntaxRole.STATEMENT, 0).child(SyntaxRole.EXPRESSION, 0);
  private static final SyntaxType.Resolved INTS = array("int[]", "int[]", new JavaTypeStructure.Primitive(JavaTypeCategory.INT));
  private static final SyntaxType.Resolved LONGS = array("long[]", "long[]", new JavaTypeStructure.Primitive(JavaTypeCategory.LONG));

  @TestFactory
  Stream<DynamicTest> givenExactArrayReceiverAndResult_whenCopiedInvocationIsCreated_thenSelectionRetainsItsStructureWithoutNamedTarget() {
    return arrayTypes().map(type -> DynamicTest.dynamicTest("given" + type.identity().displayName()
        + "ReceiverAndResult_whenCopied_thenIntrinsicStructureRemains", () -> {
          // Arrange
          var semantics = semantics(type);

          // Act
          var call = call(CALL_PATH, type, semantics, List.of(receiver(CALL_PATH, type)), Optional.empty());

          // Assert
          assertEquals(InvocationSelection.ARRAY_CLONE, call.value(SyntaxValue.Invocation.class).semantics().orElseThrow().selection());
          assertEquals(type, call.type());
          assertEquals(type, call.requiredChild(SyntaxRole.RECEIVER).type());
          assertTrue(call.semanticReference().isEmpty());
          assertTrue(call.semanticEdges().isEmpty());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenNonArrayOrUnavailableReceiver_whenIntrinsicInvocationIsCreated_thenTheReceiverIsRejected() {
    return nonArrayTypes().map(receiverType -> DynamicTest.dynamicTest("given" + receiverType
        + "Receiver_whenIntrinsicInvocationIsCreated_thenReceiverIsRejected", () -> {
          // Arrange
          var children = List.of(receiver(CALL_PATH, receiverType));

          // Act
          var failure = assertThrows(IllegalArgumentException.class,
              () -> call(CALL_PATH, INTS, semantics(INTS), children, Optional.empty()));

          // Assert
          assertTrue(failure.getMessage().contains("same exact resolved array"));
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenDifferentComponentDimensionOrUnresolvedResult_whenIntrinsicInvocationIsCreated_thenTheResultIsRejected() {
    return differentResults().map(result -> DynamicTest.dynamicTest("given" + result
        + "Result_whenIntrinsicInvocationIsCreated_thenResultIsRejected", () -> {
          // Arrange
          var children = List.of(receiver(CALL_PATH, INTS));

          // Act
          var failure = assertThrows(IllegalArgumentException.class,
              () -> call(CALL_PATH, result, semantics(INTS), children, Optional.empty()));

          // Assert
          assertTrue(failure.getMessage().contains("same exact resolved array"));
        }));
  }

  @Test
  void givenDifferentQualifyingArray_whenIntrinsicInvocationIsCreated_thenQualificationIsRejected() {
    // Arrange
    var children = List.of(receiver(CALL_PATH, INTS));

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> call(CALL_PATH, INTS, semantics(LONGS), children, Optional.empty()));

    // Assert
    assertTrue(failure.getMessage().contains("same exact resolved array"));
  }

  @Test
  void givenIntrinsicInvocationWithNamedReference_whenCopied_thenTheFabricatedTargetIsRejected() {
    // Arrange
    var selected = Optional.of(reference(ReferenceKind.METHOD, "Owner#clone()"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> call(CALL_PATH, INTS, semantics(INTS), List.of(receiver(CALL_PATH, INTS)), selected));

    // Assert
    assertTrue(failure.getMessage().contains("no named executable"));
  }

  @Test
  void givenIntrinsicInvocationWithoutReceiver_whenCopied_thenTheMissingReceiverIsRejected() {
    // Arrange
    var semantics = semantics(INTS);

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> call(CALL_PATH, INTS, semantics, List.of(), Optional.empty()));

    // Assert
    assertTrue(failure.getMessage().contains("RECEIVER"));
  }

  @Test
  void givenIntrinsicInvocationWithArgument_whenCopied_thenTheArgumentIsRejected() {
    // Arrange
    var argument = SyntaxNode.withoutDirectSource(SOURCE, CALL_PATH.child(SyntaxRole.ARGUMENT, 0), JavaSourceKind.INT_LITERAL,
        SyntaxKind.LITERAL, new SyntaxType.Resolved(MethodDecisionFixtures.INT), new SyntaxValue.Literal("1"), List.of(), Optional.empty());
    var children = List.of(receiver(CALL_PATH, INTS), new SyntaxChild(SyntaxRole.ARGUMENT, 0, argument));

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> call(CALL_PATH, INTS, semantics(INTS), children, Optional.empty()));

    // Assert
    assertTrue(failure.getMessage().contains("no arguments"));
  }

  @Test
  void givenNamedSelectionWithoutReference_whenCopied_thenTheMissingDeclarationIsRejected() {
    // Arrange
    var named = new InvocationSemantics(InvocationMode.VIRTUAL,
        new JavaTypeReference(INTS.identity(), Optional.empty()), InvocationForm.INSTANCE_EXPRESSION, InvocationSelection.NAMED_EXECUTABLE);

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> call(CALL_PATH, INTS, named, List.of(receiver(CALL_PATH, INTS)), Optional.empty()));

    // Assert
    assertTrue(failure.getMessage().contains("requires a semantic reference"));
  }

  @Test
  void givenArrayCloneRelabeledAsNamedWithFabricatedTarget_whenCopied_thenTheContradictorySelectionIsRejected() {
    // Arrange
    var named = new InvocationSemantics(InvocationMode.VIRTUAL,
        new JavaTypeReference(INTS.identity(), Optional.empty()), InvocationForm.INSTANCE_EXPRESSION, InvocationSelection.NAMED_EXECUTABLE);
    var target = Optional.of(reference(ReferenceKind.METHOD, "java.base/java.lang.Object#clone()"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> call(CALL_PATH, INTS, named, List.of(receiver(CALL_PATH, INTS)), target));

    // Assert
    assertTrue(failure.getMessage().contains("cannot select a named executable"));
  }

  @Test
  void givenArrayIntrinsicWithAnotherName_whenInvocationValueIsCreated_thenTheNameIsRejected() {
    // Arrange
    var semantics = semantics(INTS);

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.Invocation("copy", InvocationForm.INSTANCE_EXPRESSION, Optional.of(semantics)));

    // Assert
    assertTrue(failure.getMessage().contains("clone member name"));
  }

  @Test
  void givenArrayIntrinsicWithDeclaredOwner_whenSemanticsAreCreated_thenTheNamedOwnerIsRejected() {
    // Arrange
    var owner = new JavaTypeReference(INTS.identity(), Optional.of("fabricated-array-declaration"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new InvocationSemantics(InvocationMode.VIRTUAL, owner,
        InvocationForm.INSTANCE_EXPRESSION, InvocationSelection.ARRAY_CLONE));

    // Assert
    assertTrue(failure.getMessage().contains("without a declaration key"));
  }

  @Test
  void givenArrayCloneWithFixedZeroApplicability_whenAdmitted_thenTheClassificationRemainsQueryable() {
    // Arrange
    var body = body();
    var call = expression(body);
    var selected = fixed(call, 0);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(selected))
        .seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertEquals(selected, view.invocationArity(call.nodeId()).orElseThrow());
    assertEquals(0, view.invocationArity(call.nodeId()).orElseThrow().formalCount());
    assertEquals(InvocationApplicabilityMode.FIXED, view.invocationArity(call.nodeId()).orElseThrow().mode());
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(view));
  }

  @Test
  void givenArrayCloneWithoutApplicability_whenAdmitted_thenCfgRefusesMissingEvidence() {
    // Arrange
    var body = body();
    var call = expression(body);

    // Act
    var view = builder().addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY)
        .seal().methodSyntax(METHOD).orElseThrow();

    // Assert
    assertTrue(view.invocationArity(call.nodeId()).isEmpty());
    assertEquals(CfgReadiness.UNRESOLVED_SEMANTICS, CfgAdmission.classify(view));
  }

  @Test
  void givenArrayCloneWithNonzeroFormalCount_whenAdmitted_thenTheApplicabilityIsRejected() {
    // Arrange
    var body = body();
    var selected = fixed(expression(body), 1);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> builder()
        .addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(selected)).seal());

    // Assert
    assertTrue(failure.getMessage().contains("fixed arity with no formal parameters"));
  }

  @Test
  void givenArrayCloneWithVariableApplicability_whenAdmitted_thenTheApplicabilityIsRejected() {
    // Arrange
    var body = body();
    var selected = variable(expression(body), 1, MethodDecisionFixtures.INT);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> builder()
        .addMethodBody(METHOD, body, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(selected)).seal());

    // Assert
    assertTrue(failure.getMessage().contains("fixed arity with no formal parameters"));
  }

  private static SyntaxNode body() {
    return expressionBody(path -> call(path, INTS, semantics(INTS), List.of(receiver(path, INTS)), Optional.empty()));
  }

  private static Stream<SyntaxType.Resolved> arrayTypes() {
    return Stream.of(INTS,
        array("String[]", "java.lang.String[]", new JavaTypeStructure.Declared("java.base/java.lang.String", Optional.empty(), List.of())),
        array("T[]", "java.lang.Object[]", new JavaTypeStructure.Variable("Owner#T")),
        array("int[][]", "int[][]", new JavaTypeStructure.Array(new JavaTypeStructure.Primitive(JavaTypeCategory.INT))));
  }

  private static Stream<SyntaxType> nonArrayTypes() {
    return Stream.of(new SyntaxType.Resolved(MethodDecisionFixtures.INT), new SyntaxType.Resolved(MethodDecisionFixtures.OBJECT),
        SyntaxType.Erroneous.INSTANCE, SyntaxType.Unresolved.INSTANCE, new SyntaxType.Resolved(INTS.identity()));
  }

  private static Stream<SyntaxType> differentResults() {
    return Stream.of(LONGS, array("int[][]", "int[][]", new JavaTypeStructure.Array(new JavaTypeStructure.Primitive(JavaTypeCategory.INT))),
        SyntaxType.Erroneous.INSTANCE, SyntaxType.Unresolved.INSTANCE,
        array("int[]", "int[]", new JavaTypeStructure.Primitive(JavaTypeCategory.LONG)));
  }

  private static SyntaxType.Resolved array(String display, String erasure, JavaTypeStructure component) {
    return new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.ARRAY, display, erasure), new JavaTypeStructure.Array(component));
  }

  private static InvocationSemantics semantics(SyntaxType.Resolved receiver) {
    return new InvocationSemantics(InvocationMode.VIRTUAL, new JavaTypeReference(receiver.identity(), Optional.empty()),
        InvocationForm.INSTANCE_EXPRESSION, InvocationSelection.ARRAY_CLONE);
  }

  private static SyntaxChild receiver(SyntaxStructuralPath path, SyntaxType type) {
    var receiver = SyntaxNode.withoutDirectSource(SOURCE, path.child(SyntaxRole.RECEIVER, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.FIELD_ACCESS, type, new SyntaxValue.FieldAccess("array", SyntaxValue.FieldReceiver.INSTANCE_IMPLICIT), List.of(),
        Optional.of(reference(ReferenceKind.FIELD, "External#array:field")));
    return new SyntaxChild(SyntaxRole.RECEIVER, 0, receiver);
  }

  private static SyntaxNode call(SyntaxStructuralPath path, SyntaxType result, InvocationSemantics semantics, List<SyntaxChild> children,
      Optional<SemanticReference> reference) {
    return SyntaxNode.withoutDirectSource(SOURCE, path, JavaSourceKind.METHOD_INVOCATION, SyntaxKind.INVOCATION, result,
        new SyntaxValue.Invocation("clone", semantics.form(), Optional.of(semantics)), children, reference);
  }
}
