package org.aquilagraph.frontend;

import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;

/** Copies executable syntax independently of its declaration's registration policy. */
final class JavaExecutableSyntaxProjector {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaExecutableSyntaxProfile syntaxProfile;
  private final JavaLocalBindingProjector bindings;
  private final JavaPatternBindingProjector patterns;
  private final String diagnosticProducer;
  private final String syntaxContext;
  private final JavaExecutableProjectionContext context;

  JavaExecutableSyntaxProjector(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaExecutableProjectionContext context, String diagnosticProducer, String syntaxContext) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    this.diagnosticProducer = Objects.requireNonNull(diagnosticProducer, "diagnosticProducer");
    this.syntaxContext = Objects.requireNonNull(syntaxContext, "syntaxContext");
    this.context = Objects.requireNonNull(context, "context");
    syntaxProfile = new JavaExecutableSyntaxProfile(semantics, context);
    bindings = new JavaLocalBindingProjector(semantics, sourceEvidence, treeInventory);
    patterns = new JavaPatternBindingProjector(semantics, sourceEvidence);
  }

  Projection project(CompilationUnitTree unit, SourceFile sourceFile, String ownerKey, Tree tree, TreePath path,
      SyntaxStructuralPath structuralPath, boolean constructor, boolean compilerGenerated) {
    context.requireProjection();
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(ownerKey, "ownerKey");
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(structuralPath, "structuralPath");
    if (!JavaProjectionIdentity.sameInstance(path.getCompilationUnit(), unit)
        || !JavaProjectionIdentity.sameInstance(path.getLeaf(), tree)) {
      throw new IllegalArgumentException("Executable syntax projection requires its exact compiler tree and compilation-unit path");
    }
    var nodes = new ArrayList<NodeProjection>();
    var result = translateTree(unit, sourceFile, ownerKey, tree, path, structuralPath, constructor, compilerGenerated, nodes);
    return new Projection(
        new TranslationResult<>(result.node(), result.emissions(), result.featureOccurrences(), result.featureObservations()), nodes);
  }

  record Projection(TranslationResult<SyntaxNode> translation, List<NodeProjection> nodes) {
    Projection {
      Objects.requireNonNull(translation, "translation");
      nodes = List.copyOf(nodes);
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

  private SyntaxTreeTranslation translateTree(CompilationUnitTree unit, SourceFile sourceFile, String ownerKey, Tree tree, TreePath path,
      SyntaxStructuralPath structuralPath, boolean constructor, boolean compilerGenerated, List<NodeProjection> nodeProjections) {
    var collaborator = context.declarations();
    if (tree instanceof ClassTree declaration && collaborator.isPresent()) {
      var translated = collaborator.orElseThrow().project(declaration, path, structuralPath);
      nodeProjections
          .add(new NodeProjection(translated.value(), path, List.of(), treeInventory.sourceAssociation(translated.value(), path)));
      return new SyntaxTreeTranslation(translated.value(), translated.emissions(), translated.featureOccurrences(),
          translated.featureObservations());
    }
    var generated = compilerGenerated || (constructor && treeInventory.isCompilerInsertedSuperCall(path));
    var mapping = syntaxProfile.describe(tree, path, structuralPath);
    var children = translateChildren(unit, sourceFile, ownerKey, mapping.children(), constructor, generated, nodeProjections);
    var origin = sourceEvidence.origin(unit, sourceFile, tree);
    var value = mapping.value();
    if (value instanceof SyntaxValue.LocalDeclaration && tree instanceof VariableTree variable) {
      value = bindings.project(unit, sourceFile, variable, path);
    } else if (tree instanceof BindingPatternTree pattern) {
      value = patterns.project(unit, sourceFile, pattern, path);
    } else if (tree instanceof LambdaExpressionTree lambda) {
      var form = treeInventory.lambdaForm(lambda);
      value = new SyntaxValue.Lambda(form.bodyKind(), form.parenthesized(), form.parameters(),
          semantics.functionalReturnType(path).map(semantics::declarationType).orElse(SyntaxType.Unresolved.INSTANCE));
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
    var diagnostics = diagnostics(ownerKey, node, children);
    if (!generated) {
      switch (sourceEvidence.position(unit, sourceFile, tree)) {
        case JavaSourceEvidence.Position.Missing _ ->
          diagnostics.emit(diagnosticProducer, FrontendDiagnosticFactory.sourcePositionError(sourceFile, node.structuralPath(),
              "frontend.missing-source-position", "A required source occurrence has no compiler position"));
        case JavaSourceEvidence.Position.Invalid _ ->
          diagnostics.emit(diagnosticProducer, FrontendDiagnosticFactory.sourcePositionError(sourceFile, node.structuralPath(),
              "frontend.invalid-source-range", "A required source occurrence has an invalid compiler range"));
        case JavaSourceEvidence.Position.Available _ -> {
        }
      }
    }
    var occurrences = new ArrayList<LanguageFeatureOccurrence>();
    var observations = new ArrayList<LanguageFeatureObservation>();
    children.forEach(child -> occurrences.addAll(child.featureOccurrences()));
    children.forEach(child -> observations.addAll(child.featureObservations()));
    observeDeclarationProperties(unit, sourceFile, ownerKey, tree, diagnostics);
    return new SyntaxTreeTranslation(node, diagnostics.snapshot(), occurrences, observations);
  }

  private void observeDeclarationProperties(CompilationUnitTree unit, SourceFile sourceFile, String ownerKey, Tree tree,
      DiagnosticEmissionCollector diagnostics) {
    if (!(tree instanceof VariableTree variableTree)) {
      return;
    }
    if (variableTree.getNameExpression() != null) {
      diagnostics.emit(diagnosticProducer,
          FrontendDiagnosticFactory.otherError(ownerKey, "frontend.invalid-local-name-expression",
              "A local declaration cannot have a receiver name expression",
              sourceEvidence.origin(unit, sourceFile, variableTree.getNameExpression())));
    }
  }

  private List<SyntaxTreeTranslation> translateChildren(CompilationUnitTree unit, SourceFile sourceFile, String ownerKey,
      List<SyntaxChildMapping> children, boolean constructor, boolean compilerGenerated, List<NodeProjection> nodeProjections) {
    var translations = new ArrayList<SyntaxTreeTranslation>(children.size());
    try {
      for (var child : children) {
        translations.add(translateTree(unit, sourceFile, ownerKey, child.tree(), child.treePath(), child.structuralPath(), constructor,
            compilerGenerated, nodeProjections));
      }
    } catch (JavaSourceProjectionFailure failure) {
      throw new JavaSourceProjectionFailure(
          DiagnosticEmissions.combine(translations.stream().map(SyntaxTreeTranslation::emissions).toArray(DiagnosticEmissions[]::new)),
          failure);
    }
    return List.copyOf(translations);
  }

  private DiagnosticEmissionCollector diagnostics(String ownerKey, SyntaxNode node, List<SyntaxTreeTranslation> children) {
    DiagnosticEmissionCollector diagnostics = new DiagnosticEmissionCollector(diagnosticProducer, ownerKey + ':' + node.structuralPath());
    if (node.syntaxKind() == SyntaxKind.UNSUPPORTED) {
      diagnostics.emit(diagnosticProducer, unsupportedDiagnostic(ownerKey, node));
    } else {
      if (node.type() == SyntaxType.Erroneous.INSTANCE) {
        diagnostics.emit(diagnosticProducer, semanticDiagnostic(ownerKey, node, "frontend.error-type",
            "javac reported an error type for retained syntax", AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE));
      } else if (node.type() == SyntaxType.Unresolved.INSTANCE) {
        diagnostics.emit(diagnosticProducer, semanticDiagnostic(ownerKey, node, "frontend.unresolved-type",
            "A compiler type required for source syntax is unavailable", AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      } else if (node.type() instanceof SyntaxType.NoType noType) {
        diagnostics.emit(diagnosticProducer, semanticDiagnostic(ownerKey, node, "frontend.no-type-in-required-syntax",
            "A required syntax type resolved to compiler pseudo-type " + noType.kind(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      } else if (node.type() == SyntaxType.Unsupported.INSTANCE) {
        diagnostics.emit(diagnosticProducer, semanticDiagnostic(ownerKey, node, "frontend.unsupported-attributed-type",
            "A required syntax type has an implementation-reserved compiler kind", AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      } else if (node.type() instanceof SyntaxType.StructureUnavailable) {
        diagnostics.emit(diagnosticProducer, semanticDiagnostic(ownerKey, node, "frontend.unavailable-type-structure",
            "A required syntax type has no complete compiler-derived structure", AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      }
      if (node.semanticReference().isPresent() && node.semanticReference().orElseThrow().status() == ResolutionStatus.ERROR) {
        diagnostics.emit(diagnosticProducer, semanticDiagnostic(ownerKey, node, "frontend.error-reference",
            "javac reported an erroneous declaration reference for retained syntax", AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE));
      } else if (node.semanticReference().isPresent() && node.semanticReference().orElseThrow().status() == ResolutionStatus.UNRESOLVED) {
        diagnostics.emit(diagnosticProducer, semanticDiagnostic(ownerKey, node, "frontend.unresolved-reference",
            "A declaration identity required for source syntax is unavailable", AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
      }
    }
    children.forEach(child -> diagnostics.include(child.emissions()));
    return diagnostics;
  }

  private AnalysisDiagnostic unsupportedDiagnostic(String ownerKey, SyntaxNode node) {
    var compilerKind = node.value(SyntaxValue.Unsupported.class).compilerKind();
    var message = "Unsupported Java construct in " + syntaxContext + ": " + compilerKind;
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", ownerKey, node.nodeId(), "frontend.unsupported-syntax"),
        "frontend.unsupported-syntax", DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, message, node.origin().stream().toList(),
        AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE);
  }

  private static AnalysisDiagnostic semanticDiagnostic(String ownerKey, SyntaxNode node, String code, String message,
      AnalysisOutcomeKind outcomeKind) {
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", ownerKey, node.nodeId(), code), code, DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, message + ": " + node.syntaxKind(), node.origin().stream().toList(), outcomeKind);
  }

  private record SyntaxTreeTranslation(SyntaxNode node, DiagnosticEmissions emissions, List<LanguageFeatureOccurrence> featureOccurrences,
      List<LanguageFeatureObservation> featureObservations) {
    private SyntaxTreeTranslation {
      Objects.requireNonNull(node, "node");
      Objects.requireNonNull(emissions, "emissions");
      featureOccurrences = List.copyOf(Objects.requireNonNull(featureOccurrences, "featureOccurrences"));
      featureObservations = List.copyOf(Objects.requireNonNull(featureObservations, "featureObservations"));
    }
  }
}
