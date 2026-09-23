package org.aquilagraph.syntax;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;

/** Package-local validator for one captured source AST. */
final class JavaAstAggregateBuilder {
  private static final Comparator<SyntaxStructuralEdge> CHILD_ORDER = Comparator
      .comparing((SyntaxStructuralEdge edge) -> edge.role().stableId()).thenComparingInt(SyntaxStructuralEdge::position)
      .thenComparing(SyntaxStructuralEdge::childKey);
  private static final Comparator<SyntaxSemanticEdge> SEMANTIC_ORDER = Comparator.comparing(SyntaxSemanticEdge::sourceKey)
      .thenComparing(edge -> edge.relation().stableId()).thenComparing(JavaAstAggregateBuilder::compareTargets);

  private final InputSnapshot snapshot;
  private final Map<String, SourceFile> sources = new HashMap<>();
  private final Map<String, SyntaxSourceContribution> contributions = new HashMap<>();
  private boolean sealed;

  JavaAstAggregateBuilder(InputSnapshot inputSnapshot) {
    snapshot = Objects.requireNonNull(inputSnapshot, "inputSnapshot");
    for (var source : snapshot.sourceFiles()) {
      sources.put(source.sourceFileKey(), source);
    }
  }

  JavaAstAggregateBuilder add(SyntaxSourceContribution contribution) {
    requireOpen();
    Objects.requireNonNull(contribution, "contribution");
    if (!sources.containsKey(contribution.sourceFileKey())) {
      throw new IllegalArgumentException("AST contribution source is not in the captured snapshot: " + contribution.sourceFileKey()
          + " for root node " + contribution.root().nodeId());
    }
    if (contributions.putIfAbsent(contribution.sourceFileKey(), contribution) != null) {
      throw new IllegalArgumentException("Duplicate AST source contribution: " + contribution.sourceFileKey());
    }
    return this;
  }

  ValidatedAggregate validate() {
    requireOpen();
    var nodes = new HashMap<String, SyntaxNode>();
    var roots = new ArrayList<SyntaxNode>();
    var structural = new ArrayList<SyntaxStructuralEdge>();
    var semantic = new ArrayList<SyntaxSemanticEdge>();
    for (var source : snapshot.sourceFiles()) {
      var contribution = contributions.get(source.sourceFileKey());
      if (contribution == null) {
        throw new IllegalArgumentException("Missing AST source root for captured source: " + source.sourceFileKey());
      }
      validateRoot(contribution);
      roots.add(contribution.root());
      for (var node : contribution.nodes()) {
        validateNode(node, contribution.sourceFileKey(), source);
        if (nodes.putIfAbsent(node.nodeId(), node) != null) {
          throw new IllegalArgumentException("Duplicate AST node key: " + node.nodeId());
        }
      }
      structural.addAll(contribution.structuralEdges());
      semantic.addAll(contribution.semanticEdges());
    }
    validateContributionEdgeOwners(nodes);
    validateRoots(roots, nodes);
    var outgoing = validateStructuralEdges(structural, nodes, roots);
    var orderedNodes = orderNodes(roots, nodes, outgoing);
    validateEmbeddedChildren(nodes, outgoing);
    validateSemanticEdges(semantic, nodes);
    var orderedStructural = new ArrayList<SyntaxStructuralEdge>();
    for (var node : orderedNodes) {
      orderedStructural.addAll(outgoing.getOrDefault(node.nodeId(), List.of()));
    }
    semantic.sort(SEMANTIC_ORDER);
    sealed = true;
    return new ValidatedAggregate(snapshot, roots, orderedNodes, nodes, orderedStructural, semantic);
  }

  private void validateContributionEdgeOwners(Map<String, SyntaxNode> nodes) {
    for (var capturedSource : snapshot.sourceFiles()) {
      var contribution = contributions.get(capturedSource.sourceFileKey());
      for (var edge : contribution.structuralEdges()) {
        var parent = nodes.get(edge.parentKey());
        if (parent != null && !parent.sourceFileKey().equals(contribution.sourceFileKey())) {
          throw new IllegalArgumentException("AST structural parent " + edge.parentKey() + " belongs to source " + parent.sourceFileKey()
              + ", not contribution " + contribution.sourceFileKey());
        }
      }
      for (var edge : contribution.semanticEdges()) {
        var source = nodes.get(edge.sourceKey());
        if (source != null && !source.sourceFileKey().equals(contribution.sourceFileKey())) {
          throw new IllegalArgumentException("AST semantic source " + edge.sourceKey() + " belongs to source " + source.sourceFileKey()
              + ", not contribution " + contribution.sourceFileKey());
        }
      }
    }
  }

  private static void validateRoot(SyntaxSourceContribution contribution) {
    var root = contribution.root();
    if (!root.sourceFileKey().equals(contribution.sourceFileKey()) || root.sourceKind() != JavaSourceKind.COMPILATION_UNIT
        || !root.structuralPath().equals(SyntaxStructuralPath.sourceFileRoot())) {
      throw new IllegalArgumentException(
          "AST source root must be a compilation unit for source " + contribution.sourceFileKey() + ": " + root.nodeId());
    }
  }

  private static void validateNode(SyntaxNode node, String contributionSource, SourceFile source) {
    Objects.requireNonNull(node, "node");
    if (!node.sourceFileKey().equals(contributionSource) || !node.sourceFileKey().equals(source.sourceFileKey())) {
      throw new IllegalArgumentException("AST node source is not owned by contribution " + contributionSource + ": " + node.nodeId()
          + " belongs to " + node.sourceFileKey());
    }
    var range = node.origin().orElseThrow(() -> new IllegalArgumentException("AST node lacks a required source range: " + node.nodeId()));
    if (!range.sourceFileKey().equals(source.sourceFileKey()) || range.startOffset() < 0 || range.endOffsetExclusive() < range.startOffset()
        || range.endOffsetExclusive() > source.content().length()) {
      throw new IllegalArgumentException("AST source range is outside captured source " + source.sourceFileKey() + " for node "
          + node.nodeId() + ": [" + range.startOffset() + ", " + range.endOffsetExclusive() + ") of " + source.content().length());
    }
  }

  private static void validateRoots(List<SyntaxNode> roots, Map<String, SyntaxNode> nodes) {
    var ownedInstances = ownedInstances(nodes);
    for (var root : roots) {
      if (!ownedInstances.contains(root)) {
        throw new IllegalArgumentException("AST source root is not an owned node: " + root.nodeId());
      }
    }
  }

  private static Map<String, List<SyntaxStructuralEdge>> validateStructuralEdges(List<SyntaxStructuralEdge> edges,
      Map<String, SyntaxNode> nodes, List<SyntaxNode> roots) {
    var outgoing = new HashMap<String, List<SyntaxStructuralEdge>>();
    var parentCount = new HashMap<String, Integer>();
    var seenEdges = new HashSet<SyntaxStructuralEdge>();
    Map<String, Map<SyntaxRole, Set<Integer>>> rolePositions = new HashMap<>();
    for (var edge : edges) {
      Objects.requireNonNull(edge, "structuralEdge");
      if (!seenEdges.add(edge)) {
        throw new IllegalArgumentException("Duplicate AST structural edge tuple: " + edge);
      }
      var parent = nodes.get(edge.parentKey());
      var child = nodes.get(edge.childKey());
      if (parent == null) {
        throw new IllegalArgumentException("AST edge endpoint is not owned by the aggregate: structural parent " + edge.parentKey());
      }
      if (child == null) {
        throw new IllegalArgumentException("AST edge endpoint is not owned by the aggregate: structural child " + edge.childKey());
      }
      if (!parent.sourceFileKey().equals(child.sourceFileKey())) {
        throw new IllegalArgumentException(
            "AST structural edge crosses source owners " + parent.sourceFileKey() + " and " + child.sourceFileKey() + ": " + edge);
      }
      if (!positionsFor(rolePositions, edge.parentKey(), edge.role()).add(edge.position())) {
        throw new IllegalArgumentException(
            "Duplicate AST role position for parent " + edge.parentKey() + " and role " + edge.role() + ": " + edge.position());
      }
      outgoingFor(outgoing, edge.parentKey()).add(edge);
      parentCount.merge(edge.childKey(), 1, Integer::sum);
    }
    for (var parentEntry : rolePositions.entrySet()) {
      for (var roleEntry : parentEntry.getValue().entrySet()) {
        var positions = roleEntry.getValue();
        for (var expected = 0; expected < positions.size(); expected++) {
          if (!positions.contains(expected)) {
            throw new IllegalArgumentException("AST role positions are not contiguous for parent " + parentEntry.getKey() + " and role "
                + roleEntry.getKey() + ": expected " + expected);
          }
        }
      }
    }
    outgoing.values().forEach(children -> children.sort(CHILD_ORDER));
    detectCycles(nodes, outgoing);
    var rootKeys = new HashSet<String>();
    for (var root : roots) {
      rootKeys.add(root.nodeId());
      var count = parentCount.getOrDefault(root.nodeId(), 0);
      if (count != 0) {
        throw new IllegalArgumentException("AST source root has a structural parent: " + root.nodeId() + " count " + count);
      }
    }
    for (var node : nodes.values()) {
      var count = parentCount.getOrDefault(node.nodeId(), 0);
      if (!rootKeys.contains(node.nodeId()) && count != 1) {
        throw new IllegalArgumentException("AST non-root requires exactly one structural parent: " + node.nodeId() + " count " + count);
      }
    }
    for (var edge : edges) {
      var parent = nodes.get(edge.parentKey());
      var child = nodes.get(edge.childKey());
      if (!child.structuralPath().equals(parent.structuralPath().child(edge.role(), edge.position()))) {
        throw new IllegalArgumentException("AST structural path disagrees with parent edge for node " + child.nodeId());
      }
    }
    return outgoing;
  }

  private static void detectCycles(Map<String, SyntaxNode> nodes, Map<String, List<SyntaxStructuralEdge>> outgoing) {
    var completed = new HashSet<String>();
    var active = new HashSet<String>();
    for (var key : nodes.keySet()) {
      visitCycle(key, outgoing, completed, active);
    }
  }

  private static void visitCycle(String key, Map<String, List<SyntaxStructuralEdge>> outgoing, Set<String> completed, Set<String> active) {
    if (completed.contains(key)) {
      return;
    }
    if (!active.add(key)) {
      throw new IllegalArgumentException("AST structural cycle involves node " + key);
    }
    for (var edge : outgoing.getOrDefault(key, List.of())) {
      visitCycle(edge.childKey(), outgoing, completed, active);
    }
    active.remove(key);
    completed.add(key);
  }

  private static List<SyntaxNode> orderNodes(List<SyntaxNode> roots, Map<String, SyntaxNode> nodes,
      Map<String, List<SyntaxStructuralEdge>> outgoing) {
    var ordered = new ArrayList<SyntaxNode>();
    var reached = new HashSet<String>();
    for (var root : roots) {
      visitOrdered(root.nodeId(), nodes, outgoing, reached, ordered);
    }
    if (reached.size() != nodes.size()) {
      var missing = nodes.keySet().stream().filter(key -> !reached.contains(key)).sorted().findFirst().orElseThrow();
      throw new IllegalArgumentException("AST node is not reachable from a source root: " + missing);
    }
    return ordered;
  }

  private static void visitOrdered(String key, Map<String, SyntaxNode> nodes, Map<String, List<SyntaxStructuralEdge>> outgoing,
      Set<String> reached, List<SyntaxNode> ordered) {
    if (!reached.add(key)) {
      throw new IllegalArgumentException("AST node has multiple structural paths: " + key);
    }
    ordered.add(nodes.get(key));
    for (var edge : outgoing.getOrDefault(key, List.of())) {
      visitOrdered(edge.childKey(), nodes, outgoing, reached, ordered);
    }
  }

  private static void validateEmbeddedChildren(Map<String, SyntaxNode> nodes, Map<String, List<SyntaxStructuralEdge>> outgoing) {
    var ownedInstances = ownedInstances(nodes);
    for (var node : nodes.values()) {
      var expected = node.structuralEdges().stream().sorted(CHILD_ORDER).toList();
      var actual = outgoing.getOrDefault(node.nodeId(), List.of());
      if (!expected.equals(actual)) {
        throw new IllegalArgumentException("AST structural edges disagree with owned node children: " + node.nodeId());
      }
      for (var child : node.children()) {
        if (!ownedInstances.contains(child.node())) {
          throw new IllegalArgumentException("AST child is not the identical owned node: " + child.node().nodeId());
        }
      }
    }
  }

  private static void validateSemanticEdges(List<SyntaxSemanticEdge> edges, Map<String, SyntaxNode> nodes) {
    var seen = new HashSet<SyntaxSemanticEdge>();
    for (var edge : edges) {
      Objects.requireNonNull(edge, "semanticEdge");
      if (!seen.add(edge)) {
        throw new IllegalArgumentException("Duplicate AST semantic edge tuple: " + edge);
      }
      if (!nodes.containsKey(edge.sourceKey())) {
        throw new IllegalArgumentException("AST edge endpoint is not owned by the aggregate: semantic source " + edge.sourceKey());
      }
      if (edge.target() instanceof SyntaxSemanticTarget.SourceOccurrence occurrence && !nodes.containsKey(occurrence.nodeKey())) {
        throw new IllegalArgumentException("AST edge endpoint is not owned by the aggregate: semantic target " + occurrence.nodeKey());
      }
    }
    for (var node : nodes.values()) {
      if (!seen.containsAll(node.semanticEdges())) {
        throw new IllegalArgumentException("AST semantic edges omit an owned node reference: " + node.nodeId());
      }
    }
  }

  private static int compareTargets(SyntaxSemanticEdge left, SyntaxSemanticEdge right) {
    var first = left.target();
    var second = right.target();
    if (first instanceof SyntaxSemanticTarget.SourceOccurrence source) {
      if (second instanceof SyntaxSemanticTarget.SourceOccurrence other) {
        return source.nodeKey().compareTo(other.nodeKey());
      }
      return -1;
    }
    if (second instanceof SyntaxSemanticTarget.SourceOccurrence) {
      return 1;
    }
    var leftReference = ((SyntaxSemanticTarget.Declaration) first).reference();
    var rightReference = ((SyntaxSemanticTarget.Declaration) second).reference();
    return compareReferences(leftReference, rightReference);
  }

  private static int compareReferences(SemanticReference left, SemanticReference right) {
    var result = left.referenceKind().name().compareTo(right.referenceKind().name());
    if (result == 0) {
      result = left.status().name().compareTo(right.status().name());
    }
    if (result == 0) {
      result = compareOptional(left.symbolKey(), right.symbolKey());
    }
    if (result == 0) {
      result = compareOptional(left.typeName(), right.typeName());
    }
    if (result == 0) {
      result = compareStrings(left.typeAlternativeNames(), right.typeAlternativeNames());
    }
    return result == 0 ? compareStrings(left.assignableToTypeNames(), right.assignableToTypeNames()) : result;
  }

  private static int compareOptional(Optional<String> left, Optional<String> right) {
    if (left.isEmpty()) {
      return right.isEmpty() ? 0 : -1;
    }
    return right.isEmpty() ? 1 : left.orElseThrow().compareTo(right.orElseThrow());
  }

  private static int compareStrings(List<String> left, List<String> right) {
    for (var index = 0; index < Math.min(left.size(), right.size()); index++) {
      var result = left.get(index).compareTo(right.get(index));
      if (result != 0) {
        return result;
      }
    }
    return Integer.compare(left.size(), right.size());
  }

  private void requireOpen() {
    if (sealed) {
      throw new IllegalStateException("AST aggregate construction is already sealed");
    }
  }

  private static Set<SyntaxNode> ownedInstances(Map<String, SyntaxNode> nodes) {
    var instances = Collections.newSetFromMap(new IdentityHashMap<SyntaxNode, Boolean>());
    instances.addAll(nodes.values());
    return instances;
  }

  private static Set<Integer> positionsFor(Map<String, Map<SyntaxRole, Set<Integer>>> rolePositions, String parentKey, SyntaxRole role) {
    return rolePositions.computeIfAbsent(parentKey, ignored -> new EnumMap<>(SyntaxRole.class)).computeIfAbsent(role,
        ignored -> new HashSet<>());
  }

  private static List<SyntaxStructuralEdge> outgoingFor(Map<String, List<SyntaxStructuralEdge>> outgoing, String parentKey) {
    return outgoing.computeIfAbsent(parentKey, ignored -> new ArrayList<>());
  }

  record ValidatedAggregate(InputSnapshot snapshot, List<SyntaxNode> roots, List<SyntaxNode> nodes, Map<String, SyntaxNode> ownedNodes,
      List<SyntaxStructuralEdge> structuralEdges, List<SyntaxSemanticEdge> semanticEdges) {
  }
}
