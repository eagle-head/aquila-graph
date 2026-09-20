package org.aquilagraph.frontend;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.MethodBody;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.SyntaxNode;

final class JavaMethodBodyTranslator {
  private final JavaSemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final MethodBodySyntaxProfile syntaxProfile;

  JavaMethodBodyTranslator(JavaSemanticResolver semantics, JavaSourceEvidence sourceEvidence) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    syntaxProfile = new MethodBodySyntaxProfile(semantics);
  }

  TranslationResult<MethodBody> translate(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, BlockTree body,
      TreePath bodyPath) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(bodyPath, "bodyPath");
    var translation = translateTree(unit, sourceFile, methodKey, body, bodyPath, SyntaxStructuralPath.methodBody());
    return new TranslationResult<>(new MethodBody(translation.node(), translation.supported()), translation.diagnostics());
  }

  private SyntaxTreeTranslation translateTree(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, Tree tree, TreePath path,
      SyntaxStructuralPath structuralPath) {
    var mapping = syntaxProfile.describe(tree, path, structuralPath);
    var children = translateChildren(unit, sourceFile, methodKey, path, mapping.children());
    var origins = sourceEvidence.origins(unit, sourceFile, tree);
    var nodeId = StableId.sha256("syntax", methodKey, structuralPath.value(), tree.getKind().name(),
        origins.stream().map(Object::toString).collect(Collectors.joining("|")));
    var childNodes = children.stream().map(SyntaxTreeTranslation::node).toList();
    var node = new SyntaxNode(nodeId, mapping.kind(), mapping.detail(), childNodes, semantics.references(path), origins, origins.isEmpty());
    var diagnostics = diagnostics(unit, sourceFile, methodKey, tree, mapping.acceptance(), children);
    var supported = mapping.acceptance() == SyntaxAcceptance.ACCEPTED && children.stream().allMatch(SyntaxTreeTranslation::supported);
    return new SyntaxTreeTranslation(node, supported, diagnostics);
  }

  private List<SyntaxTreeTranslation> translateChildren(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, TreePath parent,
      List<SyntaxChildMapping> children) {
    var translations = new ArrayList<SyntaxTreeTranslation>(children.size());
    for (var child : children) {
      translations
          .add(translateTree(unit, sourceFile, methodKey, child.tree(), new TreePath(parent, child.tree()), child.structuralPath()));
    }
    return List.copyOf(translations);
  }

  private List<AnalysisDiagnostic> diagnostics(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, Tree tree,
      SyntaxAcceptance acceptance, List<SyntaxTreeTranslation> children) {
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    if (acceptance == SyntaxAcceptance.UNSUPPORTED) {
      diagnostics.add(unsupportedDiagnostic(unit, sourceFile, methodKey, tree));
    }
    children.forEach(child -> diagnostics.addAll(child.diagnostics()));
    return List.copyOf(diagnostics);
  }

  private AnalysisDiagnostic unsupportedDiagnostic(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, Tree tree) {
    var origins = sourceEvidence.origins(unit, sourceFile, tree);
    var message = "Unsupported Java construct in method body: " + tree.getKind();
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", methodKey, tree.getKind().name(), message), "frontend.unsupported-syntax",
        DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, message, origins);
  }

  private record SyntaxTreeTranslation(SyntaxNode node, boolean supported, List<AnalysisDiagnostic> diagnostics) {
    private SyntaxTreeTranslation {
      Objects.requireNonNull(node, "node");
      diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
  }
}
