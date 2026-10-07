package org.aquilagraph.identity.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Full canonical captured values, excluding only root and separate dependency-location provenance. */
public final class CapturedValueEncoding {
  /** Version of the canonical captured-value field encoding. */
  public static final String FORMAT_VERSION = "captured-values/1";

  private CapturedValueEncoding() {
  }

  /**
   * Encodes captured components with explicit absence and meaningful sequence positions.
   *
   * @param input
   *          captured input, when established
   * @param environment
   *          captured environment, when established
   * @return independent canonical byte array
   */
  public static byte[] encode(Optional<InputSnapshot> input, Optional<AnalysisEnvironmentSnapshot> environment) {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(environment, "environment");
    var writer = new CanonicalValueWriter(FORMAT_VERSION);
    writer.text("input");
    writer.flag(input.isPresent());
    input.ifPresent(snapshot -> writeInput(writer, snapshot));
    writer.text("environment");
    writer.flag(environment.isPresent());
    environment.ifPresent(snapshot -> writeEnvironment(writer, snapshot));
    writer.text("software");
    writer.flag(input.isPresent() && environment.isPresent());
    if (input.isPresent() && environment.isPresent()) {
      var version = new SoftwareVersion(input.orElseThrow().snapshotDigest(), environment.orElseThrow().environmentDigest());
      writer.text(version.signatureAlgorithm());
      writer.text(version.sourceSignature());
      writer.text(version.environmentSignature());
      writer.text(version.signature());
    }
    return writer.toByteArray();
  }

  private static void writeInput(CanonicalValueWriter writer, InputSnapshot snapshot) {
    writer.text(snapshot.digestAlgorithm());
    writer.text(snapshot.snapshotDigest());
    writer.count(snapshot.sourceFiles().size());
    for (var source : snapshot.sourceFiles()) {
      writer.text(source.sourceFileKey());
      writer.text(source.canonicalRelativePath());
      writer.text(source.contentDigest());
      writer.text(source.content());
    }
  }

  private static void writeEnvironment(CanonicalValueWriter writer, AnalysisEnvironmentSnapshot environment) {
    var configuration = environment.compilerConfiguration();
    writer.text(environment.digestAlgorithm());
    writer.text(environment.environmentDigest());
    writer.integer(configuration.release().feature());
    writePaths(writer, configuration.classPath());
    writePaths(writer, configuration.modulePath());
    writeStrings(writer, configuration.options());
    writer.text(configuration.canonicalForm());
    writer.text(environment.compilerImplementation());
    writer.text(environment.compilerVendor());
    writer.text(environment.compilerVersion());
    writeStrings(writer, environment.analysisOptions());
    writer.text("class-path");
    writeDependencies(writer, environment.classPath());
    writer.text("module-path");
    writeDependencies(writer, environment.modulePath());
  }

  private static void writeDependencies(CanonicalValueWriter writer, List<DependencySnapshot> dependencies) {
    writer.count(dependencies.size());
    for (var dependency : dependencies) {
      writer.text(switch (dependency.kind()) {
        case MISSING -> "missing";
        case REGULAR_FILE -> "file";
        case DIRECTORY -> "directory";
      });
      writer.longInteger(dependency.byteCount());
      writer.integer(dependency.entryCount());
      writer.text(dependency.fingerprint());
    }
  }

  private static void writePaths(CanonicalValueWriter writer, List<Path> paths) {
    writer.count(paths.size());
    paths.forEach(path -> writer.text(path.toString()));
  }

  private static void writeStrings(CanonicalValueWriter writer, List<String> values) {
    writer.count(values.size());
    values.forEach(writer::text);
  }
}
