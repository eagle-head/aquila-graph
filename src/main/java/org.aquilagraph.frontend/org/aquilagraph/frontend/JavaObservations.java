package org.aquilagraph.frontend;

import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.ServiceConfigurationError;

/** Explicit host composition of an optional observation backend. */
public final class JavaObservations {
  private JavaObservations() {
  }

  /**
   * Opens the installed provider when AQUILA_OBSERVABILITY_ENABLED is true. Provider absence or initialization failure is reported without
   * preventing analysis.
   *
   * @param configuration
   *          executable environment
   * @return host-owned observer, or a no-op observer when collection is unavailable
   */
  public static AnalysisObserver open(Map<String, String> configuration) {
    if (!Boolean.parseBoolean(configuration.getOrDefault("AQUILA_OBSERVABILITY_ENABLED", "false"))) {
      return AnalysisObserver.none();
    }
    try {
      var provider = ServiceLoader.load(AnalysisObserverFactory.class).findFirst();
      if (provider.isPresent()) {
        return provider.orElseThrow().open(Map.copyOf(configuration));
      }
      System.getLogger(JavaObservations.class.getName()).log(Level.WARNING, "Aquila observation provider is not installed");
    } catch (IllegalArgumentException | IllegalStateException | ServiceConfigurationError failure) {
      System.getLogger(JavaObservations.class.getName()).log(Level.WARNING, "Aquila observation initialization failed", failure);
    }
    return AnalysisObserver.none();
  }
}
