package org.aquilagraph.frontend.internal;

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
import javax.lang.model.element.ElementKind;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.SemanticReference;
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
final class JavaInitializationProjector {
  private static final String DIAGNOSTIC_PRODUCER = "JavaAstContributionTranslator";
  private final SemanticResolver semantics;
  private final JavaConversionEvidenceProducer conversions;
  private final JavaInvocationEvidenceProducer invocations;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaTranslationPolicy policy;
  private final JavaWrittenSourceAssociations sourceAssociations;
  private final JavaBodySourceCorrelation bodyCorrelation;

  JavaInitializationProjector(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaTranslationPolicy policy, JavaWrittenSourceAssociations sourceAssociations, JavaBodySourceCorrelation bodyCorrelation) {
    this.semantics = semantics;
    conversions = new JavaConversionEvidenceProducer(semantics);
    invocations = new JavaInvocationEvidenceProducer(semantics, conversions);
    this.sourceEvidence = sourceEvidence;
    this.treeInventory = treeInventory;
    this.policy = policy;
    this.sourceAssociations = sourceAssociations;
    this.bodyCorrelation = bodyCorrelation;
  }

  Optional<Initialization> fieldChildren(CompilationUnitTree unit, SourceFile sourceFile, TreePath fieldPath, VariableTree declaration,
      SyntaxValue.VariableDeclaration value, SyntaxStructuralPath path, DiagnosticEmissionCollector diagnostics,
      JavaExecutableProjectionContext.DeclarationProjection nestedDeclarations) {
    treeInventory.requireSource(unit, sourceFile);
    var parent = fieldPath.getParentPath();
    var element = semantics.element(fieldPath);
    var kind = value.kind() == SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT ? ElementKind.ENUM_CONSTANT : ElementKind.FIELD;
    if (!JavaProjectionIdentity.sameInstance(unit, fieldPath.getCompilationUnit())
        || !JavaProjectionIdentity.sameInstance(declaration, fieldPath.getLeaf())
        || !JavaProjectionIdentity.sameTreePath(TreePath.getPath(unit, declaration), fieldPath) || element == null
        || element.getKind() != kind || parent == null || !semantics.declarationKey(element).equals(Optional.of(value.declarationKey()))
        || !semantics.declarationKey(semantics.element(parent)).equals(Optional.of(value.ownerKey()))) {
      throw new IllegalArgumentException("Field initialization requires its exact attributed declaration and compiler path");
    }
    var initializer = declaration.getInitializer();
    if (policy.supportsEnumInitialization() && value.kind() == SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT
        && initializer instanceof NewClassTree creation) {
      return enumChildren(unit, sourceFile, fieldPath, creation, value, path, diagnostics, nestedDeclarations);
    }
    if (!policy.supportsMemberInitialization() || value.kind() == SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT) {
      return Optional.of(new Initialization(List.of(), Optional.empty(), NoRegistration.INSTANCE));
    }
    var target = new DeclarationTarget(sourceFile, fieldPath, path, value);
    if (initializer == null) {
      return Optional.of(new Initialization(List.of(), Optional.of(target),
          new FieldRegistration(value, fieldPath, Optional.empty(), new JavaBodyDecisions(List.of(), List.of(), List.of(), List.of()))));
    }
    var projection = executableProjection(unit, sourceFile, value.declarationKey(), initializer, new TreePath(fieldPath, initializer),
        JavaSyntaxStructuralPath.executionRoot(fieldPath).child(SyntaxRole.INITIALIZER, 0), memberContext(nestedDeclarations));
    var decisions = new JavaBodyDecisionProducer(semantics).produce(projection);
    diagnostics.include(decisions.emissions());
    var copied = bodyCorrelation.projectExecutable(projection, path.child(SyntaxRole.INITIALIZER, 0), diagnostics);
    return copied.map(node -> new Initialization(List.of(new SyntaxChild(SyntaxRole.INITIALIZER, 0, node)), Optional.of(target),
        new FieldRegistration(value, fieldPath, Optional.of(projection), decisions.value())));
  }

  void retain(Initialization initialization, SyntaxNode declaration, DiagnosticEmissionCollector diagnostics) {
    if (initialization.target.isPresent()) {
      requireInitializationDeclaration(initialization.target.orElseThrow(), declaration);
      requireInitializationChildren(initialization, declaration);
    }
    switch (initialization.registration) {
      case NoRegistration _ -> {
      }
      case FieldRegistration field -> retainField(field.value(), declaration, field.path(), field.projection(), field.decisions());
      case EnumRegistration constant -> retainEnum(constant, declaration, diagnostics);
    }
  }

  private void requireInitializationDeclaration(DeclarationTarget target, SyntaxNode declaration) {
    var path = target.compilerPath();
    var expectedKind = target.value().kind() == SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT
        ? SyntaxKind.ENUM_CONSTANT_DECLARATION
        : SyntaxKind.FIELD_DECLARATION;
    var origin = sourceEvidence.origin(path.getCompilationUnit(), target.source(), path.getLeaf());
    if (declaration.syntaxKind() != expectedKind || declaration.sourceKind() != JavaSourceKind.VARIABLE
        || !target.value().equals(declaration.value()) || !target.source().sourceFileKey().equals(declaration.sourceFileKey())
        || !target.path().equals(declaration.structuralPath()) || !origin.equals(declaration.origin())
        || !semantics.declarationKey(semantics.element(path)).equals(Optional.of(target.value().declarationKey()))) {
      throw new IllegalArgumentException("An initialization scope requires its exact source declaration");
    }
  }

  private static void requireInitializationChildren(Initialization initialization, SyntaxNode declaration) {
    var retained = declaration.children().stream()
        .filter(
            child -> child.role() == SyntaxRole.INITIALIZER || child.role() == SyntaxRole.ARGUMENT || child.role() == SyntaxRole.CLASS_BODY)
        .toList();
    var produced = initialization.children();
    if (retained.size() != produced.size()) {
      throw new IllegalArgumentException("An initialization declaration requires its exact copied children");
    }
    for (var index = 0; index < produced.size(); index++) {
      var expected = produced.get(index);
      var child = retained.get(index);
      if (expected.role() != child.role() || expected.position() != child.position()
          || !JavaProjectionIdentity.sameInstance(expected.node(), child.node())) {
        throw new IllegalArgumentException("An initialization declaration requires its exact copied children");
      }
    }
  }

  private void retainField(SyntaxValue.VariableDeclaration value, SyntaxNode node, TreePath fieldPath,
      Optional<JavaExecutableSyntaxProjector.Projection> projection, JavaBodyDecisions initializerDecisions) {
    var decisions = projection.map(valueProjection -> bodyCorrelation.rebind(initializerDecisions, valueProjection.translation().value(),
        node.requiredChild(SyntaxRole.INITIALIZER))).orElse(initializerDecisions);
    var uses = new ArrayList<>(decisions.uses());
    projection.flatMap(evidence -> conversions.fieldInitializer(evidence, node)).ifPresent(uses::add);
    var attributions = projection.map(valueProjection -> new ArrayList<>(bodyCorrelation.executionAttributions(valueProjection)))
        .orElseGet(ArrayList::new);
    attributions.add(new AttributedBodyOccurrence(value.declarationKey(), node.nodeId(), node.sourceFileKey(), node.sourceKind(),
        JavaSyntaxStructuralPath.executionRoot(fieldPath)));
    bodyCorrelation.retainScope(new CopiedAstValues.Scope(new ExecutionScope.Field(value.declarationKey(), value.ownerKey()), node, uses,
        decisions.invocations(), decisions.staticAccesses(), decisions.concatenations(), attributions));
  }

  private Optional<Initialization> enumChildren(CompilationUnitTree unit, SourceFile sourceFile, TreePath fieldPath, NewClassTree creation,
      SyntaxValue.VariableDeclaration value, SyntaxStructuralPath path, DiagnosticEmissionCollector diagnostics,
      JavaExecutableProjectionContext.DeclarationProjection nestedDeclarations) {
    var context = JavaExecutableProjectionContext.enumInitialization(policy, nestedDeclarations);
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
          JavaSyntaxStructuralPath.executionRoot(argumentPath), context);
      var decisions = decisionProducer.produce(projection);
      diagnostics.include(decisions.emissions());
      var copied = bodyCorrelation.projectExecutable(projection, path.child(SyntaxRole.ARGUMENT, index), diagnostics);
      if (copied.isEmpty()) {
        return Optional.empty();
      }
      var retained = bodyCorrelation.rebind(decisions.value(), projection.translation().value(), copied.orElseThrow());
      children.add(new SyntaxChild(SyntaxRole.ARGUMENT, index, copied.orElseThrow()));
      enumUses.addAll(retained.uses());
      enumInvocations.addAll(retained.invocations());
      enumStaticAccesses.addAll(retained.staticAccesses());
      enumConcatenations.addAll(retained.concatenations());
      enumAttributions.addAll(bodyCorrelation.executionAttributions(projection));
    }
    if (creation.getClassBody() != null) {
      var bodyPath = new TreePath(creationPath, creation.getClassBody());
      var translated = context.declarations().orElseThrow().project(creation.getClassBody(), bodyPath,
          path.child(SyntaxRole.CLASS_BODY, 0));
      diagnostics.include(translated.emissions());
      var body = translated.value();
      children.add(new SyntaxChild(SyntaxRole.CLASS_BODY, 0, body));
      enumAttributions.add(new AttributedBodyOccurrence(value.declarationKey(), body.nodeId(), body.sourceFileKey(), body.sourceKind(),
          JavaSyntaxStructuralPath.executionRoot(bodyPath)));
    }

    var target = new DeclarationTarget(sourceFile, fieldPath, path, value);
    return Optional.of(new Initialization(children, Optional.of(target), new EnumRegistration(value, fieldPath, creationPath, constructor,
        new JavaBodyDecisions(enumUses, enumInvocations, enumStaticAccesses, enumConcatenations), enumAttributions)));
  }

  private void retainEnum(EnumRegistration constant, SyntaxNode node, DiagnosticEmissionCollector diagnostics) {
    var value = constant.value();
    var emissions = new DiagnosticEmissionCollector("JavaBodyDecisionProducer", value.declarationKey(), DiagnosticSubject.None.INSTANCE);
    var selection = invocations.enumConstant(value.declarationKey(), node, constant.creationPath(), constant.constructor(), emissions);
    diagnostics.include(emissions.snapshot());
    var uses = new ArrayList<>(constant.decisions().uses());
    var retainedInvocations = new ArrayList<>(constant.decisions().invocations());
    selection.ifPresent(selected -> {
      uses.addAll(selected.uses());
      retainedInvocations.add(InvocationEvidence.known(selected.applicability(), node.origin()));
    });
    var attributions = new ArrayList<>(constant.attributions());
    attributions.add(new AttributedBodyOccurrence(value.declarationKey(), node.nodeId(), node.sourceFileKey(), node.sourceKind(),
        JavaSyntaxStructuralPath.executionRoot(constant.fieldPath())));
    bodyCorrelation.retainScope(
        new CopiedAstValues.Scope(new ExecutionScope.EnumConstant(value.declarationKey(), value.ownerKey(), constant.constructor()), node,
            uses, retainedInvocations, constant.decisions().staticAccesses(), constant.decisions().concatenations(), attributions));
  }

  private JavaExecutableSyntaxProjector.Projection executableProjection(CompilationUnitTree unit, SourceFile sourceFile, String scopeKey,
      Tree tree, TreePath treePath, SyntaxStructuralPath path, JavaExecutableProjectionContext context) {
    var projector = new JavaExecutableSyntaxProjector(semantics, sourceEvidence, treeInventory, context, DIAGNOSTIC_PRODUCER,
        "declaration-initialization");
    return projector.project(unit, sourceFile, scopeKey, tree, treePath, path, false, false);
  }

  private JavaExecutableProjectionContext memberContext(JavaExecutableProjectionContext.DeclarationProjection nestedDeclarations) {
    return policy.supportsNestedDeclarations()
        ? JavaExecutableProjectionContext.memberInitialization(policy, nestedDeclarations)
        : JavaExecutableProjectionContext.memberInitialization(policy);
  }

  Optional<SyntaxNode> initializer(CompilationUnitTree unit, SourceFile sourceFile, TreePath treePath, BlockTree block,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, DiagnosticEmissionCollector diagnostics,
      JavaExecutableProjectionContext.DeclarationProjection nestedDeclarations) {
    if (!policy.supportsMemberInitialization()) {
      return sourceEvidence.requiredRange(unit, sourceFile, block, occurrencePath, diagnostics).value()
          .map(range -> sourceAssociations.created(SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.BLOCK,
              block.isStatic() ? SyntaxKind.STATIC_INITIALIZER_BLOCK : SyntaxKind.INSTANCE_INITIALIZER_BLOCK,
              SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(), range), treePath));
    }
    var context = memberContext(nestedDeclarations);
    var range = sourceEvidence.requiredRange(unit, sourceFile, block, path, diagnostics).value();
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
        context);
    var original = projection.translation().value();
    var decisions = new JavaBodyDecisionProducer(semantics).produce(projection);
    diagnostics.include(decisions.emissions());
    var projected = bodyCorrelation.projectExecutable(projection, path, diagnostics);
    if (projected.isEmpty()) {
      return Optional.empty();
    }
    var kind = block.isStatic() ? SyntaxKind.STATIC_INITIALIZER_BLOCK : SyntaxKind.INSTANCE_INITIALIZER_BLOCK;
    var node = sourceAssociations.created(SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.BLOCK, kind,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, projected.orElseThrow().children(), Optional.empty(), origin),
        treePath);
    bodyCorrelation.retainCopy(original, node);
    var retained = bodyCorrelation.rebind(decisions.value(), original, node);
    bodyCorrelation.retainScope(new CopiedAstValues.Scope(new ExecutionScope.Initializer(key, ownerKey.orElseThrow(), origin, kind), node,
        retained.uses(), retained.invocations(), retained.staticAccesses(), retained.concatenations(),
        bodyCorrelation.executionAttributions(projection)));
    return Optional.of(node);
  }

  static final class Initialization {
    private final List<SyntaxChild> childNodes;
    private final Optional<DeclarationTarget> target;
    private final Registration registration;

    private Initialization(List<SyntaxChild> children, Optional<DeclarationTarget> target, Registration registration) {
      childNodes = List.copyOf(children);
      this.target = target;
      this.registration = registration;
    }

    List<SyntaxChild> children() {
      return childNodes;
    }

  }

  private record DeclarationTarget(SourceFile source, TreePath compilerPath, SyntaxStructuralPath path,
      SyntaxValue.VariableDeclaration value) {
  }

  private sealed interface Registration {
  }

  private enum NoRegistration implements Registration {
    INSTANCE
  }

  private record FieldRegistration(SyntaxValue.VariableDeclaration value, TreePath path,
      Optional<JavaExecutableSyntaxProjector.Projection> projection, JavaBodyDecisions decisions) implements Registration {
  }

  private record EnumRegistration(SyntaxValue.VariableDeclaration value, TreePath fieldPath, TreePath creationPath,
      Optional<SemanticReference> constructor, JavaBodyDecisions decisions,
      List<AttributedBodyOccurrence> attributions) implements Registration {
    private EnumRegistration {
      attributions = List.copyOf(attributions);
    }
  }
}
