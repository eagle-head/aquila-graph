package org.aquilagraph.source.internal;

import java.util.ArrayList;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.internal.AstValueSchema.Container;
import org.aquilagraph.source.internal.AstValueSchema.Fields;
import org.aquilagraph.source.internal.AstValueSchema.Scalar;
import org.aquilagraph.source.retention.AstValueProjection;
import org.aquilagraph.source.retention.AstValueProjection.Link;
import org.aquilagraph.source.retention.AstValueProjection.Node;

/** Materializes all retained fields while preserving shared source occurrences. */
public final class AstValueProjector {
  private final List<Node> nodes = new ArrayList<>();
  private final List<Link> links = new ArrayList<>();
  private final Function<SyntaxNode, Optional<Integer>> sourceOrdinal;
  private final BiConsumer<SyntaxNode, Integer> rememberSource;

  /** Initializes the graph and its occurrence tracking for one projection. */
  public AstValueProjector() {
    var occurrences = new IdentityHashMap<SyntaxNode, Integer>();
    sourceOrdinal = node -> Optional.ofNullable(occurrences.get(node));
    rememberSource = occurrences::put;
  }

  /**
   * Projects a complete retained value graph.
   *
   * @param value
   *          root copied value
   * @return immutable value graph
   */
  public AstValueProjection project(Object value) {
    append(value);
    return new AstValueProjection(nodes, links);
  }

  private int append(Object value) {
    var shape = AstValueSchema.shape(value);
    var ordinal = nodes.size();
    switch (shape) {
      case Scalar scalar -> {
        var encoded = Base64.getEncoder().encodeToString(RetainedValueCodec.valueBytes(value, false));
        nodes.add(new Node(ordinal, scalar.tag(), Optional.of(encoded), Optional.empty()));
      }
      case Container container -> {
        nodes.add(new Node(ordinal, container.tag(), Optional.empty(), Optional.empty()));
        var members = container.members(value, set -> RetainedValueCodec.orderedSet(set, false));
        for (var position = 0; position < members.size(); position++) {
          link(ordinal, "item", position, members.get(position));
        }
      }
      case Fields fields -> {
        return appendFields(value, fields, ordinal);
      }
    }
    return ordinal;
  }

  private int appendFields(Object value, Fields fields, int ordinal) {
    var sourceKey = Optional.<String>empty();
    if (fields.occurrence()) {
      var source = RetainedValueCodec.required(value, SyntaxNode.class);
      var previous = sourceOrdinal.apply(source);
      if (previous.isPresent()) {
        return previous.orElseThrow();
      }
      rememberSource.accept(source, ordinal);
      sourceKey = Optional.of(source.nodeId());
    }
    nodes.add(new Node(ordinal, fields.tag(), Optional.empty(), sourceKey));
    var components = fields.components(value, false);
    for (var position = 0; position < components.size(); position++) {
      var component = components.get(position);
      link(ordinal, component.name(), position, component.value());
    }
    return ordinal;
  }

  private void link(int owner, String field, int position, Object value) {
    links.add(new Link(owner, append(value), field, position));
  }
}
