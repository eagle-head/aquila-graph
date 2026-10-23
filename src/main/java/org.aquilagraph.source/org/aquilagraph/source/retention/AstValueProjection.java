package org.aquilagraph.source.retention;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, ordered materialization of the closed retained source value schema.
 *
 * @param nodes
 *          every tagged value in encounter order
 * @param links
 *          every exact named component and ordered position
 */
public record AstValueProjection(List<Node> nodes, List<Link> links) {
  /** Copies the complete materialization without changing field or occurrence order. */
  public AstValueProjection {
    nodes = List.copyOf(nodes);
    links = List.copyOf(links);
  }

  /**
   * One tagged value occurrence, with lossless scalar bytes or a canonical source key.
   *
   * @param ordinal
   *          value encounter slot
   * @param tag
   *          closed source format tag
   * @param scalarEncoding
   *          full tagged scalar encoding, when scalar
   * @param sourceNodeKey
   *          canonical source occurrence identity, when a source node
   */
  public record Node(int ordinal, String tag, Optional<String> scalarEncoding, Optional<String> sourceNodeKey) {
    /** Requires a tagged nonnegative encounter slot. */
    public Node {
      Objects.requireNonNull(tag, "tag");
      Objects.requireNonNull(scalarEncoding, "scalarEncoding");
      Objects.requireNonNull(sourceNodeKey, "sourceNodeKey");
      if (ordinal < 0 || tag.isBlank()) {
        throw new IllegalArgumentException("A retained value requires an encounter slot and closed tag");
      }
    }
  }

  /**
   * One exact component link; list positions and repeated occurrences are retained.
   *
   * @param sourceOrdinal
   *          owning value encounter slot
   * @param targetOrdinal
   *          component value encounter slot
   * @param field
   *          exact closed-schema component name
   * @param position
   *          ordered slot within the owning value
   */
  public record Link(int sourceOrdinal, int targetOrdinal, String field, int position) {
    /** Requires an explicit field and nonnegative ordered slots. */
    public Link {
      Objects.requireNonNull(field, "field");
      if (sourceOrdinal < 0 || targetOrdinal < 0 || position < 0 || field.isBlank()) {
        throw new IllegalArgumentException("A retained component requires exact ordered encounter slots");
      }
    }
  }
}
