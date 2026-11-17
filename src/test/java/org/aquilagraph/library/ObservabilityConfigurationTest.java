package org.aquilagraph.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObservabilityConfigurationTest {
  @Test
  void givenNoOverrides_whenLoadingDefaults_thenProductObservabilityIsEnabledAndCanBeExplicitlyDisabled() {
    // Arrange
    var environment = Map.<String, String>of();
    // Act
    var defaults = ObservabilityConfiguration.fromEnvironment(environment);
    var disabled = ObservabilityConfiguration.disabled();
    // Assert
    assertTrue(defaults.enabled());
    assertEquals(URI.create("http://127.0.0.1:4318"), defaults.endpoint());
    assertEquals("aquila-graph", defaults.serviceName());
    assertFalse(defaults.serviceVersion().isBlank());
    assertFalse(disabled.enabled());
    assertEquals(defaults.endpoint(), disabled.endpoint());
  }

  @Test
  void givenExplicitEnvironment_whenLoadingConfiguration_thenEverySupportedOverrideIsRetained() {
    // Arrange
    var environment = Map.of("AQUILA_OBSERVABILITY_ENABLED", "false", "OTEL_EXPORTER_OTLP_ENDPOINT", "https://collector.example/otlp",
        "OTEL_SERVICE_NAME", "source-analysis", "AQUILA_VERSION", "1.0");
    // Act
    var actual = ObservabilityConfiguration.fromEnvironment(environment);
    // Assert
    assertEquals(new ObservabilityConfiguration(false, URI.create("https://collector.example/otlp"), "source-analysis", "1.0"), actual);
  }

  @Test
  void givenInvalidObservationSettings_whenLoadingConfiguration_thenErrorsAreReportedBeforeOpeningASession() {
    // Arrange
    var invalid = List.of(Map.of("AQUILA_OBSERVABILITY_ENABLED", "yes"), Map.of("OTEL_SERVICE_NAME", " "), Map.of("AQUILA_VERSION", ""),
        Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "relative/path"), Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "ftp://localhost"),
        Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://user:password@localhost"), Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost?query"),
        Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost#fragment"));
    // Act
    for (var environment : invalid) {
      // Assert
      assertThrows(IllegalArgumentException.class, () -> ObservabilityConfiguration.fromEnvironment(environment));
    }
  }
}
