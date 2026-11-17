package org.aquilagraph.library;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputation;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.application.port.AstRetentionRepositoryFactory;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.frontend.AnalysisObserver;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.frontend.JavaObservations;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProducer;
import org.aquilagraph.source.api.AstProductionRequest;

/**
 * Configured library session owning analysis composition and observation lifetime. Operations on one session are serialized. Close waits
 * for an active operation and flushes its observations; use separate sessions for concurrent analyses. No process exit, command output,
 * global SDK registration, or container startup is performed by this class.
 */
public final class AquilaGraph implements AnalysisUseCase, AutoCloseable {
  private final ReentrantLock lifecycle = new ReentrantLock();
  private final AnalysisObserver observations;
  private final AstProducer producer;
  private final AnalysisComputation computation;
  private final AnalysisUseCase execution;
  private boolean closed;

  private AquilaGraph(ObservabilityConfiguration configuration, AstRetentionRepositoryFactory astRepositories,
      AnalysisRunRepositoryFactory runRepositories) {
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(astRepositories, "astRepositories");
    Objects.requireNonNull(runRepositories, "runRepositories");
    observations = JavaObservations.open(configuration.settings());
    producer = JavaFrontends.observed(observations);
    computation = AnalysisCapabilities.computation(producer, CfgDerivers.standard());
    execution = AnalysisCapabilities.execution(computation, astRepositories, runRepositories);
  }

  /**
   * Opens a complete session using product defaults and supported environment overrides.
   * 
   * @return owned session; close it after the last analysis
   */
  public static AquilaGraph open() {
    var environment = Map.copyOf(System.getenv());
    return configured(ObservabilityConfiguration.fromEnvironment(environment), environment);
  }

  /**
   * Opens a session with an explicit observation configuration and lazy environment-based storage.
   * 
   * @param configuration
   *          optional overrides expressed without SDK dependencies
   * @return owned session
   */
  public static AquilaGraph open(ObservabilityConfiguration configuration) {
    return configured(configuration, Map.copyOf(System.getenv()));
  }

  /**
   * Opens a session with caller-selected storage ports; repositories are acquired only for publication.
   * 
   * @param configuration
   *          observation configuration
   * @param astRepositories
   *          lazy AST retention factory
   * @param runRepositories
   *          lazy completed-run repository factory
   * @return owned session
   */
  public static AquilaGraph open(ObservabilityConfiguration configuration, AstRetentionRepositoryFactory astRepositories,
      AnalysisRunRepositoryFactory runRepositories) {
    return new AquilaGraph(configuration, astRepositories, runRepositories);
  }

  private static AquilaGraph configured(ObservabilityConfiguration configuration, Map<String, String> environment) {
    return open(configuration, Neo4jRepositories.astRetention(() -> Neo4jConnectionSettings.fromEnvironment(environment)),
        Neo4jRepositories.completedRuns(() -> Neo4jConnectionSettings.fromEnvironment(environment)));
  }

  /**
   * Produces the requested AST without CFG derivation or publication.
   * 
   * @param request
   *          exact source, compiler, and profile request
   * @return typed AST outcome, including incomplete or rejected production
   * @throws IllegalStateException
   *           if this session has been closed
   */
  public AstOutcome produceAst(AstProductionRequest request) {
    return use(() -> producer.produce(request));
  }

  /**
   * Computes AST and the requested optional control flow without publication.
   * 
   * @param request
   *          computation request
   * @return qualified analysis result
   * @throws IllegalStateException
   *           if this session has been closed
   */
  public AnalysisRun analyze(AnalysisComputationRequest request) {
    return use(() -> computation.analyze(request));
  }

  /**
   * Executes computation and the explicit publication intent.
   * 
   * @param request
   *          complete analysis and publication request
   * @return computation and publication outcomes
   * @throws IllegalStateException
   *           if this session has been closed
   */
  @Override
  public AnalysisExecution execute(AnalysisRequest request) {
    return use(() -> execution.execute(request));
  }

  private <T> T use(Supplier<T> operation) {
    lifecycle.lock();
    try {
      if (closed) {
        throw new IllegalStateException("The Aquila Graph session is closed");
      }
      return operation.get();
    } finally {
      lifecycle.unlock();
    }
  }

  /** Waits for active work, then flushes and closes observations once. */
  @Override
  public void close() {
    lifecycle.lock();
    try {
      if (!closed) {
        closed = true;
        observations.close();
      }
    } finally {
      lifecycle.unlock();
    }
  }
}
