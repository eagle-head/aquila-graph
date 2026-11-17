package org.aquilagraph.frontend.internal;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import org.aquilagraph.frontend.internal.ConstructorCorrelation.Status;
import org.aquilagraph.frontend.internal.JavaExecutableSyntaxProjector.NodeProjection;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.ConversionEvidence;
import org.aquilagraph.source.api.InvocationApplicability;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.InvocationSelection;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

/** Produces applicability and argument evidence from selected executable projections. */
final class JavaInvocationEvidenceProducer {
  private final SemanticResolver semantics;
  private final JavaInvocationApplicability applicability;
  private final JavaConversionEvidenceProducer conversions;

  JavaInvocationEvidenceProducer(SemanticResolver semantics, JavaConversionEvidenceProducer conversions) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.conversions = Objects.requireNonNull(conversions, "conversions");
    applicability = new JavaInvocationApplicability(semantics);
  }

  Optional<Selection> produce(String scopeKey, NodeProjection consumer, JavaExecutableProjectionProof bindings,
      DiagnosticEmissionCollector diagnostics) {
    if (!(semantics.element(consumer.compilerAnchor()) instanceof ExecutableElement executable)) {
      return Optional.empty();
    }
    if (consumer.node().value() instanceof SyntaxValue.Invocation invocation
        && invocation.semantics().filter(value -> value.selection() == InvocationSelection.ARRAY_CLONE).isPresent()) {
      return intrinsicInvocation(scopeKey, consumer, executable, invocation);
    }
    if (!JavaSelectedMemberProof.matches(consumer.node(), executable, semantics)) {
      return Optional.empty();
    }
    if (consumer.compilerAnchor().getLeaf() instanceof NewClassTree creation
        && (!executable.getTypeParameters().isEmpty() || (creation.getClassBody() != null && !creation.getArguments().isEmpty()))) {
      return constructorInvocation(scopeKey, consumer.node(), consumer.compilerAnchor(), executable, diagnostics);
    }
    var selectedType = selectedExecutable(consumer.compilerAnchor(), executable);
    if (selectedType.isEmpty()) {
      return Optional.empty();
    }
    var arguments = new ArrayList<TypeMirror>();
    for (var child : consumer.node().children()) {
      if (child.role() == SyntaxRole.ARGUMENT) {
        var mirror = conversions.mirror(bindings.binding(child.node()));
        if (mirror.isEmpty()) {
          return Optional.empty();
        }
        arguments.add(mirror.orElseThrow());
      }
    }
    return applicability.select(selectedType.orElseThrow(), executable.isVarArgs(), arguments).map(selected -> {
      var invocation = selectedApplicability(scopeKey, consumer.node(), selected);
      var uses = new ArrayList<ChildUseEvidence>();
      for (var child : consumer.node().children()) {
        if (child.role() == SyntaxRole.ARGUMENT) {
          conversions
              .argument(scopeKey, consumer.node(), child, bindings.binding(child.node()), selected.argumentTargets().get(child.position()))
              .ifPresent(uses::add);
        }
      }
      return new Selection(invocation, uses);
    });
  }

  private Optional<Selection> intrinsicInvocation(String scopeKey, NodeProjection consumer, ExecutableElement executable,
      SyntaxValue.Invocation invocation) {
    var selected = semantics.arrayCloneType(consumer.compilerAnchor(), executable);
    if (selected.isEmpty()) {
      return Optional.empty();
    }
    if (!selected.equals(invocation.semantics().map(value -> value.qualifyingType())) || consumer.node().semanticReference().isPresent()
        || !consumer.node().children(SyntaxRole.ARGUMENT).isEmpty()) {
      throw new IllegalArgumentException("Selected compiler intrinsic contradicts the emitted invocation");
    }
    return Optional.of(new Selection(new InvocationApplicability(scopeKey, consumer.node().nodeId(), InvocationApplicabilityMode.FIXED, 0,
        Optional.empty(), Optional.empty(), Optional.empty()), List.of()));
  }

  Optional<Selection> enumConstant(String scopeKey, SyntaxNode constant, TreePath creationPath, Optional<SemanticReference> constructor,
      DiagnosticEmissionCollector diagnostics) {
    var fieldPath = creationPath.getParentPath();
    if (fieldPath == null || !(fieldPath.getLeaf() instanceof VariableTree field)
        || !JavaProjectionIdentity.sameInstance(field.getInitializer(), creationPath.getLeaf())
        || !(constant.value() instanceof SyntaxValue.VariableDeclaration declaration)
        || declaration.kind() != SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT || !declaration.declarationKey().equals(scopeKey)
        || !semantics.declarationKey(semantics.element(fieldPath)).equals(Optional.of(scopeKey))) {
      throw new IllegalArgumentException("Enum constructor selection requires its exact constant declaration");
    }
    var selectedPath = semantics.enumConstructorPath(creationPath);
    if (selectedPath.isEmpty() || !(semantics.element(selectedPath.orElseThrow()) instanceof ExecutableElement executable)
        || !constructor.flatMap(reference -> reference.symbolKey()).equals(semantics.declarationKey(executable))
        || !(creationPath.getLeaf() instanceof NewClassTree creation)) {
      return Optional.empty();
    }
    if (!executable.getTypeParameters().isEmpty()) {
      return semantics.element(creationPath) instanceof ExecutableElement actual
          ? constructorInvocation(scopeKey, constant, creationPath, actual, diagnostics)
          : Optional.empty();
    }
    if (!(executable.asType() instanceof ExecutableType signature)) {
      return Optional.empty();
    }
    var arguments = creation.getArguments().stream().map(argument -> semantics.attributedMirror(new TreePath(creationPath, argument)))
        .toList();
    if (arguments.stream().anyMatch(Optional::isEmpty)) {
      return Optional.empty();
    }
    return applicability.select(signature, executable.isVarArgs(), arguments.stream().map(Optional::orElseThrow).toList()).map(selected -> {
      var invocation = selectedApplicability(scopeKey, constant, selected);
      var uses = new ArrayList<ChildUseEvidence>();
      for (var child : constant.children()) {
        if (child.role() == SyntaxRole.ARGUMENT) {
          var argumentPath = Objects.requireNonNull(TreePath.getPath(creationPath, creation.getArguments().get(child.position())),
              "enum argument path");
          conversions.argument(scopeKey, constant, child, argumentPath, selected.argumentTargets().get(child.position()))
              .ifPresent(uses::add);
        }
      }
      return new Selection(invocation, uses);
    });
  }

  private InvocationApplicability selectedApplicability(String scopeKey, SyntaxNode consumer,
      JavaInvocationApplicability.Selection selected) {
    var array = selected.variableArray();
    return new InvocationApplicability(scopeKey, consumer.nodeId(), selected.mode(), selected.formalCount(),
        array.map(semantics::typeIdentity), array.map(value -> semantics.typeIdentity(semantics.erasedType(value))),
        array.map(value -> semantics.typeIdentity(value.getComponentType())));
  }

  private Optional<Selection> constructorInvocation(String scopeKey, SyntaxNode consumer, TreePath path, ExecutableElement executable,
      DiagnosticEmissionCollector diagnostics) {
    var correlation = semantics.selectedConstructor(path, executable);
    var proof = correlation.facts();
    if (proof.isEmpty()) {
      missingConstructorCorrelation(consumer, correlation.status(), diagnostics);
      return Optional.empty();
    }
    var facts = proof.orElseThrow();
    var children = consumer.children(SyntaxRole.ARGUMENT);
    if (children.size() != facts.arguments().size()) {
      missingConstructorCorrelation(consumer, Status.UNAVAILABLE, diagnostics);
      return Optional.empty();
    }
    for (var index = 0; index < children.size(); index++) {
      if (!children.get(index).resolvedType().equals(Optional.of(facts.arguments().get(index).sourceType()))) {
        missingConstructorCorrelation(consumer, Status.UNAVAILABLE, diagnostics);
        return Optional.empty();
      }
    }
    var invocation = new InvocationApplicability(scopeKey, consumer.nodeId(), facts.mode(), facts.formalCount(), facts.varargArrayType(),
        facts.erasedArrayType(), facts.elementType());
    var uses = new ArrayList<ChildUseEvidence>();
    for (var index = 0; index < children.size(); index++) {
      var argument = facts.arguments().get(index);
      if (children.get(index).syntaxKind().requiresType()) {
        uses.add(ChildUseEvidence.known(new ConversionEvidence(scopeKey, consumer.nodeId(), SyntaxRole.ARGUMENT, index,
            argument.sourceType(), argument.selectedTarget(), Optional.empty(), argument.unboxedType(), argument.effectivePrimitiveType(),
            argument.preBoxPrimitiveType(), argument.boxedType()), consumer.syntaxKind(), consumer.origin()));
      }
    }
    return Optional.of(new Selection(invocation, uses));
  }

  private static void missingConstructorCorrelation(SyntaxNode consumer, Status status, DiagnosticEmissionCollector diagnostics) {
    var arguments = consumer.children(SyntaxRole.ARGUMENT);
    if (arguments.isEmpty() || arguments.stream().anyMatch(argument -> argument.resolvedType().isEmpty())) {
      return;
    }
    var rejected = status == Status.WITNESS_REJECTED;
    diagnostics.emit(FrontendDiagnosticFactory.error(consumer.nodeId(),
        rejected ? "frontend.constructor-witness-rejected" : "frontend.missing-constructor-correlation",
        rejected
            ? "Private constructor witness compilation did not establish the required argument evidence"
            : "Required constructor argument evidence could not be correlated with its captured source occurrence",
        consumer.origin(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
  }

  private Optional<ExecutableType> selectedExecutable(TreePath path, ExecutableElement executable) {
    if (path.getLeaf() instanceof MethodInvocationTree invocation) {
      var mirror = semantics.attributedMirror(new TreePath(path, invocation.getMethodSelect()));
      return mirror.filter(ExecutableType.class::isInstance).map(ExecutableType.class::cast);
    }
    if (path.getLeaf() instanceof NewClassTree) {
      var owner = semantics.attributedMirror(path);
      if (owner.orElse(null) instanceof DeclaredType declared) {
        var member = semantics.memberType(declared, executable);
        return member instanceof ExecutableType selected ? Optional.of(selected) : Optional.empty();
      }
    }
    return Optional.empty();
  }

  record Selection(InvocationApplicability applicability, List<ChildUseEvidence> uses) {
    Selection {
      Objects.requireNonNull(applicability, "applicability");
      uses = List.copyOf(uses);
    }
  }
}
