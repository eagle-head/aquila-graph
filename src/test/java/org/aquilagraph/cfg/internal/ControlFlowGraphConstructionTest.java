package org.aquilagraph.cfg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;

class ControlFlowGraphConstructionTest {
  @Test
  void givenEqualTypedValues_whenIdentityIsReused_thenCompletedGraphRetainsOneValue() {
    // Arrange
    var construction = construction();
    var type = Optional.of(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"));
    var original = construction.localValue("local:value", "value", type);

    // Act
    var repeated = construction.localValue("local:value", "value", type);
    var graph = construction.complete(GraphFragment.empty());

    // Assert
    assertEquals(original, repeated);
    assertEquals(List.of(original), graph.values());
  }

  @Test
  void givenExistingValueWithDifferentType_whenIdentityIsReused_thenConstructionRejectsTheConflict() {
    // Arrange
    var construction = construction();
    construction.localValue("local:value", "value", Optional.of(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int")));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> construction.localValue("local:value", "value", Optional.of(new JavaTypeIdentity(JavaTypeCategory.LONG, "long", "long"))));

    // Assert
    assertEquals("A flow value identity cannot denote unequal typed values", exception.getMessage());
  }

  @Test
  void givenExistingValueWithDifferentKind_whenIdentityIsReused_thenConstructionRejectsTheConflict() {
    // Arrange
    var construction = construction();
    construction.localValue("local:value", "value", Optional.empty());

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> construction.symbolValue("local:value", "value", Optional.empty()));

    // Assert
    assertEquals("A flow value identity cannot denote unequal typed values", exception.getMessage());
  }

  @Test
  void givenExistingValueWithDifferentDisplayName_whenIdentityIsReused_thenConstructionRejectsTheConflict() {
    // Arrange
    var construction = construction();
    construction.localValue("local:value", "value", Optional.empty());

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> construction.localValue("local:value", "other", Optional.empty()));

    // Assert
    assertEquals("A flow value identity cannot denote unequal typed values", exception.getMessage());
  }

  @Test
  void givenExistingConstantWithDifferentLiteral_whenIdentityIsReused_thenConstructionRejectsTheConflict() {
    // Arrange
    var construction = construction();
    construction.constantValue("constant:value", "1", Optional.empty());

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> construction.constantValue("constant:value", "2", Optional.empty()));

    // Assert
    assertEquals("A flow value identity cannot denote unequal typed values", exception.getMessage());
  }

  @Test
  void givenYieldWithoutNormalExit_whenLaterFragmentIsSequenced_thenOnlyTheReachedYieldIsRetained() {
    // Arrange
    var construction = construction();
    var firstValue = construction.localValue("first", "first", Optional.empty());
    var laterValue = construction.localValue("later", "later", Optional.empty());
    var firstYield = new YieldExit("outer", "first-endpoint", firstValue);
    var laterYield = new YieldExit("inner", "later-endpoint", laterValue);
    var first = new GraphFragment(Optional.of("first-endpoint"), List.of(), Optional.empty(), List.of(firstYield));
    var later = new GraphFragment(Optional.of("later-endpoint"), List.of(), Optional.of(laterValue), List.of(laterYield));

    // Act
    var result = construction.sequence(first, later);

    // Assert
    assertEquals(first.entry(), result.entry());
    assertTrue(result.normalExits().isEmpty());
    assertTrue(result.value().isEmpty());
    assertEquals(List.of(firstYield), result.yieldExits());
  }

  @Test
  void givenReachedYieldBranchesAndNormalExit_whenLaterFragmentIsSequenced_thenYieldsRemainInSourceOrder() {
    // Arrange
    var construction = construction();
    var firstValue = construction.localValue("first", "first", Optional.empty());
    var laterValue = construction.localValue("later", "later", Optional.empty());
    var firstYield = new YieldExit("outer", "first-yield", firstValue);
    var laterYield = new YieldExit("outer", "later-yield", laterValue);
    var first = new GraphFragment(Optional.of("first-entry"), List.of("normal-branch"), Optional.empty(), List.of(firstYield));
    var later = new GraphFragment(Optional.of("later-entry"), List.of("later-normal"), Optional.of(laterValue), List.of(laterYield));

    // Act
    var result = construction.sequence(first, later);

    // Assert
    assertEquals(first.entry(), result.entry());
    assertEquals(later.normalExits(), result.normalExits());
    assertEquals(later.value(), result.value());
    assertEquals(List.of(firstYield, laterYield), result.yieldExits());
    assertEquals(result.yieldExits(), result.withoutValue().yieldExits());
  }

  @Test
  void givenUnconsumedYield_whenMethodAssemblyIsRequested_thenInvariantFailurePreventsGraphPublication() {
    // Arrange
    var construction = construction();
    var value = construction.localValue("value", "value", Optional.empty());
    var fragment = new GraphFragment(Optional.of("source-endpoint"), List.of(), Optional.empty(),
        List.of(new YieldExit("unconsumed-switch", "source-endpoint", value)));

    // Act
    var exception = assertThrows(IllegalStateException.class, () -> construction.complete(fragment));

    // Assert
    assertEquals("Method assembly cannot retain unconsumed switch yields", exception.getMessage());
  }

  @Test
  void givenNestedValueScopes_whenActionFails_thenShadowedBindingAndChildLifetimeAreRestored() {
    // Arrange
    var construction = construction();
    var original = construction.localValue("outer", "outer", Optional.empty());
    var shadow = construction.localValue("shadow", "shadow", Optional.empty());
    var child = construction.localValue("child", "child", Optional.empty());
    var scope = new FlowValueScope();
    scope.bind("binding", original);

    // Act
    assertThrows(IllegalStateException.class, () -> scope.inChildScope(() -> {
      scope.bind("binding", shadow);
      scope.bind("child-only", child);
      assertEquals(Optional.of(shadow), scope.resolve("binding"));
      assertEquals(Optional.of(child), scope.resolve("child-only"));
      throw new IllegalStateException("lowering interrupted");
    }));
    var recovered = scope.inChildScope(() -> scope.resolve("binding"));

    // Assert
    assertEquals(Optional.of(original), recovered);
    assertEquals(Optional.of(original), scope.resolve("binding"));
    assertTrue(scope.resolve("child-only").isEmpty());
  }

  @Test
  void givenNestedHandlerActionFails_whenLaterOperationsAreLowered_thenOuterAndMethodRoutesAreRestored() {
    // Arrange
    var construction = construction();
    var node = SyntaxNode.withoutDirectSource("source", SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.INT_LITERAL,
        SyntaxKind.LITERAL, new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int")),
        new SyntaxValue.Literal("1"), List.of(), Optional.empty());
    var outerHandler = construction.addOperation(node, "outer-handler", OperationKind.CATCH, "outer", List.of(), Optional.empty(), List.of());
    var innerHandler = construction.addOperation(node, "inner-handler", OperationKind.CATCH, "inner", List.of(), Optional.empty(), List.of());
    var outer = new ExceptionHandler(List.of("java.lang.RuntimeException"), outerHandler.nodeId());
    var inner = new ExceptionHandler(List.of("java.lang.ArithmeticException"), innerHandler.nodeId());
    var failure = new IllegalStateException("handler action interrupted");

    // Act
    var protectedFragment = construction.withExceptionHandlers(List.of(outer), () -> {
      var thrown = assertThrows(IllegalStateException.class, () -> construction.withExceptionHandlers(List.of(inner), () -> {
        throw failure;
      }));
      assertSame(failure, thrown);
      var operation = construction.addOperation(node, "protected-operation", OperationKind.NO_OP, "protected", List.of(), Optional.empty(),
          List.of(ExceptionType.runtime("java.lang.ArithmeticException")));
      return new GraphFragment(Optional.of(operation.nodeId()), List.of(operation.nodeId()), Optional.empty());
    });
    var after = construction.addOperation(node, "after-handler-scope", OperationKind.NO_OP, "after", List.of(), Optional.empty(),
        List.of(ExceptionType.runtime("java.lang.ArithmeticException")));
    var combined = construction.sequence(protectedFragment,
        new GraphFragment(Optional.of(after.nodeId()), List.of(after.nodeId()), Optional.empty()));
    var graph = construction.complete(combined);

    // Assert
    var protectedEdges = graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(protectedFragment.entry().orElseThrow()))
        .filter(edge -> edge.transferKind() == TransferKind.EXCEPTIONAL).toList();
    assertEquals(1, protectedEdges.size());
    assertEquals(outerHandler.nodeId(), protectedEdges.getFirst().targetNodeId());
    assertEquals("java.lang.ArithmeticException", protectedEdges.getFirst().discriminator().orElseThrow());
    var afterEdges = graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(after.nodeId()))
        .filter(edge -> edge.transferKind() == TransferKind.EXCEPTIONAL).toList();
    assertEquals(1, afterEdges.size());
    assertEquals(graph.exit().nodeId(), afterEdges.getFirst().targetNodeId());
    assertEquals("java.lang.ArithmeticException", afterEdges.getFirst().discriminator().orElseThrow());
    assertTrue(graph.predecessors(innerHandler).isEmpty());
  }

  private static ControlFlowGraphConstruction construction() {
    var method = new JavaMethod("type:ValueOwner#value()", "type:ValueOwner", "value", "value()", "void", List.of(), Set.of(),
        false, true, true, List.of());
    return new ControlFlowGraphConstruction(method);
  }
}
