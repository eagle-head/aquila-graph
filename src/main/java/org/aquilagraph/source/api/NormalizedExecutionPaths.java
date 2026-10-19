package org.aquilagraph.source.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Preserves actual execution addresses across grammar-derived reassociation of written operands. */
final class NormalizedExecutionPaths {
  private NormalizedExecutionPaths() {
  }

  static void validate(SourceExecutionFacts facts, Map<String, AttributedBodyOccurrence> addresses,
      Map<String, WrittenSourceAssociation> associations, int statementOffset, Set<String> validatedRegions) {
    var root = facts.root();
    var nodes = SourceExecutionTraversal.nodes(root);
    var parents = new HashMap<String, Parent>();
    for (var node : nodes) {
      if (node.syntaxKind() != SyntaxKind.TYPE_DECLARATION) {
        node.children().forEach(child -> parents.put(child.node().nodeId(), new Parent(node, child.role(), child.position())));
      }
    }
    for (var node : nodes) {
      var occurrence = addresses.get(node.nodeId());
      var normalized = Optional.ofNullable(associations.get(node.nodeId())).flatMap(WrittenSourceAssociation::normalizedExpression)
          .filter(value -> value.occurrencePath().isEmpty() && value.compilerAnchor() instanceof CompilerExpressionAnchor.Execution);
      if (node.nodeId().equals(root.nodeId()) || (occurrence == null && normalized.isEmpty())) {
        continue;
      }
      var parent = Objects.requireNonNull(parents.get(node.nodeId()), "execution occurrence parent");
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

  private static SyntaxStructuralPath expected(SourceExecutionFacts facts, Parent parent, SyntaxNode node,
      Map<String, AttributedBodyOccurrence> addresses, Map<String, WrittenSourceAssociation> associations, int statementOffset) {
    var observedParent = addresses.get(parent.node().nodeId());
    if (observedParent != null) {
      var path = observedParent.compilerPath();
      var rootChild = parent.node().nodeId().equals(facts.root().nodeId());
      if (rootChild && facts.scope() instanceof ExecutionScope.EnumConstant
          && (parent.role() == SyntaxRole.ARGUMENT || parent.role() == SyntaxRole.CLASS_BODY)) {
        path = path.child(SyntaxRole.INITIALIZER, 0);
      }
      return path.child(parent.role(), parent.position() + (rootChild && parent.role() == SyntaxRole.STATEMENT ? statementOffset : 0));
    }
    var normalized = Optional.ofNullable(associations.get(parent.node().nodeId())).flatMap(WrittenSourceAssociation::normalizedExpression)
        .orElseThrow(() -> new IllegalArgumentException("An execution occurrence requires its exact or explicitly normalized parent"));
    if (!(normalized.compilerAnchor() instanceof CompilerExpressionAnchor.Execution anchor)) {
      throw new IllegalArgumentException("An execution operand cannot borrow an annotation source address");
    }
    var association = Objects.requireNonNull(associations.get(node.nodeId()), "intact operand source owner");
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

  private record Parent(SyntaxNode node, SyntaxRole role, int position) {
  }
}
