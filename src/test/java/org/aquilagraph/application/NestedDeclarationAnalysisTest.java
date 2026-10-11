package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NestedDeclarationAnalysisTest {
  @TempDir Path directory;

  @Test
  void givenLocalTypeWithSimpleAndLoopBodies_whenApplicationSelectsNestedMethods_thenOuterRefusalAndIndependentNestedResultsRemainDistinct() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), """
        class Example {
          int outer() {
            class Local {
              int simple() { return 5; }
              int complex() {
                int n = 0;
                inside: while (n < 2) { n++; if (n == 1) continue inside; break inside; }
                return switch(n) { case 2 -> { yield 6; } default -> 0; };
              }
            }
            return 1;
          }
        }
        """)));
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var request = new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_NESTED, Optional.empty(), PublicationIntent.DO_NOT_SAVE);

    // Act
    var source = runner.analyze(request).representation().ast();
    var ast = source.result().orElseThrow();
    var simple = ast.program().methods().stream().filter(method -> method.name().equals("simple")).findFirst().orElseThrow().methodKey();
    var complex = ast.program().methods().stream().filter(method -> method.name().equals("complex")).findFirst().orElseThrow().methodKey();
    var outer = ast.program().methods().stream().filter(method -> method.name().equals("outer")).findFirst().orElseThrow().methodKey();
    var selected = runner.deriveExisting(source, new CfgRequest(ast.identity(), List.of(simple, complex, outer), CfgProfile.JAVA_BOUNDED));
    var methods = selected.representation().cfg().orElseThrow().methods();
    var unknown = runner.deriveExisting(source, new CfgRequest(ast.identity(), List.of(simple + ":unknown"), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, source.completion(), source.diagnostics().toString());
    assertEquals(ast, selected.representation().ast().result().orElseThrow());
    assertEquals(List.of(simple, complex, outer), methods.stream().map(method -> method.methodKey()).toList());
    assertEquals(MethodCfgState.AVAILABLE, methods.getFirst().state());
    assertTrue(methods.getFirst().graph().isPresent());
    assertEquals(MethodCfgState.UNSUPPORTED, methods.get(1).state());
    assertEquals(MethodCfgState.UNSUPPORTED, methods.getLast().state());
    assertEquals(1, methods.getLast().blockers().size());
    assertEquals(SyntaxKind.TYPE_DECLARATION, ast.node(methods.getLast().blockers().getFirst().occurrence().orElseThrow()).orElseThrow().syntaxKind());
    assertFalse(ast.body(complex).orElseThrow().root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.BREAK || node.syntaxKind() == SyntaxKind.CONTINUE).toList().isEmpty());
    assertEquals(CfgState.REJECTED, unknown.representation().cfg().orElseThrow().state());
    assertTrue(unknown.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("control-flow.unknown-method")));
    assertEquals(ast, unknown.representation().ast().result().orElseThrow());
  }

  @Test
  void givenAnonymousMemberSelectionWithConstructorArgument_whenAnalyzedAndNestedBodyDerived_thenCompleteSourceAndExactTargetRemain() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), """
        class Example {
          static class Base { Base(int n) {} }
          int subject() { return new Base(1) { int extra() { return 2; } }.extra(); }
        }
        """)));
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var request = new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_NESTED, Optional.empty(), PublicationIntent.DO_NOT_SAVE);

    // Act
    var source = runner.analyze(request).representation().ast();
    var ast = source.result().orElseThrow();
    var extra = ast.program().methods().stream().filter(method -> method.name().equals("extra")).findFirst().orElseThrow();
    var subject = ast.program().methods().stream().filter(method -> method.name().equals("subject")).findFirst().orElseThrow();
    var body = ast.body(subject.methodKey()).orElseThrow();
    var creation = body.root().descendantsAndSelf().stream().filter(node -> !node.children(SyntaxRole.CLASS_BODY).isEmpty()).findFirst().orElseThrow();
    var selected = runner.deriveExisting(source, new CfgRequest(ast.identity(), List.of(extra.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, source.completion(), source.diagnostics().toString());
    assertEquals("int", body.useEvidence(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
    assertEquals(ast, selected.representation().ast().result().orElseThrow());
    assertEquals(MethodCfgState.AVAILABLE, selected.representation().cfg().orElseThrow().methods().getFirst().state());
    assertTrue(selected.representation().cfg().orElseThrow().methods().getFirst().graph().isPresent());
  }
}
