package org.aquilagraph.frontend;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.StableId;
import org.aquilagraph.syntax.MethodSyntax;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;

final class JavaMethodBodyTranslator {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final MethodBodySyntaxProfile syntaxProfile;

  JavaMethodBodyTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    syntaxProfile = new MethodBodySyntaxProfile(semantics);
  }

  TranslationResult<MethodSyntax> translate(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, BlockTree body,
      TreePath bodyPath) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(bodyPath, "bodyPath");
    var translation = translateTree(unit, sourceFile, methodKey, body, bodyPath, SyntaxStructuralPath.methodBody(), SyntaxUse.VALUE);
    return new TranslationResult<>(new MethodSyntax(methodKey, translation.node()), translation.diagnostics());
  }

  private SyntaxTreeTranslation translateTree(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, Tree tree, TreePath path,
      SyntaxStructuralPath structuralPath, SyntaxUse use) {
    var mapping = syntaxProfile.describe(tree, path, structuralPath, use);
    var children = translateChildren(unit, sourceFile, methodKey, mapping.children());
    var origin = sourceEvidence.origin(unit, sourceFile, tree);
    var nodeId = StableId.sha256("syntax", methodKey, structuralPath.value(), tree.getKind().name(),
        origin.map(Object::toString).orElse("no-direct-source"));
    var childNodes = new ArrayList<SyntaxChild>(children.size());
    for (var index = 0; index < children.size(); index++) {
      var childMapping = mapping.children().get(index);
      childNodes.add(new SyntaxChild(childMapping.role(), childMapping.position(), children.get(index).node()));
    }
    var node = origin
        .map(source -> SyntaxNode.source(nodeId, mapping.kind(), semantics.type(path), mapping.value(), childNodes,
            mapping.semanticReference(), source))
        .orElseGet(() -> SyntaxNode.withoutDirectSource(nodeId, mapping.kind(), semantics.type(path), mapping.value(), childNodes,
            mapping.semanticReference()));
    var diagnostics = diagnostics(methodKey, node, children);
    return new SyntaxTreeTranslation(node, diagnostics);
  }

  private List<SyntaxTreeTranslation> translateChildren(CompilationUnitTree unit, SourceFile sourceFile, String methodKey,
      List<SyntaxChildMapping> children) {
    var translations = new ArrayList<SyntaxTreeTranslation>(children.size());
    for (var child : children) {
      translations.add(translateTree(unit, sourceFile, methodKey, child.tree(), child.treePath(), child.structuralPath(), child.use()));
    }
    return List.copyOf(translations);
  }

  private List<AnalysisDiagnostic> diagnostics(String methodKey, SyntaxNode node, List<SyntaxTreeTranslation> children) {
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    if (node.syntaxKind() == SyntaxKind.UNSUPPORTED) {
      diagnostics.add(unsupportedDiagnostic(methodKey, node));
    } else {
      if (node.type() == SyntaxType.Erroneous.INSTANCE) {
        diagnostics.add(semanticDiagnostic(methodKey, node, "frontend.error-type", "javac reported an error type for retained syntax"));
      } else if (node.type() == SyntaxType.Unresolved.INSTANCE) {
        diagnostics.add(
            semanticDiagnostic(methodKey, node, "frontend.unresolved-type", "A compiler type required for graph lowering is unavailable"));
      }
      if (node.semanticReference().isPresent() && node.semanticReference().orElseThrow().status() == ResolutionStatus.ERROR) {
        diagnostics.add(semanticDiagnostic(methodKey, node, "frontend.error-reference",
            "javac reported an erroneous declaration reference for retained syntax"));
      } else if (node.semanticReference().isPresent() && node.semanticReference().orElseThrow().status() == ResolutionStatus.UNRESOLVED) {
        diagnostics.add(semanticDiagnostic(methodKey, node, "frontend.unresolved-reference",
            "A declaration identity required for graph lowering is unavailable"));
      }
    }
    children.forEach(child -> diagnostics.addAll(child.diagnostics()));
    return List.copyOf(diagnostics);
  }

  private static AnalysisDiagnostic unsupportedDiagnostic(String methodKey, SyntaxNode node) {
    var compilerKind = node.value(SyntaxValue.Unsupported.class).compilerKind();
    var message = "Unsupported Java construct in method body: " + compilerKind;
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", methodKey, node.nodeId(), "frontend.unsupported-syntax"),
        "frontend.unsupported-syntax", DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, message, node.origin().stream().toList());
  }

  private static AnalysisDiagnostic semanticDiagnostic(String methodKey, SyntaxNode node, String code, String message) {
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", methodKey, node.nodeId(), code), code, DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, message + ": " + node.syntaxKind(), node.origin().stream().toList());
  }

  private record SyntaxTreeTranslation(SyntaxNode node, List<AnalysisDiagnostic> diagnostics) {
    private SyntaxTreeTranslation {
      Objects.requireNonNull(node, "node");
      diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
  }
}
