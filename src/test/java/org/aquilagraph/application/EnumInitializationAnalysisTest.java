package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
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
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnumInitializationAnalysisTest {
  @TempDir Path directory;

  @Test
  void givenConstantArgumentsAndSeparateBodyMethods_whenApplicationDerivesSelectedMethods_thenSourceRemainsCompleteAcrossCfgRefusal() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "enum Example { FIRST(Math.abs(-2)) { int simple() { return 5; } int complex() { int n = 0; while(n < 2) { n++; } return n; } }; Example(int n) {} }")));
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var request = new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION, Optional.empty(), PublicationIntent.DO_NOT_SAVE);

    // Act
    var source = runner.analyze(request).representation().ast();
    var ast = source.result().orElseThrow();
    var simple = ast.program().methods().stream().filter(method -> method.name().equals("simple")).findFirst().orElseThrow();
    var complex = ast.program().methods().stream().filter(method -> method.name().equals("complex")).findFirst().orElseThrow();
    var selected = runner.deriveExisting(source, new CfgRequest(ast.identity(), List.of(simple.methodKey(), complex.methodKey()), CfgProfile.JAVA_BOUNDED));
    var methods = selected.representation().cfg().orElseThrow().methods();

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    assertEquals(ast, selected.representation().ast().result().orElseThrow());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED), methods.stream().map(method -> method.state()).toList());
    assertTrue(methods.getFirst().graph().isPresent());
    var constant = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION).findFirst().orElseThrow();
    var execution = ast.aggregate().executionSyntax(constant.value(SyntaxValue.VariableDeclaration.class).declarationKey()).orElseThrow();
    assertEquals("int", execution.useEvidence(constant.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().erasedName());
    assertTrue(execution.evidence().uses().stream().noneMatch(use -> use.scopeKey().equals(simple.methodKey()) || use.scopeKey().equals(complex.methodKey())));
  }
}
