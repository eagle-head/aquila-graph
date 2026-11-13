package org.aquilagraph.frontend.internal;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import org.aquilagraph.frontend.internal.JavaExecutableSyntaxProfile.CompilerOperand;
import org.aquilagraph.frontend.internal.JavaExecutableSyntaxProjector.NodeProjection;
import org.aquilagraph.frontend.internal.JavaExecutableSyntaxProjector.Projection;
import org.aquilagraph.frontend.internal.JavaWrittenExpressionProjector.Evaluated;
import org.aquilagraph.source.api.CompilerExpressionAnchor;
import org.aquilagraph.source.api.NormalizedExpressionCorrespondence;
import org.aquilagraph.source.api.SourceExecutionTraversal;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenExpressionRegion;
import org.aquilagraph.source.api.WrittenSourceAssociation;

/** Validates and retains exact node bindings within one live executable projection. */
final class JavaExecutableProjectionProof {
  private static final String DERIVED_ERROR = "A derived occurrence requires its actual compiler-region and written topology proof";

  private final Map<SyntaxNode, NodeProjection> bindings;

  private JavaExecutableProjectionProof(Map<SyntaxNode, NodeProjection> bindings) {
    this.bindings = Collections.unmodifiableMap(bindings);
  }

  static JavaExecutableProjectionProof corroborate(Projection projection, SemanticResolver semantics) {
    return corroborate(projection, projection.nodes(), semantics);
  }

  static JavaExecutableProjectionProof corroborate(Projection projection, List<NodeProjection> projections, SemanticResolver semantics) {
    projection.requireSemantics(semantics);
    var profile = new JavaExecutableSyntaxProfile(semantics, projection.context());
    var root = projection.translation().value();
    var compilerRoot = projection.compilerRoot();
    var source = projection.source();
    if (!root.structuralPath().equals(JavaSyntaxStructuralPath.executionRoot(compilerRoot))
        || !root.sourceFileKey().equals(source.sourceFileKey())) {
      throw new IllegalArgumentException("An executable occurrence requires its exact compiler root and source scope");
    }
    var inventory = projection.inventory();
    var bindings = new IdentityHashMap<SyntaxNode, NodeProjection>();
    for (var entry : projections) {
      if (bindings.put(entry.node(), entry) != null) {
        throw new IllegalArgumentException("One produced body instance cannot have two compiler projections");
      }
    }
    var nodes = SourceExecutionTraversal.nodes(root);
    if (nodes.size() != bindings.size()) {
      throw new IllegalArgumentException("The final registered body must retain exactly its produced compiler projection");
    }
    var visited = Collections.newSetFromMap(new IdentityHashMap<SyntaxNode, Boolean>());
    for (var node : nodes) {
      var binding = bindings.get(node);
      if (!visited.add(node)) {
        throw new IllegalArgumentException("A produced body instance must belong to one executable occurrence");
      }
      if (binding == null && node.syntaxKind() == SyntaxKind.TYPE_DECLARATION) {
        throw new IllegalArgumentException("A nested declaration boundary requires its exact compiler path");
      }
      if (binding == null) {
        throw new IllegalArgumentException("A final body node requires its exact attributed source projection");
      }
      if (!within(binding.compilerAnchor(), compilerRoot) || !root.sourceFileKey().equals(node.sourceFileKey())
          || (JavaProjectionIdentity.sameInstance(node, root)
              && (!JavaProjectionIdentity.sameInstance(binding.compilerAnchor(), compilerRoot)
                  || !binding.compilerPath().equals(root.structuralPath())))) {
        throw new IllegalArgumentException("An executable occurrence requires its exact compiler root and source scope");
      }
      if (node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
          || (projection.context().declarations().isPresent() && binding.compilerAnchor().getLeaf() instanceof ClassTree)) {
        corroborateDeclaration(binding, inventory, semantics);
        continue;
      }
      if (binding.derivation().isPresent()) {
        if (JavaProjectionIdentity.sameInstance(node, root) && !correspondence(binding).occurrencePath().isEmpty()) {
          throw derivedMismatch();
        }
        corroborateRegion(binding, bindings, inventory, profile, semantics);
      } else {
        requireDirectSource(binding, inventory);
        if (inventory.writtenExpression(binding.compilerAnchor()).isPresent()) {
          throw derivedMismatch();
        }
        corroborateDirect(binding, bindings, profile, semantics);
      }
    }
    return new JavaExecutableProjectionProof(bindings);
  }

  NodeProjection binding(SyntaxNode node) {
    var binding = bindings.get(node);
    if (binding == null) {
      throw new IllegalArgumentException("An executable node must belong to its producing projection");
    }
    return binding;
  }

  private static void corroborateRegion(NodeProjection binding, Map<SyntaxNode, NodeProjection> bindings, ParsedTreeInventory inventory,
      JavaExecutableSyntaxProfile profile, SemanticResolver semantics) {
    var supplied = correspondence(binding);
    if (!supplied.occurrencePath().isEmpty()) {
      return;
    }
    var anchor = binding.compilerAnchor();
    var expression = inventory.writtenExpression(anchor).orElseThrow(JavaExecutableProjectionProof::derivedMismatch);
    var region = expression.region().orElseThrow(JavaExecutableProjectionProof::derivedMismatch);
    var evaluator = new JavaWrittenExpressionProjector(semantics, inventory);
    var evaluated = evaluator.evaluate(expression).orElseThrow(JavaExecutableProjectionProof::derivedMismatch);
    if (evaluator.compilerContradiction(expression, evaluated).isPresent()) {
      throw derivedMismatch();
    }
    var proof = new RegionProof(binding, region, inventory.expressionObservations(expression, semantics),
        profile.compilerOperands(anchor, binding.compilerPath()), bindings, semantics);
    if (!region.matches(binding.node())) {
      throw derivedMismatch();
    }
    proof.corroborate(binding, evaluated, List.of());
  }

  private static NormalizedExpressionCorrespondence correspondence(NodeProjection binding) {
    return binding.sourceAssociation().flatMap(WrittenSourceAssociation::normalizedExpression)
        .orElseThrow(JavaExecutableProjectionProof::derivedMismatch);
  }

  private static IllegalArgumentException derivedMismatch() {
    return new IllegalArgumentException(DERIVED_ERROR);
  }

  private record RegionProof(NodeProjection root, WrittenExpressionRegion region,
      List<NormalizedExpressionCorrespondence.Observation> observations, Map<Tree, CompilerOperand> operands,
      Map<SyntaxNode, NodeProjection> bindings, SemanticResolver semantics) {
    private void corroborate(NodeProjection binding, Evaluated evaluated, List<SyntaxStructuralPath.Step> path) {
      if (evaluated.written() instanceof JavaWrittenExpression.Anchor anchor) {
        var expected = operands.get(anchor.path().getLeaf());
        if (expected == null || binding.derivation().isPresent() || !expected.address().equals(binding.compilerPath())
            || !JavaProjectionIdentity.sameTreePath(expected.path(), binding.compilerAnchor())
            || !JavaProjectionIdentity.sameTreePath(anchor.path(), binding.compilerAnchor())) {
          throw derivedMismatch();
        }
        return;
      }
      var node = binding.node();
      var association = binding.sourceAssociation().orElseThrow(JavaExecutableProjectionProof::derivedMismatch);
      var supplied = correspondence(binding);
      var facts = binding.derivation().orElseThrow(JavaExecutableProjectionProof::derivedMismatch);
      if (!matchesSourceOccurrence(node, association) || !node.value().equals(evaluated.executionValue())
          || node.syntaxKind() != evaluated.syntaxKind() || node.semanticReference().isPresent()
          || !semantics.declarationType(evaluated.type()).equals(node.type()) || !semantics.sameType(facts.type(), evaluated.type())
          || !binding.children().isEmpty() || !facts.constant().equals(evaluated.constant())
          || !facts.constantStatus().equals(evaluated.constantStatus())
          || !supplied.derivedConstant().equals(evaluated.constant().flatMap(JavaConstantExpression::copyScalar))
          || !supplied.derivedConstantExpression().equals(evaluated.constantStatus()) || !supplied.observations().equals(observations)
          || !supplied.region().equals(region) || !supplied.occurrencePath().equals(path)
          || !(supplied.compilerAnchor() instanceof CompilerExpressionAnchor.Execution execution)
          || !root.compilerPath().equals(execution.path()) || !root.compilerPath().equals(binding.compilerPath())
          || !JavaProjectionIdentity.sameInstance(root.compilerAnchor(), binding.compilerAnchor())
          || !root.compilerAnchor().getLeaf().getKind().name().equals(supplied.compilerKind().name())
          || !semantics.type(root.compilerAnchor()).equals(supplied.compilerType())
          || node.children().size() != evaluated.children().size()) {
        throw derivedMismatch();
      }
      var roles = evaluated.childRoles();
      for (var index = 0; index < node.children().size(); index++) {
        var child = node.children().get(index);
        var role = roles.get(index);
        var childBinding = bindings.get(child.node());
        if (childBinding == null || child.role() != role || child.position() != 0
            || !child.node().structuralPath().equals(node.structuralPath().child(role, 0))) {
          throw derivedMismatch();
        }
        corroborate(childBinding, evaluated.children().get(index), JavaWrittenExpression.child(path, role));
      }
    }
  }

  private static void requireDirectSource(NodeProjection binding, ParsedTreeInventory inventory) {
    var node = binding.node();
    var captured = inventory.sourceAssociation(node, binding.compilerAnchor());
    if (!binding.sourceAssociation().equals(captured)) {
      throw new IllegalArgumentException("A direct occurrence requires its exact parsed source association");
    }
    // An unavailable direct position remains eligible for attributed-only retention and diagnostics.
    if (node.origin().isPresent() && captured.filter(association -> matchesSourceOccurrence(node, association)).isEmpty()) {
      throw new IllegalArgumentException("A direct occurrence requires its exact captured source occurrence");
    }
  }

  private static boolean matchesSourceOccurrence(SyntaxNode node, WrittenSourceAssociation association) {
    return association.nodeKey().equals(node.nodeId()) && association.sourceFileKey().equals(node.sourceFileKey())
        && association.sourceKind() == node.sourceKind() && association.origin().equals(node.origin());
  }

  private static void corroborateDeclaration(NodeProjection binding, ParsedTreeInventory inventory, SemanticResolver semantics) {
    var node = binding.node();
    var anchor = binding.compilerAnchor();
    var captured = inventory.sourceAssociation(node, anchor);
    if (!(anchor.getLeaf() instanceof ClassTree declaration) || !(node.value() instanceof SyntaxValue.TypeDeclaration value)
        || node.syntaxKind() != SyntaxKind.TYPE_DECLARATION || node.type() != SyntaxType.NotApplicable.INSTANCE
        || !(semantics.element(anchor) instanceof TypeElement type)
        || !semantics.declarationKey(type).equals(Optional.of(value.declarationKey()))
        || !declaration.getSimpleName().contentEquals(value.name()) || binding.derivation().isPresent() || !binding.children().isEmpty()
        || captured.isEmpty() || !captured.equals(binding.sourceAssociation())) {
      throw new IllegalArgumentException("A nested declaration boundary requires its exact compiler path and declaration identity");
    }
    if (!matchesSourceOccurrence(node, captured.orElseThrow())) {
      throw new IllegalArgumentException("A nested declaration boundary requires its exact captured source occurrence");
    }
  }

  private static void corroborateDirect(NodeProjection binding, Map<SyntaxNode, NodeProjection> bindings,
      JavaExecutableSyntaxProfile profile, SemanticResolver semantics) {
    var node = binding.node();
    var anchor = binding.compilerAnchor();
    var mapping = profile.describe(anchor.getLeaf(), anchor, binding.compilerPath());
    var selected = mapping.children();
    if (!anchor.getLeaf().getKind().name().equals(node.sourceKind().name()) || !semantics.type(anchor).equals(node.type())
        || node.syntaxKind() != mapping.kind() || !node.semanticReference().equals(mapping.semanticReference())
        || binding.children().size() != node.children().size() || selected.size() != binding.children().size()) {
      throw new IllegalArgumentException("A final body node requires its exact attributed source projection");
    }
    for (var index = 0; index < node.children().size(); index++) {
      var child = node.children().get(index);
      var supplied = binding.children().get(index);
      var expected = selected.get(index);
      var childBinding = bindings.get(child.node());
      if (childBinding == null || supplied.role() != child.role() || supplied.position() != child.position()
          || !child.node().structuralPath().equals(node.structuralPath().child(child.role(), child.position()))
          || supplied.role() != expected.role() || supplied.position() != expected.position()
          || !supplied.structuralPath().equals(expected.structuralPath()) || !supplied.structuralPath().equals(childBinding.compilerPath())
          || !JavaProjectionIdentity.sameInstance(supplied.treePath(), childBinding.compilerAnchor())
          || !JavaProjectionIdentity.sameInstance(supplied.tree(), expected.tree())
          || !JavaProjectionIdentity.sameTreePath(supplied.treePath(), expected.treePath()) || childBinding.sourceAssociation()
              .flatMap(WrittenSourceAssociation::normalizedExpression).filter(value -> !value.occurrencePath().isEmpty()).isPresent()) {
        throw new IllegalArgumentException("A selected child use requires its exact direct child and compiler path");
      }
    }
  }

  private static boolean within(TreePath path, TreePath ancestor) {
    for (var cursor = path; cursor != null; cursor = cursor.getParentPath()) {
      if (JavaProjectionIdentity.sameInstance(cursor, ancestor)) {
        return true;
      }
    }
    return false;
  }
}
