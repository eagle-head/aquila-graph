package org.aquilagraph.frontend;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.frontend.JavaProjectionIdentity.Occurrence;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.jspecify.annotations.Nullable;

/** Collects copied parsed ownership at the actual source-node creating sites. */
final class JavaWrittenSourceAssociations {
  private final ParsedTreeInventory inventory;
  private final Optional<JavaWrittenSourceAssociations> parent;
  private final Map<Occurrence, WrittenSourceAssociation> values = new HashMap<>();

  JavaWrittenSourceAssociations(ParsedTreeInventory inventory) {
    this.inventory = Objects.requireNonNull(inventory, "inventory");
    parent = Optional.empty();
  }

  private JavaWrittenSourceAssociations(JavaWrittenSourceAssociations parent) {
    inventory = parent.inventory;
    this.parent = Optional.of(parent);
  }

  JavaWrittenSourceAssociations child() {
    return new JavaWrittenSourceAssociations(this);
  }

  void accept() {
    var owner = parent.orElseThrow();
    values.forEach((key, value) -> owner.retain(key.node(), value));
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
    var previous = values.putIfAbsent(new Occurrence(node), value);
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
    var value = values.get(new Occurrence(node));
    return value != null ? value : parent.map(owner -> owner.associationFor(node)).orElse(null);
  }

}
