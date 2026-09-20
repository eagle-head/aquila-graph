package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProgramRepresentationTest {
  @Test
  void givenAnalysisUsesCanonicalMethod_whenRepresentationIsCreated_thenAnalysisIsQueryable() {
    // Arrange
    var canonicalMethod = method("method-1", "calculate");
    var program = program(canonicalMethod);
    var analysis =
        new MethodAnalysis("analysis-1", canonicalMethod, graph(canonicalMethod.methodKey()));

    // Act
    var representation = new ProgramRepresentation(program, List.of(analysis));

    // Assert
    assertEquals(analysis, representation.methodAnalysis(canonicalMethod.methodKey()).orElseThrow());
  }

  @Test
  void givenAnalysisUsesDivergentMethodVersion_whenRepresentationIsCreated_thenInputIsRejected() {
    // Arrange
    var canonicalMethod = method("method-1", "calculate");
    var divergentMethod = method("method-1", "calculateDifferently");
    var program = program(canonicalMethod);
    var analysis =
        new MethodAnalysis("analysis-1", divergentMethod, graph(divergentMethod.methodKey()));

    // Act
    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ProgramRepresentation(program, List.of(analysis)));

    // Assert
    assertEquals(
        "Method analysis must reference the canonical program method", exception.getMessage());
  }

  @Test
  void givenMethodHasTwoAnalyses_whenRepresentationIsCreated_thenInputIsRejected() {
    // Arrange
    var canonicalMethod = method("method-1", "calculate");
    var program = program(canonicalMethod);
    var first =
        new MethodAnalysis("analysis-1", canonicalMethod, graph(canonicalMethod.methodKey()));
    var second =
        new MethodAnalysis("analysis-2", canonicalMethod, graph(canonicalMethod.methodKey()));

    // Act
    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ProgramRepresentation(program, List.of(first, second)));

    // Assert
    assertEquals(
        "Each program method may have at most one method analysis", exception.getMessage());
  }

  private static JavaProgram program(JavaMethod method) {
    return new JavaProgram(
        List.of(
            new JavaType(
                "type-1",
                "Example",
                JavaTypeKind.CLASS,
                true,
                List.of(method),
                List.of())));
  }

  private static JavaMethod method(String methodKey, String name) {
    return new JavaMethod(
        methodKey,
        "type-1",
        name,
        name + "()",
        "int",
        List.of(),
        Set.of(),
        false,
        true,
        Optional.empty(),
        List.of());
  }

  private static ControlFlowGraph graph(String methodKey) {
    var graphId = "graph-1";
    return new ControlFlowGraph(
        graphId,
        methodKey,
        new EntryNode("entry", graphId),
        List.of(),
        List.of(),
        new ExitNode("exit", graphId),
        List.of());
  }
}
