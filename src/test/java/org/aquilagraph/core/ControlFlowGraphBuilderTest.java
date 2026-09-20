package org.aquilagraph.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;
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
    var result = new ControlFlowGraphBuilder().build(method);

    // Assert
    var operations = result.graph().orElseThrow().operationNodes();
    var invocationPositions =
        java.util.stream.IntStream.range(0, operations.size())
            .filter(index -> operations.get(index).kind() == OperationKind.INVOKE)
            .boxed()
            .toList();
    assertEquals(2, invocationPositions.size());
    var binaryPosition =
        java.util.stream.IntStream.range(0, operations.size())
            .filter(index -> operations.get(index).kind() == OperationKind.BINARY)
            .findFirst()
            .orElseThrow();
    assertTrue(invocationPositions.get(0) < invocationPositions.get(1));
    assertTrue(invocationPositions.get(1) < binaryPosition);
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
    var result = new ControlFlowGraphBuilder().build(method);
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
    var graph = new ControlFlowGraphBuilder().build(method).graph().orElseThrow();

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
    var graph = new ControlFlowGraphBuilder().build(method).graph().orElseThrow();

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
    var graph = new ControlFlowGraphBuilder().build(method).graph().orElseThrow();

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
    var graph = new ControlFlowGraphBuilder().build(method).graph().orElseThrow();

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
    var graph = new ControlFlowGraphBuilder().build(method).graph().orElseThrow();

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
    var graph = new ControlFlowGraphBuilder().build(method).graph().orElseThrow();

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
    var first = builder.build(method);
    var second = builder.build(method);

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
    var graph = new ControlFlowGraphBuilder().build(method).graph().orElseThrow();

    // Assert
    var fieldRead = graph.operationNodes().stream()
        .filter(node -> node.kind() == OperationKind.READ && node.detail().equals("value"))
        .findFirst().orElseThrow();
    assertEquals(ValueKind.SYMBOL, fieldRead.operands().getFirst().value().kind());
  }

  private org.aquilagraph.model.JavaMethod method(String name) throws IOException {
    var frontend = new JavaFrontend(new JavaSourceLoader());
    return frontend.analyze(temporaryDirectory, CompilerConfiguration.standard(JavaRelease.JAVA_25)).program().methods().stream()
        .filter(candidate -> candidate.name().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
