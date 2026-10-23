package org.aquilagraph.frontend.internal;

import org.aquilagraph.source.internal.SourceValueCapture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.internal.AstResultAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.CopiedAstValues.Body;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class CfgSourceAssociationTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenEqualLocalKeysAcrossOwnershipNamespaces_whenDerivingClosedValues_thenOnlyWrittenNodesAcquireOccurrences() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED).flatMap(profile -> Stream.of(false, true)
        .flatMap(refused -> Stream.of(false, true).map(preserveOrigins -> DynamicTest.dynamicTest(
            profile.version() + ": refused=" + refused + ", equal-values=" + preserveOrigins,
            () -> assertNamespaces(profile, refused, preserveOrigins)))));
  }

  private void assertNamespaces(AstProfile profile, boolean refused, boolean preserveOrigins) throws IOException {
    // Arrange: auxiliary copies keep the compiler's independent inventories and actual source tree unchanged.
    var source = refused ? "class Example { int subject() { return 7; } }"
        : "class Example { void subject() { return; } }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var original = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var writtenKey = original.program().methods().stream().filter(method -> method.name().equals("subject")).findFirst().orElseThrow().methodKey();
    var auxiliaryKey = original.program().methods().stream().filter(method -> method.constructor()).findFirst().orElseThrow().methodKey();
    var captured = SourceValueCapture.capture(original.aggregate().orElseThrow());
    var writtenBody = captured.bodies().stream().filter(body -> body.methodKey().equals(writtenKey)).findFirst().orElseThrow();
    var bodies = captured.bodies().stream().map(body -> body.methodKey().equals(auxiliaryKey)
        ? new Body(auxiliaryKey, copy(writtenBody.root(), preserveOrigins), body.origin(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of())
        : body).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), captured.sources(), bodies, captured.memberOrders(),
        captured.associations(), captured.declarationTypes()));
    var supplied = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(aggregate),
        JavaCoverageTestSupport.emissions(original));
    var admitted = AstResultAdmission.admit(supplied, profile, "javac-public/1");
    var ast = AstCodec.decode(AstCodec.encode(admitted), admitted.identity());
    var written = ast.body(writtenKey).orElseThrow().root();
    var auxiliary = ast.body(auxiliaryKey).orElseThrow().root();
    var sourceBytes = AstCodec.encode(ast);

    // Act
    var result = CfgDerivers.standard().derive(ast,
        new CfgRequest(ast.identity(), List.of(writtenKey, auxiliaryKey), CfgProfile.JAVA_BOUNDED));
    var writtenOutcome = result.methods().getFirst();
    var auxiliaryOutcome = result.methods().getLast();

    // Assert
    assertEquals(written.nodeId(), auxiliary.nodeId());
    assertNotSame(written, auxiliary);
    assertEquals(preserveOrigins, written.equals(auxiliary));
    assertEquals(Optional.of(new SourceOccurrence(ast.identity(), written.nodeId())), ast.occurrence(written));
    assertTrue(ast.occurrence(auxiliary).isEmpty());
    var foreignCopy = AstCodec.decode(sourceBytes, ast.identity()).body(writtenKey).orElseThrow().root();
    assertEquals(written, foreignCopy);
    assertNotSame(written, foreignCopy);
    assertTrue(ast.occurrence(foreignCopy).isEmpty());
    assertSame(written, ast.node(new SourceOccurrence(ast.identity(), auxiliary.nodeId())).orElseThrow());
    assertTrue(ast.bodyRef(auxiliaryKey).orElseThrow().sourceOccurrenceKey().isEmpty());
    assertTrue(ast.bodyRef(auxiliaryKey).orElseThrow().nonwrittenProvenance().isPresent());
    if (refused) {
      assertEquals(MethodCfgState.INSUFFICIENT_EVIDENCE, auxiliaryOutcome.state());
      assertFalse(auxiliaryOutcome.blockers().isEmpty());
      assertTrue(auxiliaryOutcome.blockers().stream().allMatch(blocker -> blocker.occurrence().isEmpty()), auxiliaryOutcome.blockers().toString());
      assertEquals(MethodCfgState.AVAILABLE, writtenOutcome.state());
    } else {
      assertEquals(MethodCfgState.AVAILABLE, auxiliaryOutcome.state());
      var graph = auxiliaryOutcome.graph().orElseThrow();
      assertFalse(graph.operationNodes().isEmpty());
      assertTrue(graph.operationNodes().stream().allMatch(operation -> operation.sourceRef().occurrence().isEmpty()),
          graph.operationNodes().toString());
      assertTrue(graph.operationNodes().stream().allMatch(operation -> operation.sourceRef().body().equals(ast.bodyRef(auxiliaryKey).orElseThrow())));
      assertTrue(writtenOutcome.graph().orElseThrow().operationNodes().stream()
          .allMatch(operation -> operation.sourceRef().occurrence().isPresent()));
    }
    assertEquals(admitted.identity(), ast.identity());
    assertEquals(admitted.completion(), ast.completion());
    assertEquals(admitted.diagnostics(), ast.diagnostics());
    assertArrayEquals(sourceBytes, AstCodec.encode(ast));
  }

  private static SyntaxNode copy(SyntaxNode node, boolean preserveOrigins) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), copy(child.node(), preserveOrigins))).toList();
    return preserveOrigins && node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children,
            node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children,
            node.semanticReference());
  }
}
