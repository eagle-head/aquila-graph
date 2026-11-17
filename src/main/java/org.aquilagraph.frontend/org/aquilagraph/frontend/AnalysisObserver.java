package org.aquilagraph.frontend;

import java.util.Map;

/** Host-owned observation of analysis stages, independent of AST identity and admission. */
@FunctionalInterface
public interface AnalysisObserver extends AutoCloseable {
  /**
   * Starts a stage on the calling thread. The returned scope belongs to that thread.
   *
   * @param stage
   *          stable stage name
   * @param attributes
   *          bounded contextual fields
   * @return a scope that must be closed, including when analysis fails
   */
  Observation start(String stage, Map<String, String> attributes);

  /** Closes the host-owned exporter lifecycle without closing analysis producers. */
  @Override
  default void close() {
  }

  /**
   * Returns an observer that performs no collection or export.
   *
   * @return shared stateless observer
   */
  static AnalysisObserver none() {
    return NoAnalysisObserver.INSTANCE;
  }

  /** A thread-bound stage observation; completion is explicit and close releases its context. */
  interface Observation extends AutoCloseable {
    /**
     * Returns the execution correlation identifier.
     * 
     * @return execution identifier, or an empty string when unavailable
     */
    String executionId();

    /**
     * Returns the trace correlation identifier.
     * 
     * @return trace identifier, or an empty string when unavailable
     */
    String traceId();

    /**
     * Returns the span correlation identifier.
     * 
     * @return span identifier, or an empty string when unavailable
     */
    String spanId();

    /**
     * Records successful completion, independently of whether the AST is complete.
     *
     * @param attributes
     *          outcome and quantity fields
     */
    void complete(Map<String, String> attributes);

    /**
     * Records a structured diagnostic without changing the analysis result.
     *
     * @param name
     *          stable event name
     * @param attributes
     *          diagnostic fields
     */
    void event(String name, Map<String, String> attributes);

    /** Ends the stage; a scope without completion represents an unsuccessful operation. */
    @Override
    void close();
  }
}
