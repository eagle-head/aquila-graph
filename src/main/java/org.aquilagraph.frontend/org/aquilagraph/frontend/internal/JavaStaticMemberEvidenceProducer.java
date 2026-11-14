package org.aquilagraph.frontend.internal;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.util.TreePath;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.aquilagraph.frontend.internal.JavaExecutableSyntaxProjector.NodeProjection;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StaticMemberKind;
import org.aquilagraph.source.api.SyntaxValue;

/** Copies the selected declaring owner and access classification of static members. */
final class JavaStaticMemberEvidenceProducer {
  private final SemanticResolver semantics;

  JavaStaticMemberEvidenceProducer(SemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
  }

  Optional<StaticMemberEvidence> produce(String scopeKey, NodeProjection projection) {
    var node = projection.node();
    var staticForm = (node.value() instanceof SyntaxValue.FieldAccess field && field.receiver().isStatic())
        || (node.value() instanceof SyntaxValue.Invocation invocation && invocation.form().isStatic());
    var kind = switch (node.syntaxKind()) {
      case FIELD_ACCESS ->
        Optional.of(isAssignmentTarget(projection.compilerAnchor()) ? StaticMemberKind.FIELD_WRITE : StaticMemberKind.FIELD_READ);
      case INVOCATION -> Optional.of(StaticMemberKind.METHOD_INVOKE);
      default -> Optional.<StaticMemberKind>empty();
    };
    var element = semantics.element(projection.compilerAnchor());
    if (kind.isEmpty() || !staticForm || node.semanticReference().flatMap(reference -> reference.symbolKey()).isEmpty()) {
      return Optional.empty();
    }
    if (element != null && (!semantics.isStatic(element) || !JavaSelectedMemberProof.matches(node, element, semantics))) {
      throw new IllegalArgumentException("Selected static member contradicts its exact compiler projection");
    }
    var ownerOrigin = new EvidenceOrigin(scopeKey, node.nodeId(), StaticMemberEvidence.OWNER_SLOT, node.origin());
    var ownerType = element != null && element.getEnclosingElement() instanceof TypeElement owner
        ? semantics.typeReference(owner.asType()).filter(value -> value.declarationKey().isPresent())
        : Optional.<JavaTypeReference>empty();
    var declaringOwner = ownerType.map(value -> EvidenceValue.known(value, ownerOrigin))
        .orElseGet(() -> EvidenceValue.missing(ownerOrigin, "The selected static declaring owner could not be correlated"));
    var constantRead = kind.orElseThrow() == StaticMemberKind.FIELD_READ && element instanceof VariableElement variable
        && variable.getConstantValue() != null;
    var constantOrigin = new EvidenceOrigin(scopeKey, node.nodeId(), StaticMemberEvidence.CONSTANT_SLOT, node.origin());
    var classification = kind.orElseThrow() != StaticMemberKind.FIELD_READ
        ? EvidenceValue.<Boolean>notApplicable(constantOrigin, "This access form does not read a constant variable")
        : element == null
            ? EvidenceValue.<Boolean>missing(constantOrigin, "The selected constant-variable classification is unavailable")
            : EvidenceValue.known(constantRead, constantOrigin);
    return Optional.of(new StaticMemberEvidence(scopeKey, node.nodeId(), node.requiredReference().symbolKey().orElseThrow(),
        kind.orElseThrow(), declaringOwner, classification));
  }

  private static boolean isAssignmentTarget(TreePath path) {
    var target = path.getLeaf();
    var parent = path.getParentPath();
    while (parent != null && parent.getLeaf() instanceof ParenthesizedTree parentheses
        && JavaProjectionIdentity.sameInstance(parentheses.getExpression(), target)) {
      target = parent.getLeaf();
      parent = parent.getParentPath();
    }
    return parent != null && parent.getLeaf() instanceof AssignmentTree assignment
        && JavaProjectionIdentity.sameInstance(assignment.getVariable(), target);
  }
}
