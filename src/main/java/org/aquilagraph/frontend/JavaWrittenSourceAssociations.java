package org.aquilagraph.frontend;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.jspecify.annotations.Nullable;

/** Collects copied parsed ownership at the actual source-node creating sites. */
final class JavaWrittenSourceAssociations {
  private final ParsedTreeInventory inventory;
  private final Map<InstanceKey, WrittenSourceAssociation> values = new HashMap<>();

  JavaWrittenSourceAssociations(ParsedTreeInventory inventory) {
    this.inventory = Objects.requireNonNull(inventory, "inventory");
  }

  SyntaxNode created(SyntaxNode node, TreePath path) {
    inventory.sourceAssociation(node, path).ifPresent(value -> retain(node, value));
    return node;
  }

  SyntaxNode created(SyntaxNode node, Tree tree) {
    inventory.sourceAssociation(node, tree).ifPresent(value -> retain(node, value));
    return node;
  }

  void retain(SyntaxNode node, WrittenSourceAssociation value) {
    if (!node.nodeId().equals(value.nodeKey())) {
      throw new IllegalArgumentException("A parsed ownership copy must select its actual creating node");
    }
    var previous = values.putIfAbsent(new InstanceKey(node), value);
    if (previous != null && !previous.equals(value)) {
      throw new IllegalArgumentException("A source node has contradictory parsed ownership: " + value.nodeKey());
    }
  }

  SyntaxNode copied(SyntaxNode original, SyntaxNode copy) {
    var value = associationFor(original);
    if (value != null) {
      retain(copy,
          new WrittenSourceAssociation(copy.nodeId(), value.sourceFileKey(), value.ownerPath(), value.sourceKind(), value.origin()));
    }
    return copy;
  }

  List<WrittenSourceAssociation> forNodes(List<SyntaxNode> nodes) {
    var published = new LinkedHashMap<String, WrittenSourceAssociation>();
    for (var node : nodes) {
      var value = associationFor(node);
      if (value != null) {
        var previous = published.putIfAbsent(node.nodeId(), value);
        if (previous != null && !previous.equals(value)) {
          throw new IllegalArgumentException("Published source nodes have contradictory parsed ownership: " + node.nodeId());
        }
      }
    }
    return List.copyOf(published.values());
  }

  private @Nullable WrittenSourceAssociation associationFor(SyntaxNode node) {
    return values.get(new InstanceKey(node));
  }

  private record InstanceKey(SyntaxNode node) {
    @Override
    public boolean equals(@Nullable Object other) {
      return other instanceof InstanceKey key && JavaProjectionIdentity.sameInstance(node, key.node);
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(node);
    }
  }
}
