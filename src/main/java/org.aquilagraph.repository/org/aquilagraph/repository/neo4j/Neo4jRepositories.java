package org.aquilagraph.repository.neo4j;

import java.util.function.Supplier;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AstQueryRepositoryFactory;
import org.aquilagraph.application.port.AstRetentionRepositoryFactory;
import org.aquilagraph.repository.internal.neo4j.Neo4jAnalysisRunRepository;
import org.aquilagraph.repository.internal.neo4j.Neo4jAstResultRepositoryFactory;

/** Independent composition of the actual Neo4j repository ports. */
public final class Neo4jRepositories {
  private Neo4jRepositories() {
  }

  /**
   * Creates lazy acquisition for complete AST retention.
   *
   * @param settings
   *          deferred connection settings
   * @return save-scoped repository factory
   */
  public static AstRetentionRepositoryFactory astRetention(Supplier<Neo4jConnectionSettings> settings) {
    return new Neo4jAstResultRepositoryFactory(settings)::openRetention;
  }

  /**
   * Creates lazy acquisition for exact retained AST lookup.
   *
   * @param settings
   *          deferred connection settings
   * @return query-scoped repository factory
   */
  public static AstQueryRepositoryFactory astQuery(Supplier<Neo4jConnectionSettings> settings) {
    return new Neo4jAstResultRepositoryFactory(settings)::openQuery;
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
