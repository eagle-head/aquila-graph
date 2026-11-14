package org.aquilagraph.frontend.internal;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.frontend.internal.JavaExecutableSyntaxProjector.NodeProjection;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;

/** Copies constant and operand evidence from each producing string projection. */
final class JavaStringExpressionEvidenceProducer {
  private final JavaConstantExpression constants;

  JavaStringExpressionEvidenceProducer(SemanticResolver semantics) {
    constants = new JavaConstantExpression(Objects.requireNonNull(semantics, "semantics"));
  }

  Optional<StringExpressionEvidence> produce(String scopeKey, NodeProjection projection, JavaExecutableProjectionProof evidence) {
    var node = projection.node();
    if (!(node.value() instanceof SyntaxValue.Operator operator) || operator.operator() != SyntaxOperator.PLUS || !isString(node)) {
      return Optional.empty();
    }
    var left = node.requiredChild(SyntaxRole.LEFT_OPERAND);
    var right = node.requiredChild(SyntaxRole.RIGHT_OPERAND);
    if (left.resolvedType().isPresent() && right.resolvedType().isPresent() && !isString(left) && !isString(right)) {
      throw new IllegalArgumentException("An attributed String PLUS requires a String operand");
    }
    var origin = new EvidenceOrigin(scopeKey, node.nodeId(), StringExpressionEvidence.CONSTANT_SLOT, node.origin());
    var constant = constantStatus(projection).map(value -> EvidenceValue.known(value, origin))
        .orElseGet(() -> EvidenceValue.missing(origin, "The compiler constant-expression classification is unavailable"));
    return Optional.of(new StringExpressionEvidence(scopeKey, node.nodeId(), node.resolvedType().orElseThrow(),
        stringOperand(scopeKey, node, left, StringExpressionEvidence.LEFT_SLOT, evidence),
        stringOperand(scopeKey, node, right, StringExpressionEvidence.RIGHT_SLOT, evidence), constant));
  }

  private static EvidenceValue<Boolean> stringOperand(String scopeKey, SyntaxNode consumer, SyntaxNode operand, String slot,
      JavaExecutableProjectionProof evidence) {
    var origin = new EvidenceOrigin(scopeKey, consumer.nodeId(), slot, consumer.origin());
    if (operand.resolvedType().isPresent()) {
      return EvidenceValue.known(isString(operand), origin);
    }
    if (operand.type() == SyntaxType.Erroneous.INSTANCE) {
      var diagnostic = evidence.binding(operand).erroneousType().orElseThrow();
      return EvidenceValue.erroneous(origin, "The attributed operand type is erroneous", diagnostic);
    }
    return EvidenceValue.missing(origin, "The compiler operand classification is unavailable");
  }

  private Optional<Boolean> constantStatus(NodeProjection projection) {
    return projection.derivation().isPresent()
        ? projection.derivation().orElseThrow().constantStatus()
        : constants.constantStatus(projection.compilerAnchor());
  }

  private static boolean isString(SyntaxNode node) {
    return node.resolvedType().map(type -> type.category() == JavaTypeCategory.DECLARED && "java.lang.String".equals(type.erasedName()))
        .orElse(false);
  }
}
