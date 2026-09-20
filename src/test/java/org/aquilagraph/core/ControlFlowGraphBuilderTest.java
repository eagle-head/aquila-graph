package org.aquilagraph.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.TransferKind;
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

  private org.aquilagraph.model.JavaMethod method(String name) throws IOException {
    var frontend = new JavaFrontend(new JavaSourceLoader());
    return frontend.analyze(temporaryDirectory, CompilerConfiguration.java25()).program().methods().stream()
        .filter(candidate -> candidate.name().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
