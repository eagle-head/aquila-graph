package org.aquilagraph.syntax;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;

/** Builds a sealed aggregate for focused core-facing method-view tests. */
public final class MethodSyntaxFixture {
  private MethodSyntaxFixture() {
  }

  /** Returns a method view whose execution tree belongs to a minimal sealed aggregate. */
  public static MethodSyntax view(String methodKey, SyntaxNode body) {
    var sourceFile = new SourceFile("source", Path.of("Source.java"), "fixture-content", "x");
    var snapshot = new InputSnapshot(Path.of("."), "fixture-digest", "fixture-snapshot", List.of(sourceFile));
    var sourceRoot = SyntaxNode.source("source", SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT,
        SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY), List.of(), Optional.empty(),
        new SourceSpan("source", 0, 1));
    var contribution = new SyntaxSourceContribution("source", sourceRoot, List.of(sourceRoot), List.of(), List.of());
    return JavaAstAggregate.builder(snapshot).add(contribution)
        .addMethodBody(methodKey, body, MethodBodyOrigin.EXECUTION_PROJECTION).seal().methodSyntax(methodKey).orElseThrow();
  }
}
