package org.aquilagraph.frontend;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;

/** Projects initialization children and registers facts against their assembled declaration. */
final class JavaAstContributionTranslator {
  private static final String DIAGNOSTIC_PRODUCER = "JavaAstContributionTranslator";
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaTranslationPolicy policy;
  private final JavaWrittenSourceAssociations sourceAssociations;
  private final JavaBodySourceCorrelation bodyCorrelation;

  JavaAstContributionTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaTranslationPolicy policy, JavaWrittenSourceAssociations sourceAssociations, JavaBodySourceCorrelation bodyCorrelation) {
    this.semantics = semantics;
    this.sourceEvidence = sourceEvidence;
    this.treeInventory = treeInventory;
    this.policy = policy;
    this.sourceAssociations = sourceAssociations;
    this.bodyCorrelation = bodyCorrelation;
  }

  Optional<Initialization> fieldChildren(CompilationUnitTree unit, SourceFile sourceFile, TreePath fieldPath, VariableTree declaration,
      SyntaxValue.VariableDeclaration value, SyntaxStructuralPath path, List<AnalysisDiagnostic> diagnostics,
      JavaExecutableSyntaxProjector.DeclarationProjection nestedDeclarations) {
    var initializer = declaration.getInitializer();
    if (policy.supportsEnumInitialization() && value.kind() == SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT
        && initializer instanceof NewClassTree creation) {
      return enumChildren(unit, sourceFile, fieldPath, creation, value, path, diagnostics, nestedDeclarations);
    }
    if (!policy.supportsMemberInitialization() || value.kind() == SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT) {
      return Optional.of(new Initialization(List.of(), Optional.empty()));
    }
    if (initializer == null) {
      return Optional.of(new Initialization(List.of(), Optional.of(node -> retainField(value, node, fieldPath, Optional.empty(),
          new JavaBodyDecisions(List.of(), List.of(), List.of(), List.of())))));
    }
    var projection = executableProjection(unit, sourceFile, value.declarationKey(), initializer, new TreePath(fieldPath, initializer),
        JavaSyntaxStructuralPath.executionRoot(fieldPath).child(SyntaxRole.INITIALIZER, 0), nestedDeclarations);
    var decisions = new JavaBodyDecisionProducer(semantics).produce(value.declarationKey(), projection.translation().value(),
        projection.nodes(), diagnostics);
    var copied = bodyCorrelation.projectExecutable(projection, path.child(SyntaxRole.INITIALIZER, 0), unit, sourceFile, diagnostics);
    return copied.map(node -> new Initialization(List.of(new SyntaxChild(SyntaxRole.INITIALIZER, 0, node)),
        Optional.of(parent -> retainField(value, parent, fieldPath, Optional.of(projection), decisions))));
  }

  private void retainField(SyntaxValue.VariableDeclaration value, SyntaxNode node, TreePath fieldPath,
      Optional<JavaExecutableSyntaxProjector.Projection> projection, JavaBodyDecisions initializerDecisions) {
    var decisions = projection.map(valueProjection -> bodyCorrelation.rebind(initializerDecisions, valueProjection.translation().value(),
        node.requiredChild(SyntaxRole.INITIALIZER))).orElse(initializerDecisions);
    var uses = new ArrayList<>(decisions.uses());
    new JavaBodyDecisionProducer(semantics).fieldInitializer(value.declarationKey(), node, fieldPath).ifPresent(uses::add);
    var attributions = projection
        .map(valueProjection -> new ArrayList<>(bodyCorrelation.executionAttributions(value.declarationKey(), valueProjection)))
        .orElseGet(ArrayList::new);
    attributions.add(new AttributedBodyOccurrence(value.declarationKey(), node.nodeId(), node.sourceFileKey(), node.sourceKind(),
        JavaSyntaxStructuralPath.executionRoot(fieldPath)));
    bodyCorrelation.retainScope(new CopiedAstValues.Scope(new ExecutionScope.Field(value.declarationKey(), value.ownerKey()), node, uses,
        decisions.invocations(), decisions.staticAccesses(), decisions.concatenations(), attributions));
  }

  private Optional<Initialization> enumChildren(CompilationUnitTree unit, SourceFile sourceFile, TreePath fieldPath, NewClassTree creation,
      SyntaxValue.VariableDeclaration value, SyntaxStructuralPath path, List<AnalysisDiagnostic> diagnostics,
      JavaExecutableSyntaxProjector.DeclarationProjection nestedDeclarations) {
    var enumUses = new ArrayList<ChildUseEvidence>();
    var enumInvocations = new ArrayList<InvocationEvidence>();
    var enumStaticAccesses = new ArrayList<StaticMemberEvidence>();
    var enumConcatenations = new ArrayList<StringExpressionEvidence>();
    var enumAttributions = new ArrayList<AttributedBodyOccurrence>();
    var children = new ArrayList<SyntaxChild>();
    var creationPath = new TreePath(fieldPath, creation);
    var decisionProducer = new JavaBodyDecisionProducer(semantics);
    var constructor = semantics.enumConstructorPath(creationPath)
        .flatMap(selected -> semantics.reference(selected, ReferenceKind.CONSTRUCTOR));
    for (var index = 0; index < creation.getArguments().size(); index++) {
      var argument = creation.getArguments().get(index);
      var argumentPath = Objects.requireNonNull(TreePath.getPath(creationPath, argument), "enum argument path");
      var projection = executableProjection(unit, sourceFile, value.declarationKey(), argument, argumentPath,
          JavaSyntaxStructuralPath.executionRoot(argumentPath), nestedDeclarations);
      var decisions = decisionProducer.produce(value.declarationKey(), projection.translation().value(), projection.nodes(), diagnostics);
      var copied = bodyCorrelation.projectExecutable(projection, path.child(SyntaxRole.ARGUMENT, index), unit, sourceFile, diagnostics);
      if (copied.isEmpty()) {
        return Optional.empty();
      }
      var retained = bodyCorrelation.rebind(decisions, projection.translation().value(), copied.orElseThrow());
      children.add(new SyntaxChild(SyntaxRole.ARGUMENT, index, copied.orElseThrow()));
      enumUses.addAll(retained.uses());
      enumInvocations.addAll(retained.invocations());
      enumStaticAccesses.addAll(retained.staticAccesses());
      enumConcatenations.addAll(retained.concatenations());
      enumAttributions.addAll(bodyCorrelation.executionAttributions(value.declarationKey(), projection));
    }
    if (creation.getClassBody() != null) {
      var bodyPath = new TreePath(creationPath, creation.getClassBody());
      var body = nestedDeclarations.project(creation.getClassBody(), bodyPath, path.child(SyntaxRole.CLASS_BODY, 0)).value();
      children.add(new SyntaxChild(SyntaxRole.CLASS_BODY, 0, body));
      enumAttributions.add(new AttributedBodyOccurrence(value.declarationKey(), body.nodeId(), body.sourceFileKey(), body.sourceKind(),
          JavaSyntaxStructuralPath.executionRoot(bodyPath)));
    }

    return Optional.of(new Initialization(children, Optional.of(node -> {
      var decisions = decisionProducer.enumConstant(value.declarationKey(), node, creationPath, constructor);
      enumUses.addAll(decisions.uses());
      enumInvocations.addAll(decisions.invocations());
      enumAttributions.add(new AttributedBodyOccurrence(value.declarationKey(), node.nodeId(), node.sourceFileKey(), node.sourceKind(),
          JavaSyntaxStructuralPath.executionRoot(fieldPath)));
      bodyCorrelation
          .retainScope(new CopiedAstValues.Scope(new ExecutionScope.EnumConstant(value.declarationKey(), value.ownerKey(), constructor),
              node, enumUses, enumInvocations, enumStaticAccesses, enumConcatenations, enumAttributions));
    })));
  }

  private JavaExecutableSyntaxProjector.Projection executableProjection(CompilationUnitTree unit, SourceFile sourceFile, String scopeKey,
      Tree tree, TreePath treePath, SyntaxStructuralPath path, JavaExecutableSyntaxProjector.DeclarationProjection nestedDeclarations) {
    var projector = new JavaExecutableSyntaxProjector(semantics, sourceEvidence, treeInventory, DIAGNOSTIC_PRODUCER,
        "declaration-initialization");
    if (policy.supportsNestedDeclarations()) {
      projector.useDeclarations(nestedDeclarations);
    }
    return projector.project(unit, sourceFile, scopeKey, tree, treePath, path, false, false);
  }

  Optional<SyntaxNode> initializer(CompilationUnitTree unit, SourceFile sourceFile, TreePath treePath, BlockTree block,
      SyntaxStructuralPath path, List<AnalysisDiagnostic> diagnostics,
      JavaExecutableSyntaxProjector.DeclarationProjection nestedDeclarations) {
    var range = sourceEvidence.requiredRange(unit, sourceFile, block, path, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var ownerKey = semantics.declarationKey(semantics.element(treePath.getParentPath()));
    if (ownerKey.isEmpty()) {
      return Optional.empty();
    }
    var origin = range.orElseThrow();
    var key = ownerKey.orElseThrow() + ":initializer:" + origin.startOffset() + ':' + origin.endOffsetExclusive();
    var projection = executableProjection(unit, sourceFile, key, block, treePath, JavaSyntaxStructuralPath.executionRoot(treePath),
        nestedDeclarations);
    var original = projection.translation().value();
    var decisions = new JavaBodyDecisionProducer(semantics).produce(key, original, projection.nodes(), diagnostics);
    var projected = bodyCorrelation.projectExecutable(projection, path, unit, sourceFile, diagnostics);
    if (projected.isEmpty()) {
      return Optional.empty();
    }
    var kind = block.isStatic() ? SyntaxKind.STATIC_INITIALIZER_BLOCK : SyntaxKind.INSTANCE_INITIALIZER_BLOCK;
    var node = sourceAssociations.created(SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.BLOCK, kind,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, projected.orElseThrow().children(), Optional.empty(), origin),
        treePath);
    bodyCorrelation.retainCopy(original, node);
    var retained = bodyCorrelation.rebind(decisions, original, node);
    bodyCorrelation.retainScope(new CopiedAstValues.Scope(new ExecutionScope.Initializer(key, ownerKey.orElseThrow(), origin, kind), node,
        retained.uses(), retained.invocations(), retained.staticAccesses(), retained.concatenations(),
        bodyCorrelation.executionAttributions(key, projection)));
    return Optional.of(node);
  }

  static final class Initialization {
    private final List<SyntaxChild> childNodes;
    private final Optional<Consumer<SyntaxNode>> registration;

    private Initialization(List<SyntaxChild> children, Optional<Consumer<SyntaxNode>> registration) {
      childNodes = List.copyOf(children);
      this.registration = registration;
    }

    List<SyntaxChild> children() {
      return childNodes;
    }

    void retain(SyntaxNode declaration) {
      registration.ifPresent(operation -> operation.accept(declaration));
    }
  }
}
