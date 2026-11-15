package org.aquilagraph.cfg.api;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.GraphNodeKey;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ControlFlowGraphTest {
  @TempDir Path directory;

  @Test
  void givenAnAdmittedLiteralReturn_whenQueryingAdjacency_thenTheCompleteEvaluationOrderIsStable() {
    // Arrange
    var graph = graph("class Example { int value() { return 1; } }");
    var literal = graph.operationNodes().getFirst();
    var returned = graph.operationNodes().getLast();
    // Act
    var first = graph.successors(graph.entry());
    var repeated = graph.successors(graph.entry());
    // Assert
    assertEquals(List.of(OperationKind.CONSTANT, OperationKind.RETURN), graph.operationNodes().stream().map(OperationNode::kind).toList());
    assertEquals(List.of(literal), first);
    assertEquals(first, repeated);
    assertEquals(List.of(returned), graph.successors(literal));
    assertEquals(List.of(graph.exit()), graph.successors(returned));
    assertEquals(List.of(returned), graph.predecessors(graph.exit()));
    assertEquals(List.of(graph.entry(), literal, returned, graph.exit()), graph.nodes());
  }

  @Test
  void givenAnAdmittedGraph_whenQueryingOwnedUnknownAndForeignKeys_thenMembershipAndAbsenceRemainDistinct() {
    // Arrange
    var graph = graph("class Example { int value() { return 1; } }");
    var foreign = graph("class Example { int value() { return 2; } }");
    var owned = new GraphNodeKey(graph.identity(), graph.entry().nodeId());
    // Act
    var node = graph.node(owned);
    var unknown = graph.node(new GraphNodeKey(graph.identity(), "unknown"));
    // Assert
    assertSame(graph.entry(), node.orElseThrow());
    assertTrue(unknown.isEmpty());
    assertThrows(IllegalArgumentException.class, () -> graph.node(new GraphNodeKey(foreign.identity(), graph.entry().nodeId())));
    assertTrue(graph.successors(new EntryNode("unknown", graph.graphId())).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> graph.successors(foreign.entry()));
  }

  @Test
  void givenRepeatedDerivation_whenQueryingPublishedValues_thenQualificationAndImmutableStructureAreExact() {
    // Arrange
    var source = "class Example { int value() { return 1; } }";
    var graph = graph(source);
    // Act
    var repeated = graph(source);
    // Assert
    assertEquals(graph, repeated);
    assertEquals(graph.hashCode(), repeated.hashCode());
    assertEquals(graph.identity().astIdentity(), graph.body().astIdentity());
    assertTrue(graph.operationNodes().stream().allMatch(node -> node.sourceRef().body().equals(graph.body())));
    assertThrows(UnsupportedOperationException.class, () -> graph.operationNodes().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.edges().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.values().clear());
  }

  private ControlFlowGraph graph(String text) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), text)));
    var ast = JavaFrontends.standard().produce(new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();
    var method = ast.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
    return CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED))
        .methods().getFirst().graph().orElseThrow();
  }
}
