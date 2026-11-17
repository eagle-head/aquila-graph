package org.aquilagraph.library;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * Product-owned observation defaults and optional overrides, independent of SDK types.
 *
 * @param enabled
 *          whether to collect and export observations
 * @param endpoint
 *          base OTLP HTTP endpoint, without a signal suffix
 * @param serviceName
 *          service identity
 * @param serviceVersion
 *          artifact version supplied to telemetry
 */
public record ObservabilityConfiguration(boolean enabled, URI endpoint, String serviceName, String serviceVersion) {
  /** Requires an absolute HTTP endpoint and nonblank identities. */
  public ObservabilityConfiguration {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(serviceName, "serviceName");
    Objects.requireNonNull(serviceVersion, "serviceVersion");
    if (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme())) || endpoint.getHost() == null
        || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null || endpoint.getUserInfo() != null) {
      throw new IllegalArgumentException("Observation endpoint must be an absolute HTTP URL without credentials, query, or fragment");
    }
    if (serviceName.isBlank() || serviceVersion.isBlank()) {
      throw new IllegalArgumentException("Observation service name and version must not be blank");
    }
  }

  /**
   * Returns enabled observation using the Aquila local infrastructure defaults.
   * 
   * @return immutable product defaults
   */
  public static ObservabilityConfiguration defaults() {
    return fromEnvironment(Map.of());
  }

  /**
   * Returns an explicitly unobserved configuration for offline or isolated execution.
   * 
   * @return configuration with no SDK or exporter activity
   */
  public static ObservabilityConfiguration disabled() {
    var defaults = defaults();
    return new ObservabilityConfiguration(false, defaults.endpoint(), defaults.serviceName(), defaults.serviceVersion());
  }

  /**
   * Overlays supported environment settings on the product defaults.
   * 
   * @param environment
   *          settings captured by the caller
   * @return validated configuration
   * @throws IllegalArgumentException
   *           if a supplied setting is invalid
   */
  public static ObservabilityConfiguration fromEnvironment(Map<String, String> environment) {
    var enabled = environment.getOrDefault("AQUILA_OBSERVABILITY_ENABLED", "true");
    if (!"true".equalsIgnoreCase(enabled) && !"false".equalsIgnoreCase(enabled)) {
      throw new IllegalArgumentException("AQUILA_OBSERVABILITY_ENABLED must be true or false");
    }
    var version = AquilaGraph.class.getPackage().getImplementationVersion();
    return new ObservabilityConfiguration(Boolean.parseBoolean(enabled),
        URI.create(environment.getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://127.0.0.1:4318")),
        environment.getOrDefault("OTEL_SERVICE_NAME", "aquila-graph"),
        environment.getOrDefault("AQUILA_VERSION", version == null ? "development" : version));
  }

  Map<String, String> settings() {
    return Map.of("AQUILA_OBSERVABILITY_ENABLED", Boolean.toString(enabled), "OTEL_EXPORTER_OTLP_ENDPOINT", endpoint.toString(),
        "OTEL_SERVICE_NAME", serviceName, "AQUILA_VERSION", serviceVersion);
  }
}
