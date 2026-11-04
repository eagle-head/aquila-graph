package org.aquilagraph.repository.neo4j;

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.port.RepositoryOperationException;

/** Exact Neo4j failure evidence with its adapter-owned creating phase. */
public final class Neo4jRepositoryOperationException extends RepositoryOperationException {
  @Serial
  private static final long serialVersionUID = 1L;

  /** Adapter phase that observed the failure. */
  private final Neo4jRepositoryPhase failurePhase;

  /**
   * Retains the original effective primary and the adapter's observed operation state.
   *
   * @param context
   *          exact operation
   * @param phase
   *          creating boundary
   * @param knowledge
   *          authoritative replacement observation
   * @param primary
   *          effective runtime primary
   */
  public Neo4jRepositoryOperationException(RepositoryOperationContext context, Neo4jRepositoryPhase phase, CommitKnowledge knowledge,
      RuntimeException primary) {
    super(context, Optional.of(knowledge), primary);
    failurePhase = Objects.requireNonNull(phase, "phase");
  }

  /**
   * Returns the adapter boundary that observed the failure.
   *
   * @return Neo4j creating phase
   */
  public Neo4jRepositoryPhase phase() {
    return failurePhase;
  }
}
