package org.aquilagraph.frontend;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeatureOccurrence;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.StableId;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxStructuralPath;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;

final class JavaMethodBodyTranslator {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final JavaDeclarationPropertyObserver propertyObserver;
  private final ParsedTreeInventory treeInventory;
  private final MethodBodySyntaxProfile syntaxProfile;

  JavaMethodBodyTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaDeclarationPropertyObserver propertyObserver) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    this.propertyObserver = Objects.requireNonNull(propertyObserver, "propertyObserver");
    syntaxProfile = new MethodBodySyntaxProfile(semantics);
  }

  TranslationResult<SyntaxNode> translate(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, BlockTree body,
      TreePath bodyPath, boolean constructor, boolean implicitDeclaration) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(bodyPath, "bodyPath");
    var translation = translateTree(unit, sourceFile, methodKey, body, bodyPath, JavaSyntaxStructuralPath.methodBody(bodyPath),
        SyntaxUse.VALUE, constructor, implicitDeclaration);
    return new TranslationResult<>(translation.node(), translation.diagnostics(), translation.featureOccurrences(),
        translation.featureObservations());
  }

  private SyntaxTreeTranslation translateTree(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, Tree tree, TreePath path,
      SyntaxStructuralPath structuralPath, SyntaxUse use, boolean constructor, boolean compilerGenerated) {
    var generated = compilerGenerated || (constructor && treeInventory.isCompilerInsertedSuperCall(path));
    var mapping = syntaxProfile.describe(tree, path, structuralPath, use);
    var children = translateChildren(unit, sourceFile, methodKey, mapping.children(), constructor, generated);
    var origin = sourceEvidence.origin(unit, sourceFile, tree);
    var sourceKind = JavaSourceKind.valueOf(tree.getKind().name());
    var childNodes = new ArrayList<SyntaxChild>(children.size());
    for (var index = 0; index < children.size(); index++) {
      var childMapping = mapping.children().get(index);
      childNodes.add(new SyntaxChild(childMapping.role(), childMapping.position(), children.get(index).node()));
    }
    var node = origin
        .map(source -> SyntaxNode.source(sourceFile.sourceFileKey(), structuralPath, sourceKind, mapping.kind(), semantics.type(path),
            mapping.value(), childNodes, mapping.semanticReference(), source))
        .orElseGet(() -> SyntaxNode.withoutDirectSource(sourceFile.sourceFileKey(), structuralPath, sourceKind, mapping.kind(),
            semantics.type(path), mapping.value(), childNodes, mapping.semanticReference()));
    var diagnostics = new ArrayList<>(diagnostics(methodKey, node, children));
    if (!generated) {
      switch (sourceEvidence.position(unit, sourceFile, tree)) {
        case JavaSourceEvidence.Position.Missing ignored -> diagnostics.add(FrontendDiagnosticFactory.sourcePositionError(sourceFile,
            node.structuralPath(), "frontend.missing-source-position", "A required source occurrence has no compiler position"));
        case JavaSourceEvidence.Position.Invalid ignored -> diagnostics.add(FrontendDiagnosticFactory.sourcePositionError(sourceFile,
            node.structuralPath(), "frontend.invalid-source-range", "A required source occurrence has an invalid compiler range"));
        case JavaSourceEvidence.Position.Available ignored -> {
        }
      }
    }
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    children.forEach(child -> occurrences.addAll(child.featureOccurrences()));
    children.forEach(child -> observations.addAll(child.featureObservations()));
    observeDeclarationProperties(unit, sourceFile, methodKey, tree, diagnostics, occurrences, observations);
    return new SyntaxTreeTranslation(node, diagnostics, occurrences, observations);
  }

  private void observeDeclarationProperties(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, Tree tree,
      List<AnalysisDiagnostic> diagnostics, List<LanguageFeatureOccurrence> occurrences, List<LanguageFeatureObservation> observations) {
    if (!(tree instanceof VariableTree variableTree)) {
      return;
    }
    var origin = sourceEvidence.origin(unit, sourceFile, variableTree);
    if (!variableTree.getModifiers().getFlags().isEmpty()) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, methodKey,
          JavaDeclarationPropertyCoverage.VARIABLE_MODIFIERS_DIAGNOSTIC,
          "Local declaration modifiers are outside the supported semantic graph profile", origin,
          JavaDeclarationPropertyCoverage.VARIABLE_MODIFIERS);
    }
    for (var annotation : variableTree.getModifiers().getAnnotations()) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, methodKey,
          JavaDeclarationPropertyCoverage.VARIABLE_MODIFIERS_DIAGNOSTIC,
          "Local declaration annotations are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, annotation), JavaDeclarationPropertyCoverage.VARIABLE_MODIFIERS);
    }
    if (variableTree.getNameExpression() != null) {
      propertyObserver.observe(diagnostics, occurrences, observations, sourceFile, methodKey,
          JavaDeclarationPropertyCoverage.VARIABLE_NAME_EXPRESSION_DIAGNOSTIC,
          "Local declaration name expressions are outside the supported semantic graph profile",
          sourceEvidence.origin(unit, sourceFile, variableTree.getNameExpression()),
          JavaDeclarationPropertyCoverage.VARIABLE_NAME_EXPRESSION);
    }
  }

  private List<SyntaxTreeTranslation> translateChildren(CompilationUnitTree unit, SourceFile sourceFile, String methodKey,
      List<SyntaxChildMapping> children, boolean constructor, boolean compilerGenerated) {
    var translations = new ArrayList<SyntaxTreeTranslation>(children.size());
    for (var child : children) {
      translations.add(translateTree(unit, sourceFile, methodKey, child.tree(), child.treePath(), child.structuralPath(), child.use(),
          constructor, compilerGenerated));
    }
    return List.copyOf(translations);
  }

  private List<AnalysisDiagnostic> diagnostics(String methodKey, SyntaxNode node, List<SyntaxTreeTranslation> children) {
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    if (node.syntaxKind() == SyntaxKind.UNSUPPORTED) {
      diagnostics.add(unsupportedDiagnostic(methodKey, node));
    } else {
      if (node.type() == SyntaxType.Erroneous.INSTANCE) {
        diagnostics.add(semanticDiagnostic(methodKey, node, "frontend.error-type", "javac reported an error type for retained syntax",
            AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE));
      } else if (node.type() == SyntaxType.Unresolved.INSTANCE) {
        diagnostics.add(semanticDiagnostic(methodKey, node, "frontend.unresolved-type",
            "A compiler type required for graph lowering is unavailable", AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      }
      if (node.semanticReference().isPresent() && node.semanticReference().orElseThrow().status() == ResolutionStatus.ERROR) {
        diagnostics.add(semanticDiagnostic(methodKey, node, "frontend.error-reference",
            "javac reported an erroneous declaration reference for retained syntax", AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE));
      } else if (node.semanticReference().isPresent() && node.semanticReference().orElseThrow().status() == ResolutionStatus.UNRESOLVED) {
        diagnostics.add(semanticDiagnostic(methodKey, node, "frontend.unresolved-reference",
            "A declaration identity required for graph lowering is unavailable", AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      }
    }
    children.forEach(child -> diagnostics.addAll(child.diagnostics()));
    return List.copyOf(diagnostics);
  }

  private static AnalysisDiagnostic unsupportedDiagnostic(String methodKey, SyntaxNode node) {
    var compilerKind = node.value(SyntaxValue.Unsupported.class).compilerKind();
    var message = "Unsupported Java construct in method body: " + compilerKind;
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", methodKey, node.nodeId(), "frontend.unsupported-syntax"),
        "frontend.unsupported-syntax", DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, message, node.origin().stream().toList(),
        AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE);
  }

  private static AnalysisDiagnostic semanticDiagnostic(String methodKey, SyntaxNode node, String code, String message,
      AnalysisOutcomeKind outcomeKind) {
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", methodKey, node.nodeId(), code), code, DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, message + ": " + node.syntaxKind(), node.origin().stream().toList(), outcomeKind);
  }

  private record SyntaxTreeTranslation(SyntaxNode node, List<AnalysisDiagnostic> diagnostics,
      List<LanguageFeatureOccurrence> featureOccurrences, List<LanguageFeatureObservation> featureObservations) {
    private SyntaxTreeTranslation {
      Objects.requireNonNull(node, "node");
      diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
      featureOccurrences = List.copyOf(Objects.requireNonNull(featureOccurrences, "featureOccurrences"));
      featureObservations = List.copyOf(Objects.requireNonNull(featureObservations, "featureObservations"));
    }
  }
}
