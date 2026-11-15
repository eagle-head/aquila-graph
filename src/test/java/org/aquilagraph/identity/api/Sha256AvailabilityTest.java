package org.aquilagraph.identity.api;

import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Temporarily changes the JVM security provider registry")
class Sha256AvailabilityTest {
  @Test
  void givenNoDigestProviders_whenIdentityDigestsAreRequested_thenEveryEntryPointPreservesTheUnavailableAlgorithmCause() {
    // Arrange
    var operations = List.<Supplier<?>>of(() -> StableId.sha256("source", "Sample.java"),
        () -> SourceFile.fromContent(capturedPath(Path.of("Sample.java")), "class Sample {}"),
        () -> new CanonicalValueWriter("values/1").digest("content"));

    // Act
    var failures = withoutDigestProviders(() -> operations.stream()
        .map(operation -> assertThrows(IllegalStateException.class, operation::get)).toList());

    // Assert
    failures.forEach(failure -> {
      assertInstanceOf(NoSuchAlgorithmException.class, failure.getCause());
      assertEquals("SHA-256 is required by the Java platform", failure.getMessage());
    });
  }

  @Test
  void givenNoDigestProvidersAndBlankQualifiedKeys_whenIdentitiesAreConstructed_thenInvalidArgumentsRemainArgumentErrors() {
    // Arrange
    var ast = AstIdentity.fromContent("software", "source/1", "profile/1", "production/1", "content");
    var operations = List.<Supplier<?>>of(
        () -> new AstIdentity("", "software", "source/1", "profile/1", "production/1", "content"),
        () -> new AstIdentity(" \t\n", "software", "source/1", "profile/1", "production/1", "content"),
        () -> new GraphIdentity("", ast, "cfg/1", "graph/1", "method", "graph-content"),
        () -> new GraphIdentity(" \t\n", ast, "cfg/1", "graph/1", "method", "graph-content"));

    // Act
    var failures = withoutDigestProviders(() -> operations.stream()
        .map(operation -> assertThrows(IllegalArgumentException.class, operation::get)).toList());

    // Assert
    assertEquals(4, failures.size());
  }

  private static <T> T withoutDigestProviders(Supplier<T> operation) {
    var providers = Security.getProviders();
    try {
      for (var provider : providers) {
        Security.removeProvider(provider.getName());
      }

      return operation.get();
    } finally {
      for (var index = 0; index < providers.length; index++) {
        Security.insertProviderAt(providers[index], index + 1);
      }
      assertArrayEquals(providers, Security.getProviders(), "The original digest providers and their registration order must be restored");
    }
  }
}
