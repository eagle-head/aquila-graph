package org.aquilagraph.source.api;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;

/** Builds a sealed aggregate for focused core-facing method-view tests. */
public final class MethodSyntaxFixture {
  private MethodSyntaxFixture() {
  }

  /** Returns a method view whose execution tree belongs to a minimal sealed aggregate. */
  public static MethodSyntax view(String methodKey, SyntaxNode body) {
    return view(methodKey, body, ignored -> List.of());
  }

  /** Returns a method view with explicitly supplied selected uses bound to its copied execution tree. */
  public static MethodSyntax view(String methodKey, SyntaxNode body, Function<SyntaxNode, List<ConversionEvidence>> selectedUses) {
    body.descendantsAndSelf();
    var executionBody = withChildPaths(body, body.structuralPath());
    var sourceFile = new SourceFile("source", capturedPath(Path.of("Source.java")), "fixture-content", "x");
    var snapshot = new InputSnapshot(capturedAbsolute(Path.of(".")), "fixture-digest", "fixture-snapshot", List.of(sourceFile));
    var sourceRoot = SyntaxNode.source("source", SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT,
        SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE), List.of(), Optional.empty(),
        new SourceSpan("source", 0, 1));
    var contribution = new SyntaxSourceContribution("source", sourceRoot, List.of(sourceRoot), List.of(), List.of());
    return AggregateFixture.builder(snapshot).add(contribution)
        .addMethodBody(methodKey, executionBody, MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, selectedUses.apply(executionBody), List.of(),
            List.of(), List.of()).seal().methodSyntax(methodKey).orElseThrow();
  }

  private static SyntaxNode withChildPaths(SyntaxNode node, SyntaxStructuralPath path) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(),
        withChildPaths(child.node(), path.child(child.role(), child.position())))).toList();
    return node.origin().map(origin -> SyntaxNode.source(node.sourceFileKey(), path, node.sourceKind(), node.syntaxKind(), node.type(),
        node.value(), children, node.semanticReference(), origin)).orElseGet(() -> SyntaxNode.withoutDirectSource(node.sourceFileKey(),
            path, node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children, node.semanticReference()));
  }
}
