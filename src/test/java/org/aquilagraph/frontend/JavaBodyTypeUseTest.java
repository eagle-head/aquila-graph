package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.cfg.internal.CfgReadiness;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaBodyTypeUseTest {
  @TempDir Path sourceRoot;

  @Test
  void givenFixedAndDiamondCreationTypes_whenAnalyzed_thenTypesAndArgumentsShareTheCanonicalSourceBody()
      throws IOException {
    // Arrange
    var source = """
        import java.util.List;
        class Example<T> {
          Example(T value) { }
          Example<List<String[]>> fixed(List<String[]> values) { return new Example<List<String[]>>(values); }
          Example<List<String[]>> diamond(List<String[]> values) { return new Example<>(values); }
        }
        """;

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(
        InputSnapshot.fromSources(sourceRoot, List.of(SourceFile.fromContent(Path.of("Example.java"), source))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, run.completion(), () -> run.diagnostics().toString());
    var sourceCreations = input.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION).toList();
    assertEquals(2, sourceCreations.size());
    var sourceTypes = sourceCreations.stream().map(node -> node.requiredChild(SyntaxRole.TYPE_OPERAND)).toList();
    assertEquals(1, sourceTypes.stream().filter(node -> node.value() == SyntaxValue.DiamondType.INSTANCE).count());
    assertEquals(List.of("Example<List<String[]>>", "Example<>"), sourceTypes.stream().map(node -> {
      var range = node.origin().orElseThrow();
      return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
    }).toList());
    for (var name : List.of("fixed", "diamond")) {
      var declaration = input.program().methods().stream().filter(method -> method.name().equals(name)).findFirst().orElseThrow();
      var method = input.methodSyntax(declaration.methodKey()).orElseThrow();
      assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(method));
      var creation = method.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
          .findFirst().orElseThrow();
      assertEquals(1, creation.children(SyntaxRole.ARGUMENT).size());
      assertEquals(1, creation.children(SyntaxRole.TYPE_OPERAND).size());
      assertSame(input.aggregate().orElseThrow().node(creation.nodeId()).orElseThrow(), creation);
      assertSame(input.aggregate().orElseThrow().node(method.root().nodeId()).orElseThrow(), method.root());
      assertTrue(method.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).isPresent());
    }
  }

  @Test
  void givenCastLocalAndExplicitInvocationTypes_whenAnalyzed_thenEachWrittenTypeHasItsActualSourceOwner()
      throws IOException {
    // Arrange
    var source = """
        import java.util.List;
        class Example {
          <T> T identity(T value) { return value; }
          List<String> convert(Object value) {
            List<String> values = (List<String>) value;
            return this.<List<String>>identity(values);
          }
        }
        """;

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(
        InputSnapshot.fromSources(sourceRoot, List.of(SourceFile.fromContent(Path.of("Example.java"), source))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, run.completion(), () -> run.diagnostics().toString());
    var nodes = input.aggregate().orElseThrow().nodes();
    var local = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).findFirst().orElseThrow();
    var cast = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow();
    var call = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).findFirst().orElseThrow();
    for (var type : List.of(local.requiredChild(SyntaxRole.DECLARED_TYPE), cast.requiredChild(SyntaxRole.TYPE_OPERAND),
        call.requiredChild(SyntaxRole.TYPE_ARGUMENT))) {
      var range = type.origin().orElseThrow();
      assertEquals("List<String>", source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())));
      assertEquals(SyntaxKind.TYPE_USE, type.syntaxKind());
      assertEquals("java.util.List<java.lang.String>", type.resolvedType().orElseThrow().displayName());
    }
    var method = input.methodSyntax().stream().filter(body -> body.methodKey().contains("#convert("))
        .findFirst().orElseThrow();
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(method));
    assertTrue(method.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.TYPE_USE));
    for (var node : method.root().descendantsAndSelf()) {
      assertSame(input.aggregate().orElseThrow().node(node.nodeId()).orElseThrow(), node);
    }
  }
}
