package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.CompilerExpressionAnchor;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.WrittenSourceAssociation;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Preserves actual execution addresses across grammar-derived reassociation of written operands. */
public final class NormalizedExecutionPaths {
  private NormalizedExecutionPaths() {
  }

  static void validate(ExecutionRegion region, OccurrenceIndex<SyntaxNode> parents, OccurrenceIndex<WrittenSourceAssociation> associations,
      int statementOffset, Set<String> validatedRegions) {
    var facts = region.facts();
    var root = facts.root();
    var addresses = region.addresses();
    for (var node : region.nodes().values()) {
      var occurrence = addresses.get(node.nodeId());
      var normalized = Optional.ofNullable(associations.get(node)).flatMap(WrittenSourceAssociation::normalizedExpression)
          .filter(value -> value.occurrencePath().isEmpty() && value.compilerAnchor() instanceof CompilerExpressionAnchor.Execution);
      if (node.nodeId().equals(root.nodeId()) || (occurrence == null && normalized.isEmpty())) {
        continue;
      }
      var parent = parents.get(node);
      if (parent == null) {
        throw new IllegalArgumentException("A normalized execution occurrence requires its exact parent");
      }
      var expected = expected(facts, parent, node, addresses, associations, statementOffset);
      if (occurrence != null && !expected.equals(occurrence.compilerPath())) {
        throw new IllegalArgumentException("An intact execution occurrence must preserve its actual parent or normalized operand address");
      }
      if (normalized.isPresent()) {
        var anchor = (CompilerExpressionAnchor.Execution) normalized.orElseThrow().compilerAnchor();
        if (!expected.equals(anchor.path())) {
          throw new IllegalArgumentException("A normalized execution region must preserve its actual creating parent address");
        }
        validatedRegions.add(node.nodeId());
      }
    }
  }

  private static SyntaxStructuralPath expected(SourceExecutionFacts facts, SyntaxNode parent, SyntaxNode node,
      Map<String, AttributedBodyOccurrence> addresses, OccurrenceIndex<WrittenSourceAssociation> associations, int statementOffset) {
    var attachment = node.structuralPath().steps().getLast();
    var observedParent = addresses.get(parent.nodeId());
    if (observedParent != null) {
      var path = observedParent.compilerPath();
      var rootChild = parent.nodeId().equals(facts.root().nodeId());
      if (rootChild && facts.scope() instanceof ExecutionScope.EnumConstant
          && (attachment.role() == SyntaxRole.ARGUMENT || attachment.role() == SyntaxRole.CLASS_BODY)) {
        path = path.child(SyntaxRole.INITIALIZER, 0);
      }
      return path.child(attachment.role(),
          attachment.position() + (rootChild && attachment.role() == SyntaxRole.STATEMENT ? statementOffset : 0));
    }
    var normalized = Optional.ofNullable(associations.get(parent)).flatMap(WrittenSourceAssociation::normalizedExpression)
        .orElseThrow(() -> new IllegalArgumentException("An execution occurrence requires its exact or explicitly normalized parent"));
    if (!(normalized.compilerAnchor() instanceof CompilerExpressionAnchor.Execution anchor)) {
      throw new IllegalArgumentException("An execution operand cannot borrow an annotation source address");
    }
    var association = Optional.ofNullable(associations.get(node))
        .orElseThrow(() -> new IllegalArgumentException("An intact normalized operand requires its original source owner"));
    var owner = normalized.region().compilerOwner();
    if (!owner.isAncestorOf(association.ownerPath()) || owner.equals(association.ownerPath())) {
      throw new IllegalArgumentException("An intact operand requires its original owner within the normalized compiler region");
    }
    var path = anchor.path();
    for (var step : association.ownerPath().steps().subList(owner.size(), association.ownerPath().size())) {
      if (step.role() != SourceOwnerPath.Role.LEFT_OPERAND && step.role() != SourceOwnerPath.Role.RIGHT_OPERAND) {
        throw new IllegalArgumentException("An intact additive operand must retain its actual original additive-tree address");
      }
      path = path.child(step.role() == SourceOwnerPath.Role.LEFT_OPERAND ? SyntaxRole.LEFT_OPERAND : SyntaxRole.RIGHT_OPERAND,
          step.ordinal());
    }
    return path;
  }

}
