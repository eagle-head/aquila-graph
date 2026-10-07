package org.aquilagraph.identity.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;

/** Exercises actual identity values in a Java-base-only process without JSpecify on its class path. */
public final class IdentityRuntimeProbe {
  private IdentityRuntimeProbe() {
  }

  /**
   * Runs the isolated domain observation.
   *
   * @param arguments unused process arguments
   */
  public static void main(String[] arguments) {
    var source = SourceFile.fromContent(Path.of("A.java"), "class A {}");
    var input = InputSnapshot.fromSources(Path.of("source-root"), List.of(source));
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var environment = new AnalysisEnvironmentSnapshot(configuration, "copied-compiler", "vendor", "version", List.of(), List.of(), List.of());
    var software = SoftwareVersion.from(input, environment);
    var writer = new CanonicalValueWriter("probe/1");
    var capturedBytes = CapturedValueEncoding.encode(Optional.of(input), Optional.of(environment));
    writer.bytes(capturedBytes);
    var reader = new CanonicalValueReader(writer.toByteArray(), "probe/1");
    if (!Arrays.equals(capturedBytes, reader.bytes())) {
      throw new IllegalStateException("The copied captured-value frame changed during decoding");
    }
    reader.requireFinished();
    var identity = AstIdentity.fromContent(software.signature(), "source/1", "profile/1", "production/1", writer.digest("content"));
    var body = new BodyRef(identity, "method", Optional.of("body"), Optional.empty());
    if (!body.astIdentity().equals(identity) || input.sourceFileCount() != 1 || !source.content().equals("class A {}")) {
      throw new IllegalStateException("Identity values did not retain their supplied domain content");
    }
    System.out.println("identity-runtime-ok");
  }
}
