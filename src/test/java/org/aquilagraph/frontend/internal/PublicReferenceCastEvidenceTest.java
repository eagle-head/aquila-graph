package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.cfg.internal.CfgReadiness;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class PublicReferenceCastEvidenceTest {
  @TempDir Path sourceRoot;

  @Test
  void givenWideningLiteralAndConditionalCasts_whenPublicFactsAreCopied_thenDecisionsTransportTheirReferenceCheckClassification()
      throws IOException {
    // Arrange
    var source = """
        class Example {
          Object literal() { return (Object)"text"; }
          Object conditional(boolean flag) { return (Object)(flag ? "left" : "right"); }
          String narrow(Object value) { return (String)value; }
          Object widen(String value) { return (Object)value; }
        }
        """;
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var compilerInput = SimpleJavaFileObject.forSource(sourceRoot.resolve("Example.java").toUri(), source);
    var observed = new ArrayList<CastEvidence>();

    // Act
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release", "25", "-proc:none"), null,
          List.of(compilerInput));
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      task.analyze();
      var trees = Trees.instance(task);
      var visitor = new TreePathScanner<Void, Void>() {
        @Override
        public Void visitTypeCast(TypeCastTree tree, Void unused) {
          var input = trees.getTypeMirror(new TreePath(getCurrentPath(), tree.getExpression()));
          var target = trees.getTypeMirror(getCurrentPath());
          observed.add(new CastEvidence(input.toString(), target.toString(), task.getTypes().isAssignable(input, target)));
          return super.visitTypeCast(tree, unused);
        }
      };
      units.forEach(unit -> visitor.scan(unit, null));
    }
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(sourceRoot), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());

    // Assert
    assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
    assertEquals(List.of(new CastEvidence("java.lang.String", "java.lang.Object", true),
        new CastEvidence("java.lang.String", "java.lang.Object", true), new CastEvidence("java.lang.Object", "java.lang.String", false),
        new CastEvidence("java.lang.String", "java.lang.Object", true)), observed);
    for (var methodName : List.of("literal", "conditional")) {
      var declaration = input.program().methods().stream().filter(method -> method.name().equals(methodName)).findFirst().orElseThrow();
      var syntax = input.methodSyntax(declaration.methodKey()).orElseThrow();
      var cast = syntax.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow();
      assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(syntax));
      assertTrue(cast.requiredChild(SyntaxRole.EXPRESSION).semanticReference().isEmpty());
      assertTrue(syntax.useDecision(cast.nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow().checkedReferenceTarget().isEmpty());
      assertEquals(Optional.of(false), syntax.useEvidence(cast.nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow().referenceCheck().knownValue());
    }
  }

  private record CastEvidence(String sourceType, String targetType, boolean assignable) {}
}
