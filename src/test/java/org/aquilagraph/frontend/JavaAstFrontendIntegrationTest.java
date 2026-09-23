package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.ToolProvider;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.MethodBodyOrigin;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxSemanticRelation;
import org.aquilagraph.syntax.SyntaxSemanticTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaAstFrontendIntegrationTest {
  private static final CompilerConfiguration JAVA_25 = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

  @TempDir Path sourceRoot;

  @Test
  void givenTwoCapturedSources_whenAnalyzedTwice_thenExactAstTuplesAndDiagnosticsRepeat() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Zeta.java"), "class Zeta { }");
    Files.writeString(sourceRoot.resolve("Alpha.java"), "class Alpha { Alpha() { super(); } int value() { return 1; } }");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var snapshot = new JavaSourceLoader().discover(sourceRoot);

    // Act
    var first = frontend.analyze(snapshot, JAVA_25);
    var second = frontend.analyze(snapshot, JAVA_25);
    var firstAst = first.aggregate().orElseThrow();
    var secondAst = second.aggregate().orElseThrow();

    // Assert
    assertEquals(firstAst, secondAst);
    assertEquals(firstAst.orderedNodeKeys(), secondAst.orderedNodeKeys());
    assertEquals(firstAst.nodeTuples(), secondAst.nodeTuples());
    assertEquals(firstAst.rangeTuples(), secondAst.rangeTuples());
    assertEquals(firstAst.structuralEdges(), secondAst.structuralEdges());
    assertEquals(firstAst.semanticEdges(), secondAst.semanticEdges());
    assertEquals(first.diagnostics(), second.diagnostics());
    assertEquals(first.environmentSnapshot(), second.environmentSnapshot());
    assertEquals(snapshot.sourceFiles().stream().map(source -> source.sourceFileKey()).toList(),
        firstAst.roots().stream().map(root -> root.sourceFileKey()).toList());
    var valueMethod = first.program().methods().stream().filter(method -> method.name().equals("value")).findFirst()
        .orElseThrow();
    assertTrue(firstAst.semanticEdges().stream().anyMatch(edge -> edge.relation() == SyntaxSemanticRelation.DECLARATION_REFERENCE
        && edge.target() instanceof SyntaxSemanticTarget.Declaration target
        && target.reference().symbolKey().orElseThrow().equals(valueMethod.methodKey())
        && firstAst.node(edge.sourceKey()).orElseThrow().syntaxKind() == SyntaxKind.METHOD_DECLARATION));
    assertTrue(firstAst.roots().stream().allMatch(root -> root.sourceKind() == JavaSourceKind.COMPILATION_UNIT
        && root.syntaxKind() == SyntaxKind.COMPILATION_UNIT));
    assertEquals(firstAst.nodes().size(), firstAst.rangeTuples().size());
    assertTrue(firstAst.rangeTuples().stream().allMatch(tuple -> tuple.range().endOffsetExclusive() <= snapshot.sourceFiles().stream()
        .filter(source -> source.sourceFileKey().equals(tuple.range().sourceFileKey())).findFirst().orElseThrow().content().length()));
  }

  @Test
  void givenCompilerUnitsInReverseOrder_whenTranslated_thenAggregateFollowsSnapshotOrder() throws IOException {
    Files.writeString(sourceRoot.resolve("Zeta.java"), "class Zeta { }");
    Files.writeString(sourceRoot.resolve("Alpha.java"), "class Alpha { int value() { return 1; } }");
    var snapshot = new JavaSourceLoader().discover(sourceRoot);
    var sources = new SnapshotSourceCatalog(snapshot);
    var invocation = JavaCompilerInvocation.from(JAVA_25);
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var session = CompilerSession.open(compiler, invocation, sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed);
      var reversed = new ArrayList<>(compilation.units());
      Collections.reverse(reversed);
      assertFalse(sources.find(reversed.getFirst()).orElseThrow().sourceFileKey()
          .equals(snapshot.sourceFiles().getFirst().sourceFileKey()));

      var translated = new JavaProgramTranslator(sources, compilation.semantics(), compilation.sourceEvidence(),
          compilation.treeInventory(), JavaLanguageFeatureCoverage.fromConfiguration(JAVA_25)).translate(snapshot, reversed);
      var aggregate = translated.value().aggregate().orElseThrow();
      var normal = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, JAVA_25).aggregate().orElseThrow();

      assertEquals(snapshot.sourceFiles().stream().map(source -> source.sourceFileKey()).toList(),
          aggregate.roots().stream().map(root -> root.sourceFileKey()).toList());
      assertEquals(normal.nodeTuples(), aggregate.nodeTuples());
      assertEquals(normal.structuralEdges(), aggregate.structuralEdges());
      assertEquals(normal.semanticEdges(), aggregate.semanticEdges());
    }
  }

  @Test
  void givenWrittenMethodWithoutCompilerAdditions_whenAnalyzed_thenCoreViewUsesIdenticalSourceBody() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Example.java"),
        "class Example { Example() { super(); } int value() { return 1; } }");

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var aggregate = input.aggregate().orElseThrow();
    var method = input.program().methods().stream().filter(candidate -> candidate.name().equals("value")).findFirst().orElseThrow();
    var body = input.methodSyntax(method.methodKey()).orElseThrow().root();

    // Assert
    assertEquals(MethodBodyOrigin.SOURCE_AST, aggregate.methodBodyOrigin(method.methodKey()).orElseThrow());
    assertSame(body, aggregate.node(body.nodeId()).orElseThrow());
    assertEquals(SyntaxKind.BLOCK, body.syntaxKind());
    assertFalse(body.children(SyntaxRole.STATEMENT).isEmpty());
  }

  @Test
  void givenImplicitAndWrittenConstructors_whenAnalyzed_thenSourceAstOmitsGeneratedSyntaxAndCfgRetainsBoth() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Constructors.java"), "class Plain { } class Written { Written() { } }");

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var aggregate = input.aggregate().orElseThrow();
    var run = new AnalysisEngine().analyze(input);
    var constructors = input.program().methods().stream().filter(method -> method.name().equals("<init>")).toList();
    var plain = constructors.stream().filter(method -> method.ownerTypeKey().endsWith("Plain")).findFirst().orElseThrow();
    var written = constructors.stream().filter(method -> method.ownerTypeKey().endsWith("Written")).findFirst().orElseThrow();
    var writtenBody = input.methodSyntax(written.methodKey()).orElseThrow().root();
    var sourceBody = aggregate.nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.BLOCK).findFirst().orElseThrow();

    // Assert
    assertEquals(2, constructors.size());
    assertEquals(MethodBodyOrigin.EXECUTION_PROJECTION, aggregate.methodBodyOrigin(plain.methodKey()).orElseThrow());
    assertEquals(MethodBodyOrigin.EXECUTION_PROJECTION, aggregate.methodBodyOrigin(written.methodKey()).orElseThrow());
    assertTrue(aggregate.nodes().stream().noneMatch(node -> node.sourceKind() == JavaSourceKind.METHOD
        && node.structuralPath().steps().get(1).position() == 0));
    assertTrue(sourceBody.children(SyntaxRole.STATEMENT).isEmpty());
    assertFalse(writtenBody.children(SyntaxRole.STATEMENT).isEmpty());
    assertNotSame(sourceBody, writtenBody);
    assertEquals(List.of(plain.methodKey(), written.methodKey()), run.representation().orElseThrow().methodAnalyses().stream()
        .map(analysis -> analysis.sourceMethod().methodKey()).toList());
  }
}
