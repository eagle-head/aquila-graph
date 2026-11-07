package org.aquilagraph;

import java.util.Optional;
import org.aquilagraph.application.port.RepositoryOperationException;
import org.aquilagraph.cli.AdapterFailureDetail;
import org.aquilagraph.cli.FailureDetailProjector;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;

/** Copies the public Neo4j phase without changing application failure classification. */
final class Neo4jFailureDetailProjector implements FailureDetailProjector {
  @Override
  public Optional<AdapterFailureDetail> project(RepositoryOperationException failure) {
    return failure instanceof Neo4jRepositoryOperationException neo4j
        ? Optional.of(new AdapterFailureDetail("Neo4j", neo4j.phase().name()))
        : Optional.empty();
  }
}
