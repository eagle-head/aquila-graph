package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxStructuralEdge;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;

/** Exact source occurrences and canonical structural order of the captured graph. */
record SourceTopology(List<SyntaxNode> roots, List<SyntaxNode> orderedNodes, Map<String, SyntaxNode> nodes,
    OccurrenceIndex<SyntaxNode> parents, List<SyntaxStructuralEdge> structuralEdges, List<SyntaxSemanticEdge> semanticEdges) {
  private static final Comparator<SyntaxStructuralEdge> CHILD_ORDER = Comparator
      .comparing((SyntaxStructuralEdge edge) -> edge.role().stableId()).thenComparingInt(SyntaxStructuralEdge::position)
      .thenComparing(SyntaxStructuralEdge::childKey);

  SourceTopology {
    roots = List.copyOf(roots);
    orderedNodes = List.copyOf(orderedNodes);
    nodes = Map.copyOf(nodes);
    structuralEdges = List.copyOf(structuralEdges);
    semanticEdges = List.copyOf(semanticEdges);
  }

  static SourceTopology resolve(InputSnapshot snapshot, Map<String, SyntaxSourceContribution> contributions) {
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
        validateNode(node, source);
        if (nodes.putIfAbsent(node.nodeId(), node) != null) {
          throw new IllegalArgumentException("Duplicate AST node key: " + node.nodeId());
        }
      }
      structural.addAll(contribution.structuralEdges());
      semantic.addAll(contribution.semanticEdges());
    }
    validateContributionEdgeOwners(contributions, nodes);
    validateRoots(roots, nodes);
    var outgoing = validateStructuralEdges(structural, nodes, roots);
    var orderedNodes = orderNodes(roots, nodes, outgoing);
    var parents = validateEmbeddedChildren(nodes, outgoing);
    var orderedStructural = new ArrayList<SyntaxStructuralEdge>();
    for (var node : orderedNodes) {
      orderedStructural.addAll(outgoing.getOrDefault(node.nodeId(), List.of()));
    }
    return new SourceTopology(roots, orderedNodes, nodes, parents, orderedStructural, semantic);
  }

  private static void validateContributionEdgeOwners(Map<String, SyntaxSourceContribution> contributions, Map<String, SyntaxNode> nodes) {
    for (var contribution : contributions.values()) {
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
        || root.syntaxKind() != SyntaxKind.COMPILATION_UNIT || !(root.value() instanceof SyntaxValue.CompilationUnit)
        || !root.structuralPath().equals(SyntaxStructuralPath.sourceFileRoot())) {
      throw new IllegalArgumentException(
          "AST source root must be a compilation unit for source " + contribution.sourceFileKey() + ": " + root.nodeId());
    }
  }

  private static void validateNode(SyntaxNode node, SourceFile source) {
    Objects.requireNonNull(node, "node");
    if (!node.sourceFileKey().equals(source.sourceFileKey())) {
      throw new IllegalArgumentException("AST node source is not owned by contribution " + source.sourceFileKey() + ": " + node.nodeId()
          + " belongs to " + node.sourceFileKey());
    }
    var range = node.origin().orElseThrow(() -> new IllegalArgumentException("AST node lacks a required source range: " + node.nodeId()));
    if (node.value() instanceof SyntaxValue.CompilationUnit unit && unit.range() == SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE
        && (range.startOffset() != 0 || range.endOffsetExclusive() != source.content().length())) {
      throw new IllegalArgumentException("A captured source envelope must cover the exact complete source file");
    }
    if (!range.sourceFileKey().equals(source.sourceFileKey()) || range.startOffset() < 0 || range.endOffsetExclusive() < range.startOffset()
        || range.endOffsetExclusive() > source.content().length()) {
      throw new IllegalArgumentException("AST source range is outside captured source " + source.sourceFileKey() + " for node "
          + node.nodeId() + ": [" + range.startOffset() + ", " + range.endOffsetExclusive() + ") of " + source.content().length());
    }
  }

  private static void validateRoots(List<SyntaxNode> roots, Map<String, SyntaxNode> nodes) {
    for (var root : roots) {
      if (!OccurrenceIndex.same(root, nodes.get(root.nodeId()))) {
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
      var parent = Objects.requireNonNull(nodes.get(edge.parentKey()), "structural parent");
      var child = Objects.requireNonNull(nodes.get(edge.childKey()), "structural child");
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

  private static OccurrenceIndex<SyntaxNode> validateEmbeddedChildren(Map<String, SyntaxNode> nodes,
      Map<String, List<SyntaxStructuralEdge>> outgoing) {
    var parents = new OccurrenceIndex.Builder<SyntaxNode>();
    for (var node : nodes.values()) {
      var expected = node.structuralEdges().stream().sorted(CHILD_ORDER).toList();
      var actual = outgoing.getOrDefault(node.nodeId(), List.of());
      if (!expected.equals(actual)) {
        throw new IllegalArgumentException("AST structural edges disagree with owned node children: " + node.nodeId());
      }
      for (var child : node.children()) {
        if (!OccurrenceIndex.same(child.node(), nodes.get(child.node().nodeId()))) {
          throw new IllegalArgumentException("AST child is not the identical owned node: " + child.node().nodeId());
        }
        parents.putIfAbsent(child.node(), node);
      }
    }
    return parents.freeze();
  }

  private static Set<Integer> positionsFor(Map<String, Map<SyntaxRole, Set<Integer>>> rolePositions, String parentKey, SyntaxRole role) {
    return rolePositions.computeIfAbsent(parentKey, ignored -> new EnumMap<>(SyntaxRole.class)).computeIfAbsent(role,
        ignored -> new HashSet<>());
  }

  private static List<SyntaxStructuralEdge> outgoingFor(Map<String, List<SyntaxStructuralEdge>> outgoing, String parentKey) {
    return outgoing.computeIfAbsent(parentKey, ignored -> new ArrayList<>());
  }

}
