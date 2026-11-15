package org.aquilagraph.cfg.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class ReferenceCastLoweringTest {
  @TempDir Path sourceRoot;

  @Test
  void givenLiteralAndConditionalWideningCasts_whenFrontendAndGraphBuilderRun_thenCastsHaveNoClassCastEffect() throws IOException {
    // Arrange
    var source = """
        class Example {
          Object literal() { return (Object) "text"; }
          Object conditional(boolean flag) { return (Object) (flag ? "left" : "right"); }
        }
        """;

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(
        InputSnapshot.fromSources(capturedAbsolute(sourceRoot), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();

    // Assert
    for (var name : List.of("literal", "conditional")) {
      var method = input.program().methods().stream().filter(candidate -> candidate.name().equals(name)).findFirst().orElseThrow();
      var syntax = input.aggregate().methodSyntax(method.methodKey()).orElseThrow();
      assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(syntax));
      var result = new ControlFlowGraphBuilder().build(input, CfgAdmission.assess(input, method.methodKey(), CfgProfile.JAVA_BOUNDED).plan().orElseThrow());
      assertTrue(result.diagnostics().isEmpty());
      var graph = result.graph().orElseThrow();
      var cast = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CAST).findFirst().orElseThrow();
      var returned = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.RETURN).findFirst().orElseThrow();
      assertEquals("java.lang.Object", cast.definedValue().orElseThrow().staticType().orElseThrow().erasedName());
      assertEquals(cast.definedValue().orElseThrow(), returned.operands().getFirst().value());
      assertTrue(cast.possibleExceptions().isEmpty());
      assertTrue(graph.edges().stream().noneMatch(edge -> edge.sourceNodeId().equals(cast.nodeId())
          && edge.transferKind() == TransferKind.EXCEPTIONAL));
    }
  }

  @Test
  void givenNarrowingReferenceAndArrayCasts_whenFrontendAndGraphBuilderRun_thenEachCastRetainsItsClassCastRoute() throws IOException {
    // Arrange
    var source = """
        class Example {
          String reference(Object value) { return (String) value; }
          int[] array(Object value) { return (int[]) value; }
        }
        """;

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(
        InputSnapshot.fromSources(capturedAbsolute(sourceRoot), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();

    // Assert
    for (var name : List.of("reference", "array")) {
      var method = input.program().methods().stream().filter(candidate -> candidate.name().equals(name)).findFirst().orElseThrow();
      var syntax = input.aggregate().methodSyntax(method.methodKey()).orElseThrow();
      assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(syntax));
      var graph = new ControlFlowGraphBuilder().build(input, CfgAdmission.assess(input, method.methodKey(), CfgProfile.JAVA_BOUNDED).plan().orElseThrow()).graph().orElseThrow();
      var cast = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CAST).findFirst().orElseThrow();
      assertEquals(List.of("java.lang.ClassCastException"), cast.possibleExceptions());
      assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(cast.nodeId())
          && edge.transferKind() == TransferKind.EXCEPTIONAL
          && edge.discriminator().orElseThrow().equals("java.lang.ClassCastException")));
    }
  }

  @Test
  void givenErasureNullArrayTypeVariableAndIntersectionCasts_whenLowered_thenOnlyRequiredValidityChecksHaveClassCastEffects() throws IOException {
    // Arrange
    var source = """
        import java.util.List;
        class Example {
          List<String> complete(List<?> values) { return (List<String>) values; }
          List<String> partial(Object value) { return (List<String>) value; }
          String nullValue() { return (String) null; }
          Object arrayWiden(int[] value) { return (Object) value; }
          <T extends CharSequence> T variable(Object value) { return (T) value; }
          CharSequence intersection(Object value) { return (java.io.Serializable & CharSequence) value; }
          CharSequence intersectionWiden(String value) { return (java.io.Serializable & CharSequence) value; }
        }
        """;
    var expectedChecks = Map.of("complete", false, "partial", true, "nullValue", false, "arrayWiden", false,
        "variable", true, "intersection", true, "intersectionWiden", false);

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(
        InputSnapshot.fromSources(capturedAbsolute(sourceRoot), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();

    // Assert
    for (var entry : expectedChecks.entrySet()) {
      var method = input.program().methods().stream().filter(candidate -> candidate.name().equals(entry.getKey())).findFirst().orElseThrow();
      var syntax = input.aggregate().methodSyntax(method.methodKey()).orElseThrow();
      assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(syntax), entry.getKey());
      var castNode = syntax.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST)
          .findFirst().orElseThrow();
      assertEquals(Optional.of(entry.getValue()), syntax.useEvidence(castNode.nodeId(), SyntaxRole.EXPRESSION, 0)
          .orElseThrow().referenceCheck().knownValue(), entry.getKey());
      var graph = new ControlFlowGraphBuilder().build(input, CfgAdmission.assess(input, method.methodKey(), CfgProfile.JAVA_BOUNDED).plan().orElseThrow()).graph().orElseThrow();
      var cast = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CAST).findFirst().orElseThrow();
      assertEquals(entry.getValue() ? List.of("java.lang.ClassCastException") : List.of(), cast.possibleExceptions(), entry.getKey());
      assertEquals(entry.getValue(), graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(cast.nodeId())
          && edge.transferKind() == TransferKind.EXCEPTIONAL), entry.getKey());
    }
  }
}
