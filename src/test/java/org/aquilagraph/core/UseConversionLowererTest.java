package org.aquilagraph.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxStructuralPath;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;
import org.aquilagraph.syntax.TypedUseDecision;
import org.junit.jupiter.api.Test;

class UseConversionLowererTest {
  @Test
  void givenSelectedBoxingToWiderReference_whenUseIsLowered_thenItsRoleIdentityTypeAndOwnAllocationEffectAreRetained() {
    // Arrange
    var integer = type(JavaTypeCategory.INT, "int");
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Integer");
    var object = type(JavaTypeCategory.DECLARED, "java.lang.Object");
    var fixture = fixture(integer, object);
    var decision = decision(fixture.consumer(), integer, object, Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.of(integer), Optional.of(wrapper));

    // Act
    var converted = new UseConversionLowerer(fixture.graph()).convert(fixture.consumer(), decision, fixture.input());
    var graph = fixture.graph().complete(converted);

    // Assert
    var box = graph.operationNodes().getLast();
    assertEquals(List.of(OperationKind.READ, OperationKind.BOX), graph.operationNodes().stream().map(node -> node.kind()).toList());
    assertEquals(operationId(fixture.consumer(), "box:v1:10:expression:0"), box.nodeId());
    assertEquals(Optional.of(object), box.definedValue().orElseThrow().staticType());
    assertEquals(List.of("java.lang.OutOfMemoryError"), box.possibleExceptions());
    assertEquals(List.of(), box.semanticReferences());
    assertEquals(List.of("java.lang.OutOfMemoryError"), graph.edges().stream().filter(edge -> edge.transferKind() == TransferKind.EXCEPTIONAL)
        .map(edge -> edge.discriminator().orElseThrow()).toList());
  }

  @Test
  void givenSelectedUnboxingAndWidening_whenUseIsLowered_thenOneUnboxDefinesTheEffectivePrimitiveAtItsOwnFailureSite() {
    // Arrange
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Integer");
    var integer = type(JavaTypeCategory.INT, "int");
    var wide = type(JavaTypeCategory.LONG, "long");
    var fixture = fixture(wrapper, wide);
    var decision = decision(fixture.consumer(), wrapper, wide, Optional.empty(), Optional.of(integer), Optional.of(wide),
        Optional.empty(), Optional.empty());

    // Act
    var graph = fixture.graph().complete(new UseConversionLowerer(fixture.graph()).convert(fixture.consumer(), decision, fixture.input()));

    // Assert
    var unbox = graph.operationNodes().getLast();
    assertEquals(List.of(OperationKind.READ, OperationKind.UNBOX), graph.operationNodes().stream().map(node -> node.kind()).toList());
    assertEquals(operationId(fixture.consumer(), "unbox:v1:10:expression:0"), unbox.nodeId());
    assertEquals(Optional.of(wide), unbox.definedValue().orElseThrow().staticType());
    assertEquals(List.of("java.lang.NullPointerException"), unbox.possibleExceptions());
    assertEquals(List.of(), graph.operationNodes().getFirst().possibleExceptions());
  }

  @Test
  void givenCheckedWrapperBeforeUnboxing_whenUseIsLowered_thenCastAndUnboxKeepSeparateOrderedValuesAndFailureOrigins() {
    // Arrange
    var object = type(JavaTypeCategory.DECLARED, "java.lang.Object");
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Integer");
    var integer = type(JavaTypeCategory.INT, "int");
    var fixture = fixture(object, integer);
    var decision = decision(fixture.consumer(), object, integer, Optional.of(wrapper), Optional.of(integer), Optional.of(integer),
        Optional.empty(), Optional.empty());

    // Act
    var graph = fixture.graph().complete(new UseConversionLowerer(fixture.graph()).convert(fixture.consumer(), decision, fixture.input()));

    // Assert
    assertEquals(List.of(OperationKind.READ, OperationKind.CAST, OperationKind.UNBOX), graph.operationNodes().stream().map(node -> node.kind()).toList());
    var cast = graph.operationNodes().get(1);
    var unbox = graph.operationNodes().get(2);
    assertEquals(operationId(fixture.consumer(), "cast"), cast.nodeId());
    assertEquals(Optional.of(wrapper), cast.definedValue().orElseThrow().staticType());
    assertEquals(cast.definedValue().orElseThrow(), unbox.operands().getFirst().value());
    assertEquals(Optional.of(integer), unbox.definedValue().orElseThrow().staticType());
    assertEquals(List.of("java.lang.ClassCastException"), cast.possibleExceptions());
    assertEquals(List.of("java.lang.NullPointerException"), unbox.possibleExceptions());
    assertEquals(List.of(cast.nodeId(), unbox.nodeId()), graph.edges().stream().filter(edge -> edge.transferKind() == TransferKind.EXCEPTIONAL)
        .map(edge -> edge.sourceNodeId()).toList());
  }

  @Test
  void givenExplicitReferenceUseWithoutConversion_whenUseIsLowered_thenTheOriginalProducerFragmentIsRetained() {
    // Arrange
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Integer");
    var object = type(JavaTypeCategory.DECLARED, "java.lang.Object");
    var fixture = fixture(wrapper, object);
    var decision = decision(fixture.consumer(), wrapper, object, Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty());

    // Act
    var converted = new UseConversionLowerer(fixture.graph()).convert(fixture.consumer(), decision, fixture.input());

    // Assert
    assertSame(fixture.input(), converted);
    assertEquals(List.of(OperationKind.READ), fixture.graph().complete(converted).operationNodes().stream().map(node -> node.kind()).toList());
  }

  @Test
  void givenProducerWithoutNormalContinuation_whenConversionIsRequested_thenNoConversionOrNormalSuccessorIsCreated() {
    // Arrange
    var wrapper = type(JavaTypeCategory.DECLARED, "java.lang.Integer");
    var integer = type(JavaTypeCategory.INT, "int");
    var fixture = fixture(wrapper, integer);
    var abrupt = new GraphFragment(fixture.input().entry(), List.of(), fixture.input().value());
    var decision = decision(fixture.consumer(), wrapper, integer, Optional.empty(), Optional.of(integer), Optional.of(integer),
        Optional.empty(), Optional.empty());

    // Act
    var converted = new UseConversionLowerer(fixture.graph()).convert(fixture.consumer(), decision, abrupt);

    // Assert
    assertSame(abrupt, converted);
    assertEquals(List.of(), converted.normalExits());
    assertEquals(List.of(OperationKind.READ), fixture.graph().complete(converted).operationNodes().stream().map(node -> node.kind()).toList());
  }

  private static Fixture fixture(JavaTypeIdentity source, JavaTypeIdentity target) {
    var method = new JavaMethod("type:Owner#value()", "type:Owner", "value", "value()", "int", List.of(), Set.of(), false, true, true, List.of());
    var graph = new ControlFlowGraphConstruction(method);
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT, 0);
    var reference = new SemanticReference(ReferenceKind.LOCAL, ResolutionStatus.RESOLVED, Optional.of("local:value"),
        Optional.of(source.erasedName()), List.of(), List.of());
    var child = SyntaxNode.withoutDirectSource("source", path.child(SyntaxRole.EXPRESSION, 0), JavaSourceKind.IDENTIFIER,
        SyntaxKind.VARIABLE_ACCESS, new SyntaxType.Resolved(source), new SyntaxValue.SourceSymbol("value"), List.of(), Optional.of(reference));
    var consumer = SyntaxNode.withoutDirectSource("source", path, JavaSourceKind.TYPE_CAST, SyntaxKind.TYPE_CAST,
        new SyntaxType.Resolved(target), new SyntaxValue.TypeName(target.displayName()),
        List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, child)), Optional.empty());
    FlowValue value = graph.temporaryValue("producer", "source", Optional.of(source));
    var operation = graph.addOperationWithId("producer", child, OperationKind.READ, "source", List.of(), Optional.of(value), List.of());
    return new Fixture(graph, consumer, GraphFragment.value(operation.nodeId(), value));
  }

  private static TypedUseDecision decision(SyntaxNode consumer, JavaTypeIdentity source, JavaTypeIdentity target,
      Optional<JavaTypeIdentity> checked, Optional<JavaTypeIdentity> unboxed, Optional<JavaTypeIdentity> effective,
      Optional<JavaTypeIdentity> primitive, Optional<JavaTypeIdentity> boxed) {
    return new TypedUseDecision("type:Owner#value()", consumer.nodeId(), SyntaxRole.EXPRESSION, 0, source, target, checked, unboxed,
        effective, primitive, boxed);
  }

  private static String operationId(SyntaxNode consumer, String role) {
    var graphId = StableId.sha256("cfg", "type:Owner#value()");
    return StableId.sha256("cfg-node", graphId, "operation", StableId.sha256("operation", "type:Owner#value()", consumer.nodeId(), role));
  }

  private static JavaTypeIdentity type(JavaTypeCategory category, String name) {
    return new JavaTypeIdentity(category, name, name);
  }

  private record Fixture(ControlFlowGraphConstruction graph, SyntaxNode consumer, GraphFragment input) {
  }
}
