package org.aquilagraph.repository.internal.neo4j;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;



import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.ValueKind;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class Neo4jEnumContextProjectionTest {
  @TempDir
  Path sourceRoot;

  @Test
  void givenCompletedEnumConstructors_whenMapped_thenContextValuesAndOperandOrderRemainSeparateFromSourceDeclarations() throws IOException {
    // Arrange
    var source = SourceFile.fromContent(capturedPath(Path.of("Enums.java")),
        "enum Empty { ; } enum Delegated { ; Delegated() { this(7); } Delegated(int value) {} }");
    var input = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(InputSnapshot.fromSources(capturedAbsolute(sourceRoot), List.of(source)),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();
    var run = new SourceAnalysisFixture().analyze(input);
    assertEquals(AnalysisCompletion.COMPLETE, run.completion(), () -> run.diagnostics().values().toString());

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(run);

    // Assert
    var graphs = run.representation().cfg().orElseThrow().methods().stream().map(analysis -> analysis.graph().orElseThrow()).toList();
    assertEquals(3, graphs.size());
    assertEquals(1, projection.nodes().stream().filter(node -> node.label().equals("JavaParameter")).count());
    var implicit = projection.nodes().stream().filter(node -> node.label().equals("FlowValue")
        && node.properties().get("kind").equals("IMPLICIT_PARAMETER")).toList();
    assertEquals(6, implicit.size());
    for (var graph : graphs) {
      var values = graph.values().stream().filter(value -> value.kind() == ValueKind.IMPLICIT_PARAMETER).toList();
      assertEquals(2, values.size());
      assertEquals(List.of("enum-name", "enum-ordinal"), values.stream().map(value -> value.displayName().orElseThrow()).toList());
      for (var value : values) {
        var node = projection.nodes().stream().filter(candidate -> candidate.key().equals(value.valueId())).findFirst().orElseThrow();
        assertEquals("FlowValue", node.label());
        assertEquals("IMPLICIT_PARAMETER", node.properties().get("kind"));
        assertEquals(graph.graphId(), node.properties().get("graphId"));
        assertEquals(value.displayName().orElseThrow(), node.properties().get("displayName"));
        assertEquals(value.staticType().orElseThrow().erasedName(), node.properties().get("staticTypeErasure"));
        assertEquals(value.staticType().orElseThrow().category().name(), node.properties().get("staticTypeCategory"));
        assertFalse(node.properties().containsKey("literal"));
        assertTrue(projection.relationships().stream().noneMatch(relation -> relation.type().equals("DEFINES_VALUE")
            && relation.targetKey().equals(value.valueId())));
        assertTrue(projection.relationships().stream().noneMatch(relation -> relation.type().equals("HAS_ORIGIN")
            && relation.sourceKey().equals(value.valueId())));
      }
      var invocation = graph.operationNodes().stream().filter(operation -> operation.kind() == OperationKind.INVOKE).findFirst().orElseThrow();
      var uses = projection.relationships().stream().filter(relation -> relation.type().equals("USES_VALUE")
          && relation.sourceKey().equals(invocation.nodeId())).toList();
      assertEquals(invocation.operands().stream().map(operand -> operand.value().valueId()).toList(), uses.stream().map(Neo4jRelationship::targetKey).toList());
      assertEquals(List.of(0, 1, 2), uses.subList(0, 3).stream().map(relation -> relation.properties().get("position")).toList());
    }
  }
}
