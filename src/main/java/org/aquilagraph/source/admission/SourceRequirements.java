package org.aquilagraph.source.admission;

import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SourceExecutionTraversal;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.ExecutionSyntax;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.JavaSourceKind;

/** Source-only qualification over intrinsically admitted copied facts. */
final class SourceRequirements {
  private SourceRequirements() {
  }

  static AstCompletion assess(SyntaxAnalysisInput input, AstProfile profile) {
    if (!profile.isSupported()) {
      throw new IllegalArgumentException("Unsupported source profile: " + profile.version());
    }
    if (input.snapshot().sourceFiles().isEmpty() || input.aggregate().isEmpty() || input.program().types().isEmpty()) {
      return AstCompletion.UNAVAILABLE;
    }
    if (input.program().types().stream().anyMatch(type -> type.staticType().category() != JavaTypeCategory.DECLARED)
        || (profile.equals(AstProfile.JAVA_SOURCE_NESTED) && input.program().types().stream()
            .anyMatch(type -> (type.nesting().lexicalOwnerKey().isPresent() && type.nesting().ownerDetails().isEmpty())
                || (type.sourceDeclared() && input.aggregate().orElseThrow().declarationMemberOrder(type.typeKey())
                    .flatMap(order -> order.attributedTreeOrder()).isEmpty())))
        || input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError())
        || input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
            .anyMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED)
        || !input.languageFeatureCoverageResult().validationEvidence().unmatchedFacts().isEmpty()
        || input.aggregate().orElseThrow().nodes().stream()
            .anyMatch(node -> hasRequiredSourceGap(node, profile) || hasProfileGap(node, profile)
                || hasDeclarationScopeGap(input.aggregate().orElseThrow(), node, profile))
        || input.aggregate().orElseThrow().executionScopes().stream()
            .flatMap(scope -> input.aggregate().orElseThrow().executionSyntax(scope.key()).stream())
            .anyMatch(execution -> hasRequiredExecutionFactGap(execution, profile))
        || input.program().types().stream().flatMap(type -> type.methods().stream()).filter(method -> method.hasBody())
            .anyMatch(method -> input.aggregate().orElseThrow().methodBodyOrigin(method.methodKey())
                .map(origin -> origin == MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION).orElse(true))) {
      return AstCompletion.INCOMPLETE;
    }
    return AstCompletion.COMPLETE;
  }

  private static boolean hasProfileGap(SyntaxNode node, AstProfile profile) {
    if (profile.equals(AstProfile.JAVA_SOURCE_BOUNDED) && (node.syntaxKind() == SyntaxKind.CLASS_LITERAL
        || node.syntaxKind() == SyntaxKind.UPDATE || node.syntaxKind() == SyntaxKind.COMPOUND_ASSIGNMENT
        || node.syntaxKind() == SyntaxKind.ARRAY_CREATION || isIteration(node.syntaxKind()) || node.syntaxKind() == SyntaxKind.ASSERT
        || node.syntaxKind() == SyntaxKind.SYNCHRONIZED || node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION
        || node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE || node.syntaxKind() == SyntaxKind.INSTANCE_OF
        || node.syntaxKind() == SyntaxKind.BINDING_PATTERN || node.syntaxKind() == SyntaxKind.DECONSTRUCTION_PATTERN
        || node.syntaxKind() == SyntaxKind.ANY_PATTERN || node.syntaxKind() == SyntaxKind.SWITCH_STATEMENT
        || (node.syntaxKind() == SyntaxKind.CASE && !SourceSwitchRequirements.isBoundedCase(node))
        || (node.syntaxKind() == SyntaxKind.TRY
            && (!node.children(SyntaxRole.RESOURCE).isEmpty() || !node.children(SyntaxRole.FINALLY_BLOCK).isEmpty()))
        || node.sourceKind() == JavaSourceKind.UNION_TYPE || node.sourceKind() == JavaSourceKind.INTERSECTION_TYPE)) {
      return true;
    }
    if (!profile.equals(AstProfile.JAVA_SOURCE_NESTED)
        && (!node.children(SyntaxRole.CLASS_BODY).isEmpty() || (node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
            && node.structuralPath().steps().stream().anyMatch(step -> step.role() == SyntaxRole.STATEMENT)))) {
      return true;
    }
    if (!(node.value() instanceof SyntaxValue.LocalDeclaration local)) {
      return false;
    }
    if (profile.equals(AstProfile.JAVA_SOURCE_BOUNDED)) {
      return "_".equals(local.name()) || !local.writtenModifiers().isEmpty() || !node.children(SyntaxRole.ANNOTATION).isEmpty()
          || local.bindingEvidence()
              .filter(binding -> binding.typeForm() != LocalBindingEvidence.TypeForm.DECLARED || !binding.sharedAnnotationKeys().isEmpty())
              .isPresent();
    }
    return ("_".equals(local.name()) && local.bindingEvidence().isEmpty())
        || local.bindingEvidence().map(binding -> binding.declarationKey().isEmpty()).orElse(false)
        || (!"_".equals(local.name()) && node.semanticReference().isEmpty());
  }

  private static boolean hasRequiredExecutionFactGap(ExecutionSyntax execution, AstProfile profile) {
    if ((profile.equals(AstProfile.JAVA_SOURCE_INITIALIZATION) || profile.equals(AstProfile.JAVA_SOURCE_NESTED))
        && !(execution.scope() instanceof ExecutionScope.Method)
        && execution.attributedOccurrences().stream().noneMatch(address -> address.nodeKey().equals(execution.root().nodeId()))) {
      return true;
    }
    return !SourceFormRequirements.supportsBody(execution.root()) || SourceExecutionTraversal.nodes(execution.root()).stream().anyMatch(
        node -> (isStaticMember(node) && execution.staticAccess(node.nodeId()).flatMap(value -> value.declaringOwner().knownValue())
            .flatMap(owner -> owner.declarationKey()).isEmpty()) || hasSelectedArgumentTargetGap(execution, node)
            || (node.syntaxKind() == SyntaxKind.FIELD_DECLARATION && !node.children(SyntaxRole.INITIALIZER).isEmpty()
                && execution.useEvidence(node.nodeId(), SyntaxRole.INITIALIZER, 0).isEmpty()));
  }

  private static boolean hasDeclarationScopeGap(JavaAstAggregate aggregate, SyntaxNode node, AstProfile profile) {
    if (!profile.equals(AstProfile.JAVA_SOURCE_INITIALIZATION) && !profile.equals(AstProfile.JAVA_SOURCE_NESTED)) {
      return false;
    }
    if (node.syntaxKind() == SyntaxKind.FIELD_DECLARATION) {
      return aggregate.executionSyntax(node.value(SyntaxValue.VariableDeclaration.class).declarationKey())
          .filter(scope -> scope.scope() instanceof ExecutionScope.Field).isEmpty();
    }
    return (node.syntaxKind() == SyntaxKind.STATIC_INITIALIZER_BLOCK || node.syntaxKind() == SyntaxKind.INSTANCE_INITIALIZER_BLOCK)
        && aggregate.executionScopes().stream().filter(ExecutionScope.Initializer.class::isInstance)
            .noneMatch(scope -> aggregate.executionSyntax(scope.key()).orElseThrow().root().nodeId().equals(node.nodeId()));
  }

  private static boolean isIteration(SyntaxKind kind) {
    return switch (kind) {
      case WHILE_LOOP, DO_WHILE_LOOP, FOR_LOOP, ENHANCED_FOR_LOOP, LABELED_STATEMENT, BREAK, CONTINUE -> true;
      default -> false;
    };
  }

  private static boolean hasSelectedArgumentTargetGap(ExecutionSyntax body, SyntaxNode node) {
    return (node.syntaxKind() == SyntaxKind.INVOCATION || node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        && node.children().stream().filter(child -> child.role() == SyntaxRole.ARGUMENT)
            .anyMatch(child -> body.useEvidence(node.nodeId(), child.role(), child.position()).isEmpty());
  }

  private static boolean isStaticMember(SyntaxNode node) {
    return (node.value() instanceof SyntaxValue.FieldAccess access && access.receiver().isStatic())
        || (node.syntaxKind() == SyntaxKind.INVOCATION && node.value() instanceof SyntaxValue.Invocation invocation
            && invocation.form().isStatic());
  }

  private static boolean hasRequiredSourceGap(SyntaxNode node, AstProfile profile) {
    if (node.syntaxKind() == SyntaxKind.TYPE_USE && node.value() instanceof SyntaxValue.PackageQualifier
        && node.type() == SyntaxType.NotApplicable.INSTANCE) {
      return false;
    }
    if (!SourceFormRequirements.supports(node, profile)) {
      return true;
    }
    if (node.syntaxKind().requiresType() && (!(node.type() instanceof SyntaxType.Resolved resolved)
        || resolved.identity().category() == JavaTypeCategory.ERROR || resolved.identity().category() == JavaTypeCategory.OTHER)) {
      return true;
    }
    if (node.semanticReference().map(reference -> reference.status() != ResolutionStatus.RESOLVED).orElse(false)) {
      return true;
    }
    return switch (node.syntaxKind()) {
      case INVOCATION, OBJECT_CREATION ->
        node.semanticReference().isEmpty() || node.value(SyntaxValue.Invocation.class).semantics().isEmpty();
      case FIELD_ACCESS, VARIABLE_ACCESS -> node.semanticReference().isEmpty();
      default -> false;
    };
  }
}
