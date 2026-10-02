package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
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

  @Test
  void givenForeignMethod_whenRepresentationIsCreated_thenCanonicalMembershipIsRequired() {
    // Arrange
    var canonical = method("method:calculate", "calculate");
    var foreign = method("method:external", "external");
    var analysis = new MethodAnalysis("analysis:external", foreign, graph(foreign.methodKey()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new ProgramRepresentation(program(canonical), List.of(analysis)));

    // Assert
    assertEquals("Method analysis must reference the canonical program method", failure.getMessage());
  }

  @Test
  void givenEqualDistinctCanonicalMethod_whenRepresentationIsCreated_thenFullValueAssociationIsAccepted() {
    // Arrange
    var canonical = method("method:calculate", "calculate");
    var equalMethod = method("method:calculate", "calculate");
    var analysis = new MethodAnalysis("analysis:calculate", equalMethod, graph(equalMethod.methodKey()));

    // Act
    var representation = new ProgramRepresentation(program(canonical), List.of(analysis));

    // Assert
    assertEquals(canonical, equalMethod);
    assertSame(equalMethod, representation.methodAnalysis(canonical.methodKey()).orElseThrow().sourceMethod());
  }

  @Test
  void givenBodylessAnalysisReplacesRequiredBody_whenRepresentationIsCreated_thenDirectConstructionDoesNotQualifyCoverage() {
    // Arrange
    var required = new JavaMethod("method:required", "type-1", "required", "required()", "int", List.of(), Set.of(), false, true,
        true, List.of());
    var bodyless = method("method:bodyless", "bodyless");
    var canonicalType = program(required).types().getFirst();
    var program = new JavaProgram(List.of(new JavaType(canonicalType.typeKey(), canonicalType.qualifiedName(), canonicalType.kind(),
        Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(),
        List.of(required, bodyless), List.of(), List.of())));
    var replacement = new MethodAnalysis("analysis:bodyless", bodyless, graph(bodyless.methodKey()));

    // Act
    var representation = new ProgramRepresentation(program, List.of(replacement));

    // Assert
    assertEquals(1, representation.methodAnalyses().size());
    assertFalse(representation.methodAnalysis(required.methodKey()).isPresent());
    assertEquals(replacement, representation.methodAnalysis(bodyless.methodKey()).orElseThrow());
  }

  @Test
  void givenOrderedMutableAnalysisList_whenRepresentationIsCreated_thenSuppliedOrderIsFrozenAndQueriesAreBounded() {
    // Arrange
    var firstMethod = method("method:first", "first");
    var secondMethod = method("method:second", "second");
    var canonicalType = program(firstMethod).types().getFirst();
    var program = new JavaProgram(List.of(new JavaType(canonicalType.typeKey(), canonicalType.qualifiedName(), canonicalType.kind(),
        Set.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(),
        List.of(firstMethod, secondMethod), List.of(), List.of())));
    var first = new MethodAnalysis("analysis:first", firstMethod, graph(firstMethod.methodKey()));
    var second = new MethodAnalysis("analysis:second", secondMethod, graph(secondMethod.methodKey()));
    var supplied = new ArrayList<>(List.of(second, first));

    // Act
    var representation = new ProgramRepresentation(program, supplied);
    supplied.clear();

    // Assert
    assertEquals(List.of(second, first), representation.methodAnalyses());
    assertFalse(representation.methodAnalysis("method:absent").isPresent());
    assertEquals("methodKey", assertThrows(NullPointerException.class, () -> representation.methodAnalysis(null)).getMessage());
    assertThrows(UnsupportedOperationException.class, () -> representation.methodAnalyses().clear());
  }

  @Test
  void givenNullProgramOrAnalysisList_whenRepresentationIsCreated_thenRequiredBoundariesRejectNull() {
    // Arrange
    var program = program(method("method:calculate", "calculate"));

    // Act
    var absentProgram = assertThrows(NullPointerException.class, () -> new ProgramRepresentation(null, List.of()));
    var absentAnalyses = assertThrows(NullPointerException.class, () -> new ProgramRepresentation(program, null));

    // Assert
    assertEquals("program", absentProgram.getMessage());
    assertEquals("methodAnalyses", absentAnalyses.getMessage());
  }

  private static JavaProgram program(JavaMethod method) {
    return new JavaProgram(
        List.of(
            new JavaType(
                "type-1",
                "Example",
                JavaTypeKind.CLASS,
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                List.of(),
                List.of(method),
                List.of(),
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
        false,
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
