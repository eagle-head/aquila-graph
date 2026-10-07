package org.aquilagraph.source.admission;

import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.MethodSyntax;
import org.aquilagraph.source.api.JavaTypeCategory;

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
    if (input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError())
        || input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
            .anyMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED)
        || !input.languageFeatureCoverageResult().validationEvidence().unmatchedFacts().isEmpty()
        || input.aggregate().orElseThrow().nodes().stream().anyMatch(SourceRequirements::hasRequiredSourceGap)
        || input.program().methods().stream().flatMap(method -> input.aggregate().orElseThrow().methodSyntax(method.methodKey()).stream())
            .anyMatch(SourceRequirements::hasRequiredBodyFactGap)
        || input.program().types().stream().flatMap(type -> type.methods().stream()).filter(method -> method.hasBody())
            .anyMatch(method -> input.aggregate().orElseThrow().methodBodyOrigin(method.methodKey())
                .map(origin -> origin == MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION).orElse(true))) {
      return AstCompletion.INCOMPLETE;
    }
    return AstCompletion.COMPLETE;
  }

  private static boolean hasRequiredBodyFactGap(MethodSyntax body) {
    return !SourceFormRequirements.supportsBody(body.root()) || body.root().descendantsAndSelf().stream()
        .anyMatch(node -> (isStaticMember(node) && body.staticAccess(node.nodeId()).flatMap(value -> value.declaringOwner().knownValue())
            .flatMap(owner -> owner.declarationKey()).isEmpty()) || hasSelectedArgumentTargetGap(body, node));
  }

  private static boolean hasSelectedArgumentTargetGap(MethodSyntax body, SyntaxNode node) {
    return (node.syntaxKind() == SyntaxKind.INVOCATION || node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        && node.children().stream().filter(child -> child.role() == SyntaxRole.ARGUMENT)
            .anyMatch(child -> body.useEvidence(node.nodeId(), child.role(), child.position()).isEmpty());
  }

  private static boolean isStaticMember(SyntaxNode node) {
    return (node.value() instanceof SyntaxValue.FieldAccess access && access.receiver().isStatic())
        || (node.syntaxKind() == SyntaxKind.INVOCATION && node.value() instanceof SyntaxValue.Invocation invocation
            && invocation.form().isStatic());
  }

  private static boolean hasRequiredSourceGap(SyntaxNode node) {
    if (node.syntaxKind() == SyntaxKind.TYPE_USE && node.value() instanceof SyntaxValue.PackageQualifier
        && node.type() == SyntaxType.NotApplicable.INSTANCE) {
      return false;
    }
    if (!SourceFormRequirements.supports(node)) {
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
