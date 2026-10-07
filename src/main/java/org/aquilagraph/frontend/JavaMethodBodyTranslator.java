package org.aquilagraph.frontend;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;

final class JavaMethodBodyTranslator {
  private static final String DIAGNOSTIC_PRODUCER = "JavaMethodBodyTranslator";
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final MethodBodySyntaxProfile syntaxProfile;
  private final JavaLocalBindingProjector bindings;

  JavaMethodBodyTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    syntaxProfile = new MethodBodySyntaxProfile(semantics);
    bindings = new JavaLocalBindingProjector(semantics, sourceEvidence, treeInventory);
  }

  TranslationResult<TranslatedMethodBody> translate(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, BlockTree body,
      TreePath bodyPath, boolean constructor, boolean implicitDeclaration) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(bodyPath, "bodyPath");
    var methodPath = bodyPath.getParentPath();
    if (!JavaProjectionIdentity.sameInstance(bodyPath.getCompilationUnit(), unit)
        || !JavaProjectionIdentity.sameInstance(bodyPath.getLeaf(), body) || methodPath == null
        || !(methodPath.getLeaf() instanceof MethodTree declaration) || !JavaProjectionIdentity.sameInstance(declaration.getBody(), body)
        || !(semantics.element(methodPath) instanceof ExecutableElement executable)
        || !semantics.declarationKey(executable).equals(Optional.of(methodKey))) {
      throw new IllegalArgumentException("A method body projection requires its attributed executable and exact body path");
    }
    var nodeProjections = new ArrayList<NodeProjection>();
    var translation = translateTree(unit, sourceFile, methodKey, body, bodyPath, JavaSyntaxStructuralPath.methodBody(bodyPath), constructor,
        implicitDeclaration, nodeProjections);
    var decisions = new JavaBodyDecisionProducer(semantics).produce(methodKey, translation.node(), nodeProjections,
        translation.diagnostics());
    var bodyProjection = new BodyProjection(declaration, bodyPath, translation.node(), nodeProjections, decisions);
    return new TranslationResult<>(new TranslatedMethodBody(methodKey, translation.node(), bodyProjection), translation.diagnostics(),
        translation.featureOccurrences(), translation.featureObservations());
  }

  static final class BodyProjection {
    private final MethodTree capturedDeclaration;
    private final TreePath capturedBodyPath;
    private final SyntaxNode createdRoot;
    private final List<NodeProjection> retainedNodeProjections;
    private final JavaBodyDecisions selectedDecisions;

    private BodyProjection(MethodTree declaration, TreePath bodyPath, SyntaxNode producedRoot, List<NodeProjection> nodeProjections,
        JavaBodyDecisions decisions) {
      this.capturedDeclaration = declaration;
      this.capturedBodyPath = bodyPath;
      this.createdRoot = producedRoot;
      this.retainedNodeProjections = List.copyOf(nodeProjections);
      this.selectedDecisions = decisions;
    }

    MethodTree declaration() {
      return capturedDeclaration;
    }

    TreePath bodyPath() {
      return capturedBodyPath;
    }

    SyntaxNode producedRoot() {
      return createdRoot;
    }

    List<NodeProjection> nodeProjections() {
      return retainedNodeProjections;
    }

    JavaBodyDecisions decisions() {
      return selectedDecisions;
    }
  }

  record NodeProjection(SyntaxNode node, TreePath path, List<SyntaxChildMapping> children,
      Optional<WrittenSourceAssociation> sourceAssociation) {
    NodeProjection {
      Objects.requireNonNull(node, "node");
      Objects.requireNonNull(path, "path");
      children = List.copyOf(children);
      Objects.requireNonNull(sourceAssociation, "sourceAssociation");
    }
  }

  private SyntaxTreeTranslation translateTree(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, Tree tree, TreePath path,
      SyntaxStructuralPath structuralPath, boolean constructor, boolean compilerGenerated, List<NodeProjection> nodeProjections) {
    var generated = compilerGenerated || (constructor && treeInventory.isCompilerInsertedSuperCall(path));
    var mapping = syntaxProfile.describe(tree, path, structuralPath);
    var children = translateChildren(unit, sourceFile, methodKey, mapping.children(), constructor, generated, nodeProjections);
    var origin = sourceEvidence.origin(unit, sourceFile, tree);
    var value = mapping.value();
    if (value instanceof SyntaxValue.LocalDeclaration && tree instanceof VariableTree variable) {
      value = bindings.project(unit, sourceFile, variable, path);
    }
    var sourceKind = JavaSourceKind.valueOf(tree.getKind().name());
    var childNodes = new ArrayList<SyntaxChild>(children.size());
    for (var index = 0; index < children.size(); index++) {
      var childMapping = mapping.children().get(index);
      childNodes.add(new SyntaxChild(childMapping.role(), childMapping.position(), children.get(index).node()));
    }
    var node = origin.isPresent()
        ? SyntaxNode.source(sourceFile.sourceFileKey(), structuralPath, sourceKind, mapping.kind(), semantics.type(path), value, childNodes,
            mapping.semanticReference(), origin.orElseThrow())
        : SyntaxNode.withoutDirectSource(sourceFile.sourceFileKey(), structuralPath, sourceKind, mapping.kind(), semantics.type(path),
            value, childNodes, mapping.semanticReference());
    nodeProjections.add(new NodeProjection(node, path, mapping.children(), treeInventory.sourceAssociation(node, path)));
    var diagnostics = mutableDiagnostics(diagnostics(methodKey, node, children), methodKey + ':' + structuralPath);
    if (!generated) {
      switch (sourceEvidence.position(unit, sourceFile, tree)) {
        case JavaSourceEvidence.Position.Missing _ ->
          TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.sourcePositionError(sourceFile,
              node.structuralPath(), "frontend.missing-source-position", "A required source occurrence has no compiler position"));
        case JavaSourceEvidence.Position.Invalid _ ->
          TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.sourcePositionError(sourceFile,
              node.structuralPath(), "frontend.invalid-source-range", "A required source occurrence has an invalid compiler range"));
        case JavaSourceEvidence.Position.Available _ -> {
        }
      }
    }
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    children.forEach(child -> occurrences.addAll(child.featureOccurrences()));
    children.forEach(child -> observations.addAll(child.featureObservations()));
    observeDeclarationProperties(unit, sourceFile, methodKey, tree, diagnostics);
    return new SyntaxTreeTranslation(node, diagnostics, occurrences, observations);
  }

  private void observeDeclarationProperties(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, Tree tree,
      List<AnalysisDiagnostic> diagnostics) {
    if (!(tree instanceof VariableTree variableTree)) {
      return;
    }
    if (variableTree.getNameExpression() != null) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(methodKey, "frontend.invalid-local-name-expression",
              "A local declaration cannot have a receiver name expression",
              sourceEvidence.origin(unit, sourceFile, variableTree.getNameExpression())));
    }
  }

  private List<SyntaxTreeTranslation> translateChildren(CompilationUnitTree unit, SourceFile sourceFile, String methodKey,
      List<SyntaxChildMapping> children, boolean constructor, boolean compilerGenerated, List<NodeProjection> nodeProjections) {
    var translations = new ArrayList<SyntaxTreeTranslation>(children.size());
    for (var child : children) {
      translations.add(translateTree(unit, sourceFile, methodKey, child.tree(), child.treePath(), child.structuralPath(), constructor,
          compilerGenerated, nodeProjections));
    }
    return List.copyOf(translations);
  }

  private static List<AnalysisDiagnostic> mutableDiagnostics(List<AnalysisDiagnostic> retained, String producingOwner) {
    var result = new TranslationDiagnosticEvidence(DIAGNOSTIC_PRODUCER, producingOwner);
    result.addAll(retained);
    return result;
  }

  private List<AnalysisDiagnostic> diagnostics(String methodKey, SyntaxNode node, List<SyntaxTreeTranslation> children) {
    List<AnalysisDiagnostic> diagnostics = new TranslationDiagnosticEvidence(DIAGNOSTIC_PRODUCER, methodKey + ':' + node.structuralPath());
    if (node.syntaxKind() == SyntaxKind.UNSUPPORTED) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, unsupportedDiagnostic(methodKey, node));
    } else {
      if (node.type() == SyntaxType.Erroneous.INSTANCE) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, semanticDiagnostic(methodKey, node, "frontend.error-type",
            "javac reported an error type for retained syntax", AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE));
      } else if (node.type() == SyntaxType.Unresolved.INSTANCE) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, semanticDiagnostic(methodKey, node, "frontend.unresolved-type",
            "A compiler type required for graph lowering is unavailable", AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      } else if (node.type() instanceof SyntaxType.NoType noType) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            semanticDiagnostic(methodKey, node, "frontend.no-type-in-required-syntax",
                "A required syntax type resolved to compiler pseudo-type " + noType.kind(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      } else if (node.type() == SyntaxType.Unsupported.INSTANCE) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            semanticDiagnostic(methodKey, node, "frontend.unsupported-attributed-type",
                "A required syntax type has an implementation-reserved compiler kind", AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      } else if (node.type() instanceof SyntaxType.StructureUnavailable) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            semanticDiagnostic(methodKey, node, "frontend.unavailable-type-structure",
                "A required syntax type has no complete compiler-derived structure", AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      }
      if (node.semanticReference().isPresent() && node.semanticReference().orElseThrow().status() == ResolutionStatus.ERROR) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, semanticDiagnostic(methodKey, node, "frontend.error-reference",
            "javac reported an erroneous declaration reference for retained syntax", AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE));
      } else if (node.semanticReference().isPresent() && node.semanticReference().orElseThrow().status() == ResolutionStatus.UNRESOLVED) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            semanticDiagnostic(methodKey, node, "frontend.unresolved-reference",
                "A declaration identity required for graph lowering is unavailable", AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      }
    }
    children.forEach(child -> diagnostics.addAll(child.diagnostics()));
    return TranslationDiagnosticEvidence.copyOf(diagnostics);
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
      diagnostics = TranslationDiagnosticEvidence.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
      featureOccurrences = List.copyOf(Objects.requireNonNull(featureOccurrences, "featureOccurrences"));
      featureObservations = List.copyOf(Objects.requireNonNull(featureObservations, "featureObservations"));
    }
  }
}
