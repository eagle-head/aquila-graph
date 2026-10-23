package org.aquilagraph.cfg.internal;

import java.util.List;
import org.aquilagraph.identity.api.SourceSpan;

/** Node owned by a method control-flow graph. */
sealed interface DraftNode permits DraftEntry, DraftOperation, DraftExit {
  /**
   * Returns the stable node identifier.
   *
   * @return node identifier
   */
  String nodeId();

  /**
   * Returns the owning graph identifier.
   *
   * @return graph identifier
   */
  String graphId();

  /**
   * Returns source locations contributing to this node.
   *
   * @return immutable source origins
   */
  List<SourceSpan> origins();
}
