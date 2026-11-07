package org.aquilagraph.cli;

import java.util.Optional;
import org.aquilagraph.application.port.RepositoryOperationException;

/** Supplies public adapter observation detail without coupling core reporting to repository types. */
@FunctionalInterface
public interface FailureDetailProjector {
  /**
   * Copies the detail declared by this exact carrier, if recognized.
   *
   * @param failure
   *          original operation carrier
   * @return detached adapter observation, or explicit absence
   */
  Optional<AdapterFailureDetail> project(RepositoryOperationException failure);
}
