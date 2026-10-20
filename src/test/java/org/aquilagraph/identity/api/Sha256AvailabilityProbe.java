package org.aquilagraph.identity.api;

import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.function.Supplier;

/** Exercises identity digest failures with the providers removed only inside its child JVM. */
public final class Sha256AvailabilityProbe {
  private Sha256AvailabilityProbe() {
  }

  /**
   * Verifies all identity digest entry points preserve the missing-algorithm failure.
   *
   * @param arguments unused process arguments
   */
  public static void main(String[] arguments) {
    var providers = Security.getProviders();
    try {
      for (var provider : providers) {
        Security.removeProvider(provider.getName());
      }
      requireUnavailable(() -> StableId.sha256("source", "Sample.java"));
      requireUnavailable(() -> SourceFile.fromContent(Path.of("Sample.java"), "class Sample {}"));
      requireUnavailable(() -> new CanonicalValueWriter("values/1").digest("content"));
    } finally {
      for (var index = 0; index < providers.length; index++) {
        if (Security.insertProviderAt(providers[index], index + 1) != index + 1) {
          throw new AssertionError("The original digest provider order must be restored");
        }
      }
    }
    System.out.println("sha256-unavailable-ok");
  }

  private static void requireUnavailable(Supplier<?> operation) {
    try {
      operation.get();
    } catch (IllegalStateException failure) {
      if (!(failure.getCause() instanceof NoSuchAlgorithmException)) {
        throw new AssertionError("Identity digest failure must preserve its missing-algorithm cause", failure);
      }
      if (!"SHA-256 is required by the Java platform".equals(failure.getMessage())) {
        throw new AssertionError("Identity digest failure must retain its platform prerequisite diagnostic", failure);
      }
      return;
    }
    throw new AssertionError("Identity digest creation must fail without a registered SHA-256 provider");
  }
}
