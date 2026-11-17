package org.aquilagraph.cfg.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.library.AquilaGraph;
import org.aquilagraph.library.ObservabilityConfiguration;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.InvocationMode;
import org.aquilagraph.source.api.InvocationSelection;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArrayCloneGraphTest {
  @TempDir Path directory;

  @Test
  void givenArrayCloneVariants_whenLibraryDerivesAndRetainedSourceIsReused_thenExactSelectionsAndTypesReachGraphs() {
    // Arrange
    var request = request("""
        interface Copies<T> {
          static int[] primitive(int[] value) { return value.clone(); }
          static String[] reference(String[] value) { return value.clone(); }
          default T[] generic(T[] value) { return value.clone(); }
          static int[][] matrix(int[][] value) { return value.clone(); }
          static int[] explicit(int[] value) { return value.<String>clone(); }
        }
        """);
    try (var library = AquilaGraph.open(ObservabilityConfiguration.disabled())) {
      // Act
      var run = library.analyze(new AnalysisComputationRequest(request, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))));
      var original = run.representation().ast().result().orElseThrow();
      var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
      var selected = new CfgRequest(retained.identity(), retained.program().methods().stream().map(method -> method.methodKey()).toList(),
          CfgProfile.JAVA_BOUNDED);
      var rederived = CfgDerivers.standard().derive(retained, selected);

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(AnalysisCompletion.COMPLETE, run.completion(), run.diagnostics().values().toString());
      var expectedTypes = Map.of("primitive", type("int[]", "int[]"), "reference", type("java.lang.String[]", "java.lang.String[]"),
          "generic", type("T[]", "java.lang.Object[]"), "matrix", type("int[][]", "int[][]"), "explicit", type("int[]", "int[]"));
      for (var outcome : rederived.methods()) {
        var graph = outcome.graph().orElseThrow();
        var method = retained.method(outcome.methodKey()).orElseThrow();
        var expected = expectedTypes.get(method.name());
        var clone = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.INVOKE).findFirst().orElseThrow();
        var semantics = clone.invocationSemantics().orElseThrow();
        assertEquals(InvocationSelection.ARRAY_CLONE, semantics.selection());
        assertEquals(InvocationMode.VIRTUAL, semantics.mode());
        assertEquals(InvocationForm.INSTANCE_EXPRESSION, semantics.form());
        assertEquals(expected, semantics.qualifyingType().identity());
        assertTrue(semantics.qualifyingType().declarationKey().isEmpty());
        assertTrue(clone.semanticReferences().isEmpty());
        assertEquals(1, clone.operands().size());
        assertEquals(Optional.of(expected), clone.operands().getFirst().value().staticType());
        assertEquals(Optional.of(expected), clone.definedValue().orElseThrow().staticType());
        assertTrue(clone.possibleExceptions().contains("java.lang.NullPointerException"));
        assertFalse(clone.possibleExceptions().contains("java.lang.CloneNotSupportedException"));
        assertEquals("java-cfg/3", graph.identity().graphModelVersion());
        var originalGraph = run.representation().cfg().orElseThrow().methods().stream()
            .filter(value -> value.methodKey().equals(outcome.methodKey())).findFirst().orElseThrow().graph().orElseThrow();
        assertEquals(originalGraph.identity(), graph.identity());
      }
      assertEquals(5, rederived.methods().size());
      var refused = CfgDerivers.standard().derive(retained, new CfgRequest(retained.identity(), selected.methodKeys(),
          new CfgProfile("java-cfg-bounded/1", "java-cfg/2")));
      assertEquals(CfgState.REJECTED, refused.state());
      assertTrue(refused.methods().isEmpty());
      assertEquals(List.of("control-flow.incompatible-profile"), refused.diagnostics().stream().map(value -> value.code()).toList());
    }
  }

  @Test
  void givenChainedClonesOnAnEffectfulReceiver_whenGraphIsDerived_thenReceiverRunsOnceAndEachResultFeedsTheNextCall() {
    // Arrange
    var request = request("""
        interface Copies {
          static int[] receiver(int[] value) { return value; }
          static int[] copy(int[] value) { return receiver(value).clone().clone(); }
        }
        """);
    try (var library = AquilaGraph.open(ObservabilityConfiguration.disabled())) {
      // Act
      var run = library.analyze(new AnalysisComputationRequest(request, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))));
      var method = run.representation().ast().result().orElseThrow().program().methods().stream()
          .filter(value -> value.name().equals("copy")).findFirst().orElseThrow();
      var graph = run.representation().cfg().orElseThrow().methods().stream().filter(value -> value.methodKey().equals(method.methodKey()))
          .findFirst().orElseThrow().graph().orElseThrow();

      // Assert
      var calls = graph.operationNodes().stream().filter(value -> value.kind() == OperationKind.INVOKE).toList();
      assertEquals(List.of("receiver", "clone", "clone"), calls.stream().map(value -> value.detail()).toList());
      assertEquals(List.of(InvocationSelection.NAMED_EXECUTABLE, InvocationSelection.ARRAY_CLONE, InvocationSelection.ARRAY_CLONE),
          calls.stream().map(value -> value.invocationSemantics().orElseThrow().selection()).toList());
      for (var index = 1; index < calls.size(); index++) {
        var previous = calls.get(index - 1);
        var current = calls.get(index);
        assertEquals(previous.definedValue().orElseThrow(), current.operands().getFirst().value());
        assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(previous.nodeId())
            && edge.targetNodeId().equals(current.nodeId()) && edge.transferKind() == TransferKind.NORMAL));
        assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(current.nodeId())
            && edge.discriminator().filter("java.lang.NullPointerException"::equals).isPresent()));
      }
    }
  }

  private AstProductionRequest request(String content) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Copies.java")), content)));
    return new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }

  private static JavaTypeIdentity type(String display, String erasure) {
    return new JavaTypeIdentity(JavaTypeCategory.ARRAY, display, erasure);
  }
}
