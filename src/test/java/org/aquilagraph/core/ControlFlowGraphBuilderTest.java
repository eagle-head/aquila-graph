package org.aquilagraph.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;
import org.aquilagraph.syntax.MethodSyntax;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxOperator;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ControlFlowGraphBuilderTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenNestedInvocations_whenCfgIsBuilt_thenLeftToRightEvaluationOrderIsPreserved()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Order.java"),
        """
        class Order {
          int first() { return 1; }
          int second() { return 2; }
          int combined() { return first() + second(); }
        }
        """);
    var method = method("combined");

    // Act
    var result = build(method);

    // Assert
    var operations = result.graph().orElseThrow().operationNodes();
    var invocationPositions =
        IntStream.range(0, operations.size())
            .filter(index -> operations.get(index).kind() == OperationKind.INVOKE)
            .boxed()
            .toList();
    assertEquals(2, invocationPositions.size());
    var binaryPosition =
        IntStream.range(0, operations.size())
            .filter(index -> operations.get(index).kind() == OperationKind.BINARY)
            .findFirst()
            .orElseThrow();
    assertTrue(invocationPositions.get(0) < invocationPositions.get(1));
    assertTrue(invocationPositions.get(1) < binaryPosition);
  }

  @Test
  void givenElementFreeBinaryExpression_whenCfgIsBuilt_thenResolvedTypeReachesDefinedFlowValue() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("TypedValue.java"),
        "final class TypedValue { long calculate() { return 1L + 2L; } }");
    var method = method("calculate");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var binary = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.BINARY).findFirst().orElseThrow();
    var type = binary.definedValue().orElseThrow().staticType().orElseThrow();
    assertEquals(JavaTypeCategory.LONG, type.category());
    assertEquals("long", type.erasedName());
  }

  @Test
  void givenUnresolvedExpressionType_whenCfgIsRequested_thenUnresolvedDiagnosticPreventsGraphConstruction() {
    // Arrange
    var method = syntheticMethod(SyntaxType.Unresolved.INSTANCE);

    // Act
    var result = build(method);

    // Assert
    assertTrue(result.graph().isEmpty());
    assertEquals(List.of("control-flow.unresolved-semantics"),
        result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }

  @Test
  void givenErroneousExpressionType_whenCfgIsRequested_thenErroneousDiagnosticPreventsGraphConstruction() {
    // Arrange
    var method = syntheticMethod(SyntaxType.Erroneous.INSTANCE);

    // Act
    var result = build(method);

    // Assert
    assertTrue(result.graph().isEmpty());
    assertEquals(List.of("control-flow.erroneous-semantics"),
        result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }

  @Test
  void givenBinaryChildrenStoredOutOfOrder_whenCfgIsBuilt_thenTypedRolesDetermineEvaluationOrder() {
    // Arrange
    var left = syntaxLeaf("left", SyntaxKind.LITERAL, new SyntaxValue.Literal("1"));
    var right = syntaxLeaf("right", SyntaxKind.LITERAL, new SyntaxValue.Literal("2"));
    var binary =
        SyntaxNode.withoutDirectSource(
            "binary",
            SyntaxKind.BINARY,
            resolvedIntType(),
            new SyntaxValue.Operator(SyntaxOperator.PLUS),
            List.of(
                new SyntaxChild(SyntaxRole.RIGHT_OPERAND, 0, right),
                new SyntaxChild(SyntaxRole.LEFT_OPERAND, 0, left)),
            Optional.empty());
    var returnNode =
        SyntaxNode.withoutDirectSource(
            "return",
            SyntaxKind.RETURN,
            SyntaxType.NotApplicable.INSTANCE,
            SyntaxValue.None.INSTANCE,
            List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, binary)),
            Optional.empty());
    var root =
        SyntaxNode.withoutDirectSource(
            "root",
            SyntaxKind.BLOCK,
            SyntaxType.NotApplicable.INSTANCE,
            SyntaxValue.None.INSTANCE,
            List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, returnNode)),
            Optional.empty());
    var declaration =
        new JavaMethod(
            "type:RoleOrder#evaluate()",
            "type:RoleOrder",
            "evaluate",
            "evaluate()",
            "int",
            List.of(),
            Set.of(),
            false,
            true,
            true,
            List.of());
    var syntax = new MethodSyntax(declaration.methodKey(), root);

    // Act
    var graph = new ControlFlowGraphBuilder().build(declaration, syntax).graph().orElseThrow();

    // Assert
    var constants =
        graph.operationNodes().stream()
            .filter(node -> node.kind() == OperationKind.CONSTANT)
            .map(node -> node.definedValue().orElseThrow().literal().orElseThrow())
            .toList();
    assertEquals(List.of("1", "2"), constants);
  }

  @Test
  void givenShortCircuitAndThrow_whenCfgIsBuilt_thenConditionalAndAbruptTransfersAreExplicit()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Flow.java"),
        """
        class Flow {
          boolean right() { return true; }
          int test(boolean left) {
            if (left && right()) { throw new IllegalStateException(); }
            return 1;
          }
        }
        """);
    var method = method("test");

    // Act
    var result = build(method);
    var graph = result.graph().orElseThrow();

    // Assert
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.transferKind() == TransferKind.TRUE_BRANCH));
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.transferKind() == TransferKind.FALSE_BRANCH));
    assertTrue(graph.operationNodes().stream().anyMatch(node -> node.kind() == OperationKind.THROW));
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.transferKind() == TransferKind.EXCEPTIONAL));
  }

  @Test
  void givenKnownExceptionAndOrderedCatches_whenCfgIsBuilt_thenOnlyFirstMatchingCatchReceivesTheTransfer()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("CatchOrder.java"),
        """
        class CatchOrder {
          void execute() {
            try {
              throw new IllegalArgumentException();
            } catch (IllegalArgumentException expected) {
            } catch (RuntimeException fallback) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var expectedCatch = graph.operationNodes().stream()
        .filter(node -> node.kind() == OperationKind.CATCH && node.detail().endsWith("IllegalArgumentException"))
        .findFirst().orElseThrow();
    var exceptionalTransfers = graph.edges().stream()
        .filter(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.transferKind() == TransferKind.EXCEPTIONAL).toList();
    assertEquals(1, exceptionalTransfers.size());
    assertEquals(expectedCatch.nodeId(), exceptionalTransfers.getFirst().targetNodeId());
  }

  @Test
  void givenNestedTryWithUnmatchedInnerCatch_whenCfgIsBuilt_thenExceptionReachesOuterCatch()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("NestedTry.java"),
        """
        class NestedTry {
          void execute() {
            try {
              try {
                throw new IllegalStateException();
              } catch (IllegalArgumentException ignored) {
              }
            } catch (IllegalStateException expected) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var outerCatch = graph.operationNodes().stream()
        .filter(node -> node.kind() == OperationKind.CATCH && node.detail().endsWith("IllegalStateException"))
        .findFirst().orElseThrow();
    assertTrue(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(outerCatch.nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
    assertFalse(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(graph.exit().nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
  }

  @Test
  void givenUserDefinedExceptionHierarchy_whenCfgIsBuilt_thenNearestAssignableCatchReceivesTheTransfer()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("ExceptionHierarchy.java"),
        """
        class ExceptionHierarchy {
          static class BaseFailure extends RuntimeException {
          }

          static class SpecificFailure extends BaseFailure {
          }

          void execute() {
            try {
              throw new SpecificFailure();
            } catch (BaseFailure expected) {
            } catch (RuntimeException fallback) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var expectedCatch = graph.operationNodes().stream()
        .filter(node -> node.kind() == OperationKind.CATCH && node.detail().endsWith("BaseFailure"))
        .findFirst().orElseThrow();
    var exceptionalTransfers = graph.edges().stream()
        .filter(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.transferKind() == TransferKind.EXCEPTIONAL).toList();
    assertEquals(1, exceptionalTransfers.size());
    assertEquals(expectedCatch.nodeId(), exceptionalTransfers.getFirst().targetNodeId());
  }

  @Test
  void givenMultiCatch_whenCfgIsBuilt_thenEachCompilerResolvedAlternativeCanHandleTheException()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("MultiCatch.java"),
        """
        class MultiCatch {
          void execute() {
            try {
              throw new IllegalStateException();
            } catch (IllegalArgumentException | IllegalStateException expected) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var catchNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CATCH).findFirst().orElseThrow();
    assertTrue(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(catchNode.nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
  }

  @Test
  void givenMultiCatchWithoutACompatibleAlternative_whenCfgIsBuilt_thenCommonSupertypeDoesNotCauseFalseCatch()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("UnmatchedMultiCatch.java"),
        """
        class UnmatchedMultiCatch {
          void execute() {
            try {
              throw new NullPointerException();
            } catch (IllegalArgumentException | IllegalStateException unrelated) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var catchNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CATCH).findFirst().orElseThrow();
    assertFalse(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(catchNode.nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
    assertTrue(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(graph.exit().nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
  }

  @Test
  void givenUnrelatedExceptionsWithTheSameSimpleName_whenCfgIsBuilt_thenQualifiedTypesPreventFalseCatch()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("QualifiedException.java"),
        """
        class QualifiedException {
          static class Left {
            static class Failure extends RuntimeException {
            }
          }

          static class Right {
            static class Failure extends RuntimeException {
            }
          }

          void execute() {
            try {
              throw new Right.Failure();
            } catch (Left.Failure unrelated) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var catchNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CATCH).findFirst().orElseThrow();
    assertFalse(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(catchNode.nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
    assertTrue(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(graph.exit().nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
  }

  @Test
  void givenSameMethod_whenCfgIsBuiltTwice_thenGraphIdentityAndStructureAreStable()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Stable.java"),
        """
        class Stable {
          int choose(boolean condition) {
            return condition ? 1 : 2;
          }
        }
        """);
    var method = method("choose");
    var builder = new ControlFlowGraphBuilder();

    // Act
    var first = builder.build(method.declaration(), method.syntax());
    var second = builder.build(method.declaration(), method.syntax());

    // Assert
    assertEquals(first, second);
  }

  @Test
  void givenBlockLocalShadowingAField_whenCfgIsBuilt_thenLocalBindingDoesNotEscapeItsScope()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("LexicalScope.java"),
        """
        class LexicalScope {
          int value;

          int read() {
            {
              int value = 1;
            }
            return value;
          }
        }
        """);
    var method = method("read");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var fieldRead = graph.operationNodes().stream()
        .filter(node -> node.kind() == OperationKind.MEMBER_ACCESS && node.detail().equals("value"))
        .findFirst().orElseThrow();
    assertEquals(ValueKind.RECEIVER, fieldRead.operands().getFirst().value().kind());
  }

  private static ControlFlowGraphResult build(MethodInput method) {
    return new ControlFlowGraphBuilder().build(method.declaration(), method.syntax());
  }

  private static SyntaxNode syntaxLeaf(String nodeId, SyntaxKind kind, SyntaxValue value) {
    return SyntaxNode.withoutDirectSource(nodeId, kind, resolvedIntType(), value, List.of(), Optional.empty());
  }

  private static SyntaxType resolvedIntType() {
    return new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"));
  }

  private static MethodInput syntheticMethod(SyntaxType expressionType) {
    var literal = SyntaxNode.withoutDirectSource("literal", SyntaxKind.LITERAL, expressionType, new SyntaxValue.Literal("1"), List.of(),
        Optional.empty());
    var returnNode = SyntaxNode.withoutDirectSource("return", SyntaxKind.RETURN, SyntaxType.NotApplicable.INSTANCE,
        SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, literal)), Optional.empty());
    var root = SyntaxNode.withoutDirectSource("root", SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, returnNode)), Optional.empty());
    var declaration = new JavaMethod("type:Synthetic#value()", "type:Synthetic", "value", "value()", "int", List.of(), Set.of(),
        false, true, true, List.of());
    return new MethodInput(declaration, new MethodSyntax(declaration.methodKey(), root));
  }

  private MethodInput method(String name) throws IOException {
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var declaration = result.program().methods().stream().filter(candidate -> candidate.name().equals(name)).findFirst().orElseThrow();
    return new MethodInput(declaration, result.methodSyntax(declaration.methodKey()).orElseThrow());
  }

  private record MethodInput(JavaMethod declaration, MethodSyntax syntax) {
  }
}
