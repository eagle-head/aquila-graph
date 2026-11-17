package org.aquilagraph.frontend;

import java.util.Map;

/** Service-provider boundary for an optional telemetry backend owned by the executable. */
@FunctionalInterface
public interface AnalysisObserverFactory {
  /**
   * Opens a host-owned observer using explicit configuration.
   *
   * @param configuration
   *          host environment values
   * @return observer whose resources must be closed by the host
   */
  AnalysisObserver open(Map<String, String> configuration);
}
