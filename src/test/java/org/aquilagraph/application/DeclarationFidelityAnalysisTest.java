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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeclarationFidelityAnalysisTest {
  @TempDir Path directory;

  @Test
  void givenEmptyMembersAndMethodsWithDifferentCfgAdmission_whenTheApplicationUsesTheDeclarationProfile_thenSourceCompletenessAndOrderSurvive()
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"),
        "class Example { ; int simple() { return 5; } ; int complex() { int n = 0; while(n < 2) { n++; } return n; } ; }")));
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var request = new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_DECLARATIONS, Optional.empty(), PublicationIntent.DO_NOT_SAVE);
    // Act
    var source = runner.analyze(request).representation().ast();
    var ast = source.result().orElseThrow();
    var methods = ast.program().methods().stream().filter(method -> method.sourceDeclared()).toList();
    var selected = runner.deriveExisting(source,
        new CfgRequest(ast.identity(), methods.stream().map(method -> method.methodKey()).toList(), CfgProfile.JAVA_BOUNDED));
    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    assertEquals(AstProfile.JAVA_SOURCE_DECLARATIONS, ast.profile());
    assertEquals(ast, selected.representation().ast().result().orElseThrow());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED),
        selected.representation().cfg().orElseThrow().methods().stream().map(method -> method.state()).toList());
    assertEquals(3, ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.EMPTY).count());
    assertTrue(ast.aggregate().executionScopes().stream().allMatch(scope -> ast.program().methods().stream()
        .anyMatch(method -> method.methodKey().equals(scope.key()))));
  }
}
