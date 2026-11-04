package org.aquilagraph.repository.neo4j;

import java.util.function.Supplier;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
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
   * Creates lazy acquisition for completed analysis runs.
   *
   * @param settings
   *          deferred connection settings
   * @return contextual factory for caller-owned storage
   */
  public static AnalysisRunRepositoryFactory completedRuns(Supplier<Neo4jConnectionSettings> settings) {
    return Neo4jAnalysisRunRepository.factory(settings);
  }
}
