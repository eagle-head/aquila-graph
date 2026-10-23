package org.aquilagraph.frontend.internal;

import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.aquilagraph.source.api.CompilerExpressionAnchor;

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
    treeInventory.requireSource(unit, sourceFile);
    if (!structuralPath.equals(JavaSyntaxStructuralPath.executionRoot(path))) {
      throw new IllegalArgumentException("Executable projection requires its exact structural root path");
    }
    context.requireOwner(semantics, sourceEvidence, sourceFile, ownerKey, path, constructor, compilerGenerated);
    var nodes = new ArrayList<NodeProjection>();
    var result = translateTree(unit, sourceFile, ownerKey, tree, path, structuralPath, structuralPath, constructor, compilerGenerated,
        nodes);
    return new Projection(semantics, context, unit, sourceFile, ownerKey, new TranslationResult<>(result.node(), result.emissions()),
        nodes);
  }

  static final class Projection {
    private final SemanticResolver semantics;
    private final JavaExecutableProjectionContext selectedContext;
    private final CompilationUnitTree capturedUnit;
    private final SourceFile capturedSource;
    private final String ownerScope;
    private final TranslationResult<SyntaxNode> translatedSyntax;
    private final List<NodeProjection> projectedNodes;
    private final Map<SyntaxNode, NodeProjection> bindings;

    private Projection(SemanticResolver semantics, JavaExecutableProjectionContext context, CompilationUnitTree unit, SourceFile source,
        String scopeKey, TranslationResult<SyntaxNode> translation, List<NodeProjection> nodes) {
      this.semantics = semantics;
      selectedContext = context;
      capturedUnit = unit;
      capturedSource = source;
      ownerScope = scopeKey;
      translatedSyntax = translation;
      projectedNodes = List.copyOf(nodes);
      var correspondence = new IdentityHashMap<SyntaxNode, NodeProjection>();
      nodes.forEach(node -> correspondence.put(node.node(), node));
      bindings = Collections.unmodifiableMap(correspondence);
    }

    TranslationResult<SyntaxNode> translation() {
      return translatedSyntax;
    }

    List<NodeProjection> nodes() {
      return projectedNodes;
    }

    CompilationUnitTree unit() {
      return capturedUnit;
    }

    SourceFile source() {
      return capturedSource;
    }

    String scopeKey() {
      return ownerScope;
    }

    JavaExecutableProjectionContext context() {
      return selectedContext;
    }

    void requireSemantics(SemanticResolver supplied) {
      if (!JavaProjectionIdentity.sameInstance(semantics, supplied)) {
        throw new IllegalArgumentException("Executable evidence belongs to its producing compiler environment");
      }
    }

    Optional<AnalysisDiagnostic> erroneousType(SyntaxNode node) {
      return binding(node).erroneousType();
    }

    NodeProjection binding(SyntaxNode node) {
      var binding = bindings.get(node);
      if (binding == null) {
        throw new IllegalArgumentException("An executable node must belong to its producing projection");
      }
      return binding;
    }
  }

  record NodeProjection(SyntaxNode node, TreePath compilerAnchor, SyntaxStructuralPath compilerPath, List<SyntaxChildMapping> children,
      Optional<WrittenSourceAssociation> sourceAssociation, Optional<AnalysisDiagnostic> erroneousType,
      Optional<JavaWrittenExpressionProjector.Facts> derivation) {
    NodeProjection(SyntaxNode node, TreePath compilerAnchor, SyntaxStructuralPath compilerPath, List<SyntaxChildMapping> children,
        Optional<WrittenSourceAssociation> sourceAssociation, Optional<AnalysisDiagnostic> erroneousType) {
      this(node, compilerAnchor, compilerPath, children, sourceAssociation, erroneousType, Optional.empty());
    }
    NodeProjection {
      Objects.requireNonNull(node, "node");
      Objects.requireNonNull(compilerAnchor, "compilerAnchor");
      Objects.requireNonNull(compilerPath, "compilerPath");
      Objects.requireNonNull(derivation, "derivation");
      children = List.copyOf(children);
      Objects.requireNonNull(sourceAssociation, "sourceAssociation");
      Objects.requireNonNull(erroneousType, "erroneousType");
    }
  }

  private SyntaxTreeTranslation translateTree(CompilationUnitTree unit, SourceFile sourceFile, String ownerKey, Tree tree, TreePath path,
      SyntaxStructuralPath structuralPath, SyntaxStructuralPath compilerPath, boolean constructor, boolean compilerGenerated,
      List<NodeProjection> nodeProjections) {
    var collaborator = context.declarations();
    if (tree instanceof ClassTree declaration && collaborator.isPresent()) {
      var translated = collaborator.orElseThrow().project(declaration, path, structuralPath);
      nodeProjections.add(new NodeProjection(translated.value(), path, compilerPath, List.of(),
          treeInventory.sourceAssociation(translated.value(), path), Optional.empty()));
      return new SyntaxTreeTranslation(translated.value(), translated.emissions());
    }
    var writtenExpression = treeInventory.writtenExpression(path);
    if (writtenExpression.isPresent()) {
      var expression = writtenExpression.orElseThrow();
      var originalOperands = new IdentityHashMap<Tree, CompilerOperand>();
      compilerOperands(tree, path, compilerPath, originalOperands);
      var diagnostics = new DiagnosticEmissionCollector("JavaWrittenExpressionProjector", ownerKey + ':' + structuralPath);
      var projected = new JavaWrittenExpressionProjector(semantics, treeInventory).project(expression, sourceFile, structuralPath, false,
          new CompilerExpressionAnchor.Execution(compilerPath), (anchor, destination) -> {
            var original = Objects.requireNonNull(originalOperands.get(anchor.getLeaf()), "original compiler operand");
            var translated = translateTree(unit, sourceFile, ownerKey, anchor.getLeaf(), original.path(), destination, original.address(),
                constructor, compilerGenerated, nodeProjections);
            diagnostics.include(translated.emissions());
            return SourceProjectionResult.success(translated.node());
          },
          (node, facts, association) -> nodeProjections
              .add(new NodeProjection(node, path, compilerPath, List.of(), Optional.of(association), Optional.empty(), Optional.of(facts))),
          diagnostics);
      if (projected.failure().isPresent()) {
        throw new JavaSourceProjectionFailure(diagnostics.snapshot());
      }
      return new SyntaxTreeTranslation(projected.value().orElseThrow(), diagnostics.snapshot());
    }
    var generated = compilerGenerated || (constructor && treeInventory.isCompilerInsertedSuperCall(path));
    var mapping = syntaxProfile.describe(tree, path, compilerPath);
    var children = translateChildren(unit, sourceFile, ownerKey, mapping.children(), structuralPath, constructor, generated,
        nodeProjections);
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
    var erroneousType = node.syntaxKind() != SyntaxKind.UNSUPPORTED && node.type() == SyntaxType.Erroneous.INSTANCE
        ? Optional.of(semanticDiagnostic(ownerKey, node, "frontend.error-type", "javac reported an error type for retained syntax",
            AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE))
        : Optional.<AnalysisDiagnostic>empty();
    nodeProjections
        .add(new NodeProjection(node, path, compilerPath, mapping.children(), treeInventory.sourceAssociation(node, path), erroneousType));
    var diagnostics = diagnostics(ownerKey, node, children, erroneousType);
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
    observeDeclarationProperties(unit, sourceFile, ownerKey, tree, diagnostics);
    return new SyntaxTreeTranslation(node, diagnostics.snapshot());
  }

  private void compilerOperands(Tree tree, TreePath path, SyntaxStructuralPath address, Map<Tree, CompilerOperand> operands) {
    operands.put(tree, new CompilerOperand(path, address));
    if (tree.getKind() == Tree.Kind.PLUS || tree.getKind() == Tree.Kind.MINUS) {
      for (var child : syntaxProfile.describe(tree, path, address).children()) {
        compilerOperands(child.tree(), child.treePath(), child.structuralPath(), operands);
      }
    }
  }

  private record CompilerOperand(TreePath path, SyntaxStructuralPath address) {
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
      List<SyntaxChildMapping> children, SyntaxStructuralPath parentPath, boolean constructor, boolean compilerGenerated,
      List<NodeProjection> nodeProjections) {
    var translations = new ArrayList<SyntaxTreeTranslation>(children.size());
    try {
      for (var child : children) {
        translations.add(translateTree(unit, sourceFile, ownerKey, child.tree(), child.treePath(),
            parentPath.child(child.role(), child.position()), child.structuralPath(), constructor, compilerGenerated, nodeProjections));
      }
    } catch (JavaSourceProjectionFailure failure) {
      throw new JavaSourceProjectionFailure(
          DiagnosticEmissions.combine(translations.stream().map(SyntaxTreeTranslation::emissions).toArray(DiagnosticEmissions[]::new)),
          failure);
    }
    return List.copyOf(translations);
  }

  private DiagnosticEmissionCollector diagnostics(String ownerKey, SyntaxNode node, List<SyntaxTreeTranslation> children,
      Optional<AnalysisDiagnostic> erroneousType) {
    DiagnosticEmissionCollector diagnostics = new DiagnosticEmissionCollector(diagnosticProducer, ownerKey + ':' + node.structuralPath());
    if (node.syntaxKind() == SyntaxKind.UNSUPPORTED) {
      diagnostics.emit(diagnosticProducer, unsupportedDiagnostic(ownerKey, node));
    } else {
      if (node.type() == SyntaxType.Erroneous.INSTANCE) {
        diagnostics.emit(diagnosticProducer, erroneousType.orElseThrow());
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

  private record SyntaxTreeTranslation(SyntaxNode node, DiagnosticEmissions emissions) {
    private SyntaxTreeTranslation {
      Objects.requireNonNull(node, "node");
      Objects.requireNonNull(emissions, "emissions");
    }
  }
}
