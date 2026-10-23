package org.aquilagraph.repository.neo4j;

import java.util.function.Supplier;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.AstResultRepositoryFactory;
import org.aquilagraph.repository.internal.neo4j.Neo4jAnalysisRunRepository;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstResultRepositoryFactory;

/** Independent composition of the actual Neo4j repository ports. */
public final class Neo4jRepositories {
  private Neo4jRepositories() {
  }

  /**
   * Creates lazy acquisition for exact retained-AST operations.
   *
   * @param settings
   *          deferred connection settings
   * @return operation-scoped repository factory
   */
  public static AstResultRepositoryFactory astResults(Supplier<Neo4jConnectionSettings> settings) {
    return new Neo4jAstResultRepositoryFactory(settings);
  }

  /**
   * Opens storage for completed analysis runs.
   *
   * @param settings
   *          validated connection settings
   * @return owned repository, which the caller closes
   */
  public static AnalysisRunRepository completedRuns(Neo4jConnectionSettings settings) {
    return new Neo4jAnalysisRunRepository(settings);
  }
}
