package org.aquilagraph.application;

import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.application.port.AstResultRepositoryFactory;
import org.aquilagraph.cfg.api.CfgDeriver;
import org.aquilagraph.source.api.AstProducer;

/** Composes the public analysis capabilities from their required collaborators. */
public enum AnalysisCapabilities {
  ;

  /**
   * Composes computation without storage.
   *
   * @param producer
   *          source production port
   * @param deriver
   *          CFG derivation port
   * @return independent analysis computation
   */
  public static AnalysisComputation computation(AstProducer producer, CfgDeriver deriver) {
    return new SourceComputation(producer, deriver);
  }

  /**
   * Composes caller-bound derivation of established source.
   *
   * @param deriver
   *          CFG derivation port
   * @return independent existing-source derivation
   */
  public static ExistingSourceDerivation existingSourceDerivation(CfgDeriver deriver) {
    return new EstablishedSourceDerivation(deriver);
  }

  /**
   * Composes independent AST retention without acquiring a repository.
   *
   * @param repositories
   *          lazy AST repository factory
   * @return independent AST retention
   */
  public static AstRetention astRetention(AstResultRepositoryFactory repositories) {
    return new StandaloneAstRetention(repositories);
  }

  /**
   * Composes exact retained AST lookup without acquiring a repository.
   *
   * @param repositories
   *          lazy AST repository factory
   * @return independent AST query
   */
  public static AstQuery astQuery(AstResultRepositoryFactory repositories) {
    return new RetainedAstQuery(repositories);
  }

  /**
   * Composes computation and publication without acquiring repositories.
   *
   * @param computation
   *          independent computation capability
   * @param astRepositories
   *          lazy AST repository factory
   * @param runRepositories
   *          lazy completed-run repository factory
   * @return coordinated analysis use case
   */
  public static AnalysisUseCase execution(AnalysisComputation computation, AstResultRepositoryFactory astRepositories,
      AnalysisRunRepositoryFactory runRepositories) {
    return new AnalysisExecutionCoordinator(computation, new PublicationCoordinator(astRepositories, runRepositories));
  }
}
