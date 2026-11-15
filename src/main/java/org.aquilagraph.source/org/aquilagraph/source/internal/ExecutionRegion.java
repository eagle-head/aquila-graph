package org.aquilagraph.source.internal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.SourceExecutionTraversal;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxNodeKey;

/** The exact tree, executable decisions and compiler addresses registered for a single execution. */
record ExecutionRegion(ExecutionRegistration registration, List<SyntaxNode> descendants, Map<String, SyntaxNode> nodes,
    Map<String, AttributedBodyOccurrence> addresses) {
  static ExecutionRegion resolve(ExecutionRegistration registration, Set<SyntaxNodeKey> compilerAddresses) {
    var facts = registration.facts();
    var descendants = facts.root().descendantsAndSelf();
    var nodes = SourceExecutionTraversal.nodes(facts.root()).stream()
        .collect(Collectors.toUnmodifiableMap(SyntaxNode::nodeId, node -> node));
    var addresses = new HashMap<String, AttributedBodyOccurrence>();
    var scopeAddresses = new HashSet<SyntaxNodeKey>();
    for (var occurrence : facts.attributedOccurrences()) {
      var node = nodes.get(occurrence.nodeKey());
      if (!facts.scope().key().equals(occurrence.scopeKey()) || node == null || !node.sourceFileKey().equals(occurrence.sourceFileKey())
          || node.sourceKind() != occurrence.sourceKind() || addresses.putIfAbsent(occurrence.nodeKey(), occurrence) != null
          || !scopeAddresses.add(SyntaxNodeKey.from(occurrence.sourceFileKey(), occurrence.compilerPath(), occurrence.sourceKind()))) {
        throw new IllegalArgumentException("Attributed body addresses require unique exact owned occurrences and source kinds");
      }
    }
    for (var address : scopeAddresses) {
      if (!compilerAddresses.add(address)) {
        throw new IllegalArgumentException("An attributed compiler address cannot belong to multiple execution occurrences");
      }
    }
    return new ExecutionRegion(registration, descendants, nodes, Map.copyOf(addresses));
  }

  SourceExecutionFacts facts() {
    return registration.facts();
  }
}
